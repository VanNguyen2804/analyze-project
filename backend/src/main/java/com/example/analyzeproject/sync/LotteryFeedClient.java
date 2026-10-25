package com.example.analyzeproject.sync;

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

    public record Draw(String category, LocalDate date, String drawId, List<Integer> numbers, Integer special) {}

    public List<Draw> fetch(String category, LocalDate from, ZonedDateTime now) throws IOException, InterruptedException {
        URI uri = URI.create(properties.sourceUrl(category));
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Feed URL must use HTTPS without credentials");
        }
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("Accept", "application/x-ndjson, text/plain")
                .header("User-Agent", "analyze-project-result-sync/1.0").GET().build();
        // A bounded body subscriber keeps both the whole-request timeout and a maximum response size.
        var response = client.send(request, info -> new LimitedBodySubscriber(MAX_BYTES));
        if (response.statusCode() != 200) throw new IOException("Feed returned HTTP " + response.statusCode());
        return parse(response.body(), category, from, now);
    }

    public List<Draw> parse(String body, String category, LocalDate from, ZonedDateTime now) throws IOException {
        if (!Set.of("MEGA", "POWER").contains(category)) throw new IllegalArgumentException("Unknown category");
        if (body.isBlank() || body.length() > MAX_BYTES) throw new IOException("Empty or oversized feed");
        var result = new TreeMap<LocalDate, Draw>();
        var localNow = now.withZoneSameInstant(ZONE);
        for (String line : body.split("\\R")) {
            if (line.isBlank()) continue;
            JsonNode row = mapper.readTree(line);
            LocalDate date;
            try { date = LocalDate.parse(row.path("date").asText()); }
            catch (RuntimeException ex) { throw new IOException("Invalid draw date", ex); }
            if (date.isBefore(from) || date.isAfter(localNow.toLocalDate())
                    || (date.equals(localNow.toLocalDate()) && localNow.toLocalTime().isBefore(LocalTime.of(18, 30)))) continue;
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
            Draw draw = new Draw(category, date, id, numbers, power ? values.get(6) : null);
            Draw previous = result.putIfAbsent(date, draw);
            if (previous != null && !previous.equals(draw)) throw new IOException("Conflicting source rows on " + date);
        }
        if (result.isEmpty()) throw new IOException("Feed has no completed draws in the requested window");
        return List.copyOf(result.values());
    }

    public static LocalDate latestExpected(String category, ZonedDateTime now) {
        var local = now.withZoneSameInstant(ZONE);
        LocalDate date = local.toLocalTime().isBefore(LocalTime.of(18, 30)) ? local.toLocalDate().minusDays(1) : local.toLocalDate();
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
