package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public final class DrawQuality {
    private DrawQuality() {}
    private static final List<Map<String,Object>> CORRECTIONS;
    static {
        try (var in = DrawQuality.class.getResourceAsStream("/data/reviewed_draw_corrections.json")) {
            CORRECTIONS = new ObjectMapper().readValue(in, new TypeReference<List<Map<String,Object>>>() {});
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    public static String fingerprint(LotteryNumber row) {
        return row.getCategory() + "|" + row.getDrawDate() + "|" + (row.getNumbers() == null ? List.<Integer>of() : row.getNumbers()).stream().sorted(Comparator.nullsFirst(Comparator.naturalOrder())).map(String::valueOf).collect(Collectors.joining(","))
                + "|" + (row.getSpecialNumber() == null ? "" : row.getSpecialNumber());
    }
    private static boolean sameNumbers(List<Integer> a, List<Integer> b) {
        return a != null && b != null && a.stream().sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList()
                .equals(b.stream().sorted(Comparator.nullsFirst(Comparator.naturalOrder())).toList());
    }
    public static boolean eligible(LotteryNumber row) {
        if (row == null || row.getDrawDate() == null || !Set.of("MEGA","POWER").contains(Objects.toString(row.getCategory(), ""))
                || row.getNumbers() == null || row.getNumbers().size() != 6 || new HashSet<>(row.getNumbers() == null ? List.of() : row.getNumbers()).size() != 6) return false;
        int max = "POWER".equals(row.getCategory()) ? 55 : 45;
        if (row.getNumbers().stream().anyMatch(n -> n == null || n < 1 || n > max)) return false;
        Integer sp = row.getSpecialNumber();
        if (max == 55 ? sp == null || sp < 1 || sp > 55 || row.getNumbers().contains(sp) : sp != null) return false;
        var q = row.getQuality();
        return q != null && "SOURCE_MATCHED".equals(q.get("status")) && fingerprint(row).equals(q.get("fingerprint"));
    }
    private static Map<String,Object> snapshot(LotteryNumber row) {
        var value = new LinkedHashMap<String,Object>();
        value.put("category",row.getCategory()); value.put("drawDate",row.getDrawDate().toString());
        value.put("numbers",row.getNumbers() == null ? null : new ArrayList<>(row.getNumbers())); value.put("specialNumber",row.getSpecialNumber()); value.put("note",row.getNote());
        return value;
    }
    public static List<LotteryNumber> history(List<LotteryNumber> rows) {
        Set<String> seen = new HashSet<>();
        return rows.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LotteryNumber::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LotteryNumber::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(row -> seen.add(row.getCategory() + "|" + row.getDrawDate())).filter(DrawQuality::eligible).collect(Collectors.toList());
    }
    @SuppressWarnings("unchecked")
    public static boolean reconcile(LotteryNumber row, LotteryNumber incoming, String source, String drawId, String override) {
        var before = snapshot(row);
        var oldQuality = row.getQuality();
        String status = "QUARANTINED", reason = override == null ? "SOURCE_NOT_FOUND" : override, evidence = null;
        if (incoming != null && override == null) {
            boolean exact = fingerprint(row).equals(fingerprint(incoming));
            boolean missingSpecial = "POWER".equals(row.getCategory()) && row.getSpecialNumber() == null
                    && sameNumbers(row.getNumbers(), incoming.getNumbers());
            Map<String,Object> correction = CORRECTIONS.stream().filter(c -> c.get("category").equals(row.getCategory())
                    && c.get("drawDate").equals(row.getDrawDate().toString())
                    && sameNumbers((List<Integer>)c.get("beforeNumbers"), row.getNumbers())
                    && Objects.equals(c.get("beforeSpecial"),row.getSpecialNumber())
                    && sameNumbers((List<Integer>)c.get("numbers"), incoming.getNumbers())
                    && Objects.equals(c.get("specialNumber"),incoming.getSpecialNumber())).findFirst().orElse(null);
            if (exact || missingSpecial || correction != null) {
                status = "SOURCE_MATCHED"; reason = exact ? "MATCHED_SOURCE" : missingSpecial ? "FILLED_MISSING_SPECIAL" : "REVIEWED_CORRECTION";
                if (correction != null) evidence = (String)correction.get("evidenceUrl");
                if (!exact) {
                    row.setNumbers(new ArrayList<>(incoming.getNumbers())); row.setSpecialNumber(incoming.getSpecialNumber());
                    row.setNote("Reconciled against " + source + "; original values and note retained in quality.audit");
                }
            } else reason = "SOURCE_CONFLICT";
        }
        String fingerprint = fingerprint(row);
        List<Map<String,Object>> events = oldQuality == null ? new ArrayList<>() : new ArrayList<>((List<Map<String,Object>>)oldQuality.getOrDefault("audit",List.of()));
        if (oldQuality != null && status.equals(oldQuality.get("status")) && fingerprint.equals(oldQuality.get("fingerprint"))
                && source.equals(oldQuality.get("sourceUrl")) && (status.equals("SOURCE_MATCHED") || !events.isEmpty() && reason.equals(events.get(events.size()-1).get("reason")))) return false;
        var event = new LinkedHashMap<String,Object>();
        event.put("at", Instant.now().toString()); event.put("reason",reason); event.put("sourceUrl",source); event.put("evidenceUrl",evidence);
        event.put("before",before); event.put("after",snapshot(row)); event.put("previousStatus",oldQuality == null ? "UNVERIFIED" : oldQuality.get("status"));
        events.add(event);
        var quality = new LinkedHashMap<String,Object>();
        quality.put("status",status); quality.put("fingerprint",fingerprint); quality.put("sourceUrl",source); quality.put("sourceDrawId",drawId); quality.put("audit",events);
        row.setQuality(quality);
        return true;
    }
}
