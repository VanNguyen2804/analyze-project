package com.example.analyzeproject.infrastructure.sync;
import com.example.analyzeproject.domain.model.FeedDraw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.util.*;

/** Reads the public NDJSON feed. POWER's seventh value is special, never sorted into the six main balls. */
@Component
public class LotteryFeedClient {
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int MAX_BYTES = 5_000_000;
    private final ObjectMapper mapper;
    private final LotterySyncProperties properties;
    private final HttpClient client;

    public LotteryFeedClient(ObjectMapper mapper, LotterySyncProperties properties) {
        this.mapper = mapper;
        this.properties = properties;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds())).build();
    }

    public record FetchResult(List<FeedDraw> draws, String sourceUrl, String primaryError) {}

    public List<FeedDraw> fetch(String category, LocalDate from, ZonedDateTime now) throws IOException, InterruptedException {
        return fetchWithSource(category, from, now).draws();
    }

    public FetchResult fetchWithSource(String category, LocalDate from, ZonedDateTime now) throws IOException, InterruptedException {
        URI uri = URI.create(properties.sourceUrl(category));
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Feed URL must use HTTPS without credentials");
        }
        if ("vietlott.vn".equals(uri.getHost())) {
            try {
                return new FetchResult(fetchOfficial(uri, category, from, now), uri.toString(), null);
            } catch (IOException primary) {
                String fallback = properties.getPowerFallbackUrl();
                if (!"POWER".equals(category) || fallback == null || fallback.isBlank()) throw primary;
                URI backup = URI.create(fallback);
                if (!"https".equals(backup.getScheme()) || backup.getHost() == null || backup.getUserInfo() != null)
                    throw new IOException("Fallback URL must use HTTPS without credentials", primary);
                try {
                    return new FetchResult(parse(download(backup), category, from, now), fallback, primary.getMessage());
                } catch (IOException failure) { failure.addSuppressed(primary); throw failure; }
            }
        }
        return new FetchResult(parse(download(uri), category, from, now), uri.toString(), null);
    }

    String download(URI uri) throws IOException, InterruptedException {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Accept", "application/x-ndjson, text/plain")
                .header("User-Agent", "analyze-project-result-sync/1.0").GET().build();
        // A bounded body subscriber keeps both the whole-request timeout and a maximum response size.
        var response = client.send(request, info -> new LimitedBodySubscriber(MAX_BYTES));
        if (response.statusCode() != 200) throw new IOException("Feed returned HTTP " + response.statusCode());
        return response.body();
    }

    private List<FeedDraw> fetchOfficial(URI uri, String category, LocalDate from, ZonedDateTime now)
            throws IOException, InterruptedException {
        var draws = new TreeMap<LocalDate, FeedDraw>();
        String base = uri.toString().split("\\?", 2)[0];
        var current = parseOfficial(download(URI.create(base + "?nocatche=1")), category, LocalDate.MIN, now.plusDays(1)).get(0);
        for (int count = 0; count < 4000; count++) {
            if (current.date().isBefore(from)) return completed(draws);
            var local = now.withZoneSameInstant(ZONE);
            if (current.date().isBefore(local.toLocalDate()) ||
                    (current.date().equals(local.toLocalDate()) && local.toLocalTime().getHour() >= 19))
                draws.put(current.date(), current);
            if (current.date().equals(from) || Long.parseLong(current.drawId()) <= 1) return completed(draws);
            String previousId = String.format(Locale.ROOT, "%05d", Long.parseLong(current.drawId()) - 1);
            Thread.sleep(250);
            FeedDraw previous;
            try {
                previous = parseOfficial(download(URI.create(base + "?id=" + previousId + "&nocatche=1")),
                    category, LocalDate.MIN, now.plusDays(1)).get(0);
            } catch (IOException ex) {
                if (draws.isEmpty()) throw ex;
                org.slf4j.LoggerFactory.getLogger(LotteryFeedClient.class).warn(
                        "Official history stopped at draw {}; importing completed newer draws: {}", previousId, ex.getMessage());
                return completed(draws);
            }
            if (!previous.drawId().equals(previousId) || !previous.date().isBefore(current.date()))
                throw new IOException("Official history returned unexpected draw: " + previousId);
            current = previous;
        }
        throw new IOException("Official history exceeded traversal limit");
    }

    private List<FeedDraw> completed(TreeMap<LocalDate, FeedDraw> draws) throws IOException {
        if (draws.isEmpty()) throw new IOException("No completed official draws in requested window");
        return List.copyOf(draws.values());
    }

    public List<FeedDraw> parseOfficial(String html, String category, LocalDate from, ZonedDateTime now) throws IOException {
        var header = java.util.regex.Pattern.compile("<h5[^>]*>.*?<b>\\s*#([0-9]+)</b>.*?<b>([0-9]{2}/[0-9]{2}/[0-9]{4})</b>", java.util.regex.Pattern.DOTALL).matcher(html);
        if (!header.find()) throw new IOException("Official draw header missing");
        String date;
        try { date = LocalDate.parse(header.group(2), java.time.format.DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(java.time.format.ResolverStyle.STRICT)).toString(); }
        catch (RuntimeException ex) { throw new IOException("Invalid official date", ex); }
        int start = html.indexOf("day_so_ket_qua_v2", header.end());
        int end = html.indexOf("</div>", start);
        if (start < 0 || end < 0) throw new IOException("Official result missing");
        var balls = java.util.regex.Pattern.compile("<span[^>]*class=[\"'][^\"']*bong_tron[^\"']*[\"'][^>]*>\\s*([0-9]{1,2})\\s*</span>").matcher(html.substring(start, end));
        var values = new ArrayList<Integer>();
        while (balls.find()) values.add(Integer.parseInt(balls.group(1)));
        return parse(mapper.writeValueAsString(Map.of("date", date, "id", header.group(1), "result", values)), category, from, now);
    }

    public List<FeedDraw> parse(String body, String category, LocalDate from, ZonedDateTime now) throws IOException {
        if (!Set.of("MEGA", "POWER").contains(category)) throw new IllegalArgumentException("Unknown category");
        if (body.isBlank() || body.length() > MAX_BYTES) throw new IOException("Empty or oversized feed");
        var result = new TreeMap<LocalDate, FeedDraw>();
        var localNow = now.withZoneSameInstant(ZONE);
        for (String line : body.split("\\R")) {
            if (line.isBlank()) continue;
            JsonNode row = mapper.readTree(line);
            LocalDate date;
            try { date = LocalDate.parse(row.path("date").asText()); }
            catch (RuntimeException ex) { throw new IOException("Invalid draw date", ex); }
            if (date.isBefore(from) || date.isAfter(localNow.toLocalDate())
                    || (date.equals(localNow.toLocalDate()) && localNow.toLocalTime().isBefore(LocalTime.of(19, 0)))) continue;
            String id = row.path("id").asText();
            JsonNode balls = row.path("result");
            boolean power = "POWER".equals(category);
            if (!id.matches("[0-9]{1,12}") || !balls.isArray() || balls.size() != (power ? 7 : 6)) {
                throw new IOException("Invalid result shape for " + category + " " + date);
            }
            var values = new ArrayList<Integer>();
            for (JsonNode ball : balls) {
                if (!ball.isIntegralNumber() || !ball.canConvertToInt() || ball.asInt() < 1 || ball.asInt() > (power ? 55 : 45)) {
                    throw new IOException("Invalid ball for " + category + " " + date);
                }
                values.add(ball.asInt());
            }
            if (new HashSet<>(values).size() != values.size()) throw new IOException("Duplicate balls on " + date);
            var numbers = values.subList(0, 6).stream().sorted().toList();
            FeedDraw draw = new FeedDraw(category, date, id, numbers, power ? values.get(6) : null);
            FeedDraw previous = result.putIfAbsent(date, draw);
            if (previous != null && !previous.equals(draw)) throw new IOException("Conflicting source rows on " + date);
        }
        if (result.isEmpty()) throw new IOException("Feed has no completed draws in the requested window");
        return List.copyOf(result.values());
    }

    public static LocalDate latestExpected(String category, ZonedDateTime now) {
        var local = now.withZoneSameInstant(ZONE);
        LocalDate date = local.toLocalTime().isBefore(LocalTime.of(19, 0)) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
        Set<DayOfWeek> days = "POWER".equals(category)
                ? Set.of(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY, DayOfWeek.SATURDAY)
                : Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY);
        while (!days.contains(date.getDayOfWeek())) date = date.minusDays(1);
        return date;
    }

    private static class LimitedBodySubscriber implements HttpResponse.BodySubscriber<String> {
        private final java.util.concurrent.CompletableFuture<String> result = new java.util.concurrent.CompletableFuture<>();
        private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        private final int limit;
        private java.util.concurrent.Flow.Subscription subscription;
        LimitedBodySubscriber(int limit) { this.limit = limit; }
        public java.util.concurrent.CompletionStage<String> getBody() { return result; }
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) { this.subscription = subscription; subscription.request(1); }
        public void onNext(List<java.nio.ByteBuffer> chunks) {
            for (var chunk : chunks) {
                if (bytes.size() + chunk.remaining() > limit) {
                    subscription.cancel(); result.completeExceptionally(new IOException("Feed exceeds size limit")); return;
                }
                byte[] data = new byte[chunk.remaining()]; chunk.get(data); bytes.writeBytes(data);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(bytes.toString(java.nio.charset.StandardCharsets.UTF_8)); }
    }
}
