package com.example.analyzeproject.infrastructure.persistence;

import com.example.analyzeproject.application.port.out.LotteryNumberRepository;
import com.example.analyzeproject.application.service.LotteryResultImporter;
import com.example.analyzeproject.domain.model.FeedDraw;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/** Keeps the advisory lock and all imported revisions in the same transaction. */
public class TransactionalLotteryResultImporter extends LotteryResultImporter {
    public TransactionalLotteryResultImporter(LotteryNumberRepository draws, JdbcTemplate jdbc) {
        super(draws, () -> jdbc.execute("SELECT pg_advisory_xact_lock(76452655)"));
    }

    @Override
    @Transactional
    public ImportResult importDraws(List<FeedDraw> draws, String sourceUrl) {
        return super.importDraws(draws, sourceUrl);
    }
}
