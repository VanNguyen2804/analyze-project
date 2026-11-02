package com.example.analyzeproject.infrastructure.persistence.jpa;

import com.example.analyzeproject.domain.model.*;
import com.example.analyzeproject.infrastructure.persistence.entity.*;
import java.util.*;

/** Copies values at the persistence boundary, including mutable collections and audit JSON. */
public final class PersistenceMapper {
    public LotteryNumber toDomain(LotteryNumberEntity value) {
        if (value == null) return null;
        var result = new LotteryNumber();
        result.setQuality(copyMap(value.getQuality()));
        result.setId(value.getId());
        result.setDrawDate(value.getDrawDate());
        result.setCategory(value.getCategory());
        result.setNumbers(value.getNumbers() == null ? null : new ArrayList<>(value.getNumbers()));
        result.setSpecialNumber(value.getSpecialNumber());
        result.setCreatedAt(value.getCreatedAt());
        result.setNote(value.getNote());
        return result;
    }

    public LotteryNumberEntity toEntity(LotteryNumber value) {
        if (value == null) return null;
        var result = new LotteryNumberEntity();
        result.setQuality(copyMap(value.getQuality()));
        result.setId(value.getId());
        result.setDrawDate(value.getDrawDate());
        result.setCategory(value.getCategory());
        result.setNumbers(value.getNumbers() == null ? null : new ArrayList<>(value.getNumbers()));
        result.setSpecialNumber(value.getSpecialNumber());
        result.setCreatedAt(value.getCreatedAt());
        result.setNote(value.getNote());
        return result;
    }

    public UserTicket toDomain(UserTicketEntity value) {
        if (value == null) return null;
        var result = new UserTicket();
        result.setId(value.getId());
        result.setCategory(value.getCategory());
        result.setDrawDate(value.getDrawDate());
        result.setNumbers(value.getNumbers() == null ? null : new ArrayList<>(value.getNumbers()));
        result.setPrize(value.getPrize());
        result.setCheckedAt(value.getCheckedAt());
        return result;
    }

    public UserTicketEntity toEntity(UserTicket value) {
        if (value == null) return null;
        var result = new UserTicketEntity();
        result.setId(value.getId());
        result.setCategory(value.getCategory());
        result.setDrawDate(value.getDrawDate());
        result.setNumbers(value.getNumbers() == null ? null : new ArrayList<>(value.getNumbers()));
        result.setPrize(value.getPrize());
        result.setCheckedAt(value.getCheckedAt());
        return result;
    }

    public AlgorithmHyperparameter toDomain(AlgorithmHyperparameterEntity value) {
        if (value == null) return null;
        var result = new AlgorithmHyperparameter();
        result.setId(value.getId());
        result.setVersion(value.getVersion());
        result.setDrawDate(value.getDrawDate());
        result.setCategory(value.getCategory());
        result.setModel(value.getModel());
        result.setHyperparametersJson(value.getHyperparametersJson());
        result.setReadmeContent(value.getReadmeContent());
        result.setCreatedAt(value.getCreatedAt());
        result.setNote(value.getNote());
        return result;
    }

    public AlgorithmHyperparameterEntity toEntity(AlgorithmHyperparameter value) {
        if (value == null) return null;
        var result = new AlgorithmHyperparameterEntity();
        result.setId(value.getId());
        result.setVersion(value.getVersion());
        result.setDrawDate(value.getDrawDate());
        result.setCategory(value.getCategory());
        result.setModel(value.getModel());
        result.setHyperparametersJson(value.getHyperparametersJson());
        result.setReadmeContent(value.getReadmeContent());
        result.setCreatedAt(value.getCreatedAt());
        result.setNote(value.getNote());
        return result;
    }

    public LotteryDeviationVariable toDomain(LotteryDeviationVariableEntity value) {
        if (value == null) return null;
        var result = new LotteryDeviationVariable();
        result.setId(value.getId());
        result.setCategory(value.getCategory());
        result.setBaseDrawDate(value.getBaseDrawDate());
        result.setTargetDrawDate(value.getTargetDrawDate());
        result.setAiPredictedNumber(value.getAiPredictedNumber());
        result.setActualNumber(value.getActualNumber());
        result.setVariableDelta(value.getVariableDelta());
        result.setVariableType(value.getVariableType());
        result.setPatternName(value.getPatternName());
        result.setProbabilityShift(value.getProbabilityShift());
        result.setTransformationRule(value.getTransformationRule());
        result.setCreatedAt(value.getCreatedAt());
        result.setNote(value.getNote());
        return result;
    }

    public LotteryDeviationVariableEntity toEntity(LotteryDeviationVariable value) {
        if (value == null) return null;
        var result = new LotteryDeviationVariableEntity();
        result.setId(value.getId());
        result.setCategory(value.getCategory());
        result.setBaseDrawDate(value.getBaseDrawDate());
        result.setTargetDrawDate(value.getTargetDrawDate());
        result.setAiPredictedNumber(value.getAiPredictedNumber());
        result.setActualNumber(value.getActualNumber());
        result.setVariableDelta(value.getVariableDelta());
        result.setVariableType(value.getVariableType());
        result.setPatternName(value.getPatternName());
        result.setProbabilityShift(value.getProbabilityShift());
        result.setTransformationRule(value.getTransformationRule());
        result.setCreatedAt(value.getCreatedAt());
        result.setNote(value.getNote());
        return result;
    }

    private Map<String,Object> copyMap(Map<String,Object> value) {
        if (value == null) return null;
        Map<String,Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(key, copyValue(item)));
        return result;
    }

    private Object copyValue(Object value) {
        if (value instanceof Map<?,?> map) {
            Map<Object,Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(key, copyValue(item)));
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(this::copyValue).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        return value;
    }
}
