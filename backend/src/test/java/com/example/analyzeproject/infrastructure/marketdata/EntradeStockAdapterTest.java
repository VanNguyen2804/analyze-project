package com.example.analyzeproject.infrastructure.marketdata;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntradeStockAdapterTest {
    @Test void sameBusinessDateSnapshotsKeepTheLaterCandleWithoutAddingVolumes(){
        var early=new com.example.analyzeproject.domain.model.stock.StockModels.Candle(1672099200,26.45,26.5,26.15,26.45,317300,true);
        var later=new com.example.analyzeproject.domain.model.stock.StockModels.Candle(1672106400,26.45,27.4,26.05,26.7,1844000,true);
        var normalized=EntradeStockAdapter.normalizeDaily(java.util.List.of(later,early));
        assertEquals(1,normalized.size());assertEquals(later,normalized.get(0));assertEquals(1844000,normalized.get(0).volume());
    }
    @Test void quoteUnitIsIndependentFromTheBroaderVerificationFlagAndIndexIsNotScaled() {
        assertEquals("VND",EntradeStockAdapter.priceUnit("VIC",1000,false));
        assertEquals("THOUSAND_VND",EntradeStockAdapter.priceUnit("VIC",1,false));
        assertEquals("THOUSAND_VND",EntradeStockAdapter.priceUnit("FPT",1,true));
        assertEquals("POINTS",EntradeStockAdapter.priceUnit("VNINDEX",1000,false));
        assertEquals("SOURCE",EntradeStockAdapter.priceUnit("MBB",2,false));
    }
}
