package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.junit.jupiter.api.Assertions.*;

class ConcentrationStudyReportTest {
    @Test @EnabledIfSystemProperty(named="concentration.report",matches=".+")
    @SuppressWarnings("unchecked") void exportReadOnlyFiveTicketStudy() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        byte[] input=Files.readAllBytes(Path.of(System.getProperty("concentration.input")));
        List<LotteryNumber> rows=mapper.readValue(input,new TypeReference<>(){});
        var report=new PredictionBenchmark(null).run(rows,"MEGA",new PredictionBenchmark.Options(10,30,180,5));
        assertEquals("COMPLETED",report.get("status"));
        for(var draw:(List<Map<String,Object>>)report.get("byDraw")) {
            assertTrue(draw.get("trainingThrough").toString().compareTo(draw.get("targetDate").toString())<0);
            var methods=(Map<String,Map<String,Object>>)draw.get("methods");
            for(String method:List.of("logistic_default","logistic_coverage","logistic_concentrated","logistic_hybrid")) {
                var result=methods.get(method);assertEquals(5,((List<?>)result.get("tickets")).size());
                assertEquals(methods.get("logistic_default").get("ranking"),result.get("ranking"));
            }
        }
        var document=new LinkedHashMap<String,Object>();
        document.put("source","read-only production export; source-matched community data, not independent official verification");
        document.put("inputSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input)));
        document.put("MEGA",report);
        var destination=Path.of(System.getProperty("concentration.report"));Files.createDirectories(destination.toAbsolutePath().getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(),document);
    }
}
