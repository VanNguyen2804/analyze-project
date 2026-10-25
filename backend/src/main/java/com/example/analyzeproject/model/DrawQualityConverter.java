package com.example.analyzeproject.model;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;

@Converter
public class DrawQualityConverter implements AttributeConverter<Map<String,Object>, String> {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    public String convertToDatabaseColumn(Map<String,Object> value) {
        if (value == null) return null;
        try { return MAPPER.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Invalid quality audit", e); }
    }
    public Map<String,Object> convertToEntityAttribute(String value) {
        if (value == null) return null;
        try { return MAPPER.readValue(value, new TypeReference<Map<String,Object>>() {}); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid stored quality audit", e); }
    }
}
