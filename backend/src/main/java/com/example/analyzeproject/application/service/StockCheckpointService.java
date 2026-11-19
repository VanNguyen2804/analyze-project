package com.example.analyzeproject.application.service;

import java.util.List;

/** Read access to persisted stock checkpoint reports. */
public interface StockCheckpointService {
    List<Object> reports(String symbol, String day);
}
