package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.Feed;

/** Shared confirmation gate. Raw position costs must never be compared with adjusted prices. */
public final class StockDataQuality {
    private StockDataQuality() {}
    public static boolean canConfirm(Feed feed, String freshness) {
        return feed.verified() && "RAW".equals(feed.priceBasis()) && "RECENT".equals(freshness)
                && ("VND".equals(feed.unit()) || "THOUSAND_VND".equals(feed.unit()));
    }
}
