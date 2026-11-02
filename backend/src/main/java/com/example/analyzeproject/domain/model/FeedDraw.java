package com.example.analyzeproject.domain.model;

import java.time.LocalDate;
import java.util.List;

public record FeedDraw(String category, LocalDate date, String drawId, List<Integer> numbers, Integer special) {}
