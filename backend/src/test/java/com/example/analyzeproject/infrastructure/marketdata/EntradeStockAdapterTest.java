package com.example.analyzeproject.infrastructure.marketdata;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EntradeStockAdapterTest {
    @Test void quoteUnitIsIndependentFromTheBroaderVerificationFlagAndIndexIsNotScaled() {
        assertEquals("VND",EntradeStockAdapter.priceUnit("VIC",1000,false));
        assertEquals("THOUSAND_VND",EntradeStockAdapter.priceUnit("VIC",1,false));
        assertEquals("THOUSAND_VND",EntradeStockAdapter.priceUnit("FPT",1,true));
        assertEquals("POINTS",EntradeStockAdapter.priceUnit("VNINDEX",1000,false));
        assertEquals("SOURCE",EntradeStockAdapter.priceUnit("MBB",2,false));
    }
}
