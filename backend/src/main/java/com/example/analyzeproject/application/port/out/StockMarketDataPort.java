package com.example.analyzeproject.application.port.out;

import com.example.analyzeproject.domain.model.stock.StockModels.*;

public interface StockMarketDataPort {
    Feed load(String symbol, Mode mode);
}
