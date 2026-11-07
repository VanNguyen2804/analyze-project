package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.domain.model.LotteryNumber;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.mockito.Mockito.mock;

/** Explicit report export for a local snapshot; no external connection and no ledger writes. */
class PredictionStudyReportTest {
    @Test @EnabledIfSystemProperty(named="prediction.study.report",matches=".+")
    void exportDevelopmentStudy() throws Exception {
        var json=new ObjectMapper().findAndRegisterModules().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        byte[] bytes;
        try(var stream=getClass().getResourceAsStream("/data/lottery_numbers.json")){bytes=stream.readAllBytes();}
        List<LotteryNumber> rows=json.readValue(bytes,new TypeReference<>(){});
        var analyze=new AnalyzeService(mock(LotteryNumberRepository.class),mock(UserTicketRepository.class),mock(AlgorithmHyperparameterRepository.class),null);
        var study=new PredictionBenchmark(analyze);
        var report=new LinkedHashMap<String,Object>();report.put("source","repository_fixture");
        report.put("inputSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        report.put("MEGA",study.run(rows,"MEGA",PredictionBenchmark.Options.defaults()));
        report.put("POWER",study.run(rows,"POWER",PredictionBenchmark.Options.defaults()));
        var path=Path.of(System.getProperty("prediction.study.report"));Files.createDirectories(path.toAbsolutePath().getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),report);
    }
}
