package com.example.analyzeproject.domain.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import java.util.*;
import java.util.stream.Collectors;

/** Pure draw eligibility, revision selection and fingerprints. */
public final class DrawQuality {
    private DrawQuality() {}
    public static String fingerprint(LotteryNumber row) {
        return row.getCategory() + "|" + row.getDrawDate() + "|" + (row.getNumbers() == null ? List.<Integer>of() : row.getNumbers()).stream().sorted(Comparator.nullsFirst(Comparator.naturalOrder())).map(String::valueOf).collect(Collectors.joining(","))
                + "|" + (row.getSpecialNumber() == null ? "" : row.getSpecialNumber());
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
    public static List<LotteryNumber> history(List<LotteryNumber> rows) {
        Set<String> seen = new HashSet<>();
        return rows.stream().filter(Objects::nonNull)
                .sorted(Comparator.comparing(LotteryNumber::getDrawDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LotteryNumber::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LotteryNumber::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(row -> seen.add(row.getCategory() + "|" + row.getDrawDate())).filter(DrawQuality::eligible).collect(Collectors.toList());
    }
}
