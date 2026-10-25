package com.example.analyzeproject.service;
import com.example.analyzeproject.model.LotteryNumber;
import java.util.*;
public final class TestDraws {
    public static LotteryNumber verified(LotteryNumber row) {
        if ("POWER".equals(row.getCategory()) && row.getSpecialNumber() == null) {
            for (int n = 1; n <= 55; n++) if (!row.getNumbers().contains(n)) { row.setSpecialNumber(n); break; }
        }
        row.setQuality(Map.of("status","SOURCE_MATCHED","fingerprint",DrawQuality.fingerprint(row),"sourceUrl","test:synthetic","audit",List.of()));
        return row;
    }
}
