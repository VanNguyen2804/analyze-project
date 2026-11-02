package com.example.analyzeproject.infrastructure.persistence;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.application.service.DrawAnalysisService;
import com.example.analyzeproject.application.dto.DrawReport;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;

/** Reports read all ledgers and results from one consistent database snapshot. */
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class TransactionalDrawAnalysisService extends DrawAnalysisService {
    public TransactionalDrawAnalysisService(LotteryNumberRepository draws, UserTicketRepository users,
            ForecastStore forecasts, AiLabStore lab, WorkspaceStore workspace, Clock clock) {
        super(draws, users, forecasts, lab, workspace, clock);
    }

    @Override
    public DrawReport report(String category, String requestedDate, String algorithm, String evidenceId) {
        return super.report(category, requestedDate, algorithm, evidenceId);
    }

    @Override
    public DrawReport.TicketGroup evaluateTickets(DrawReport.EvaluateRequest request) {
        return super.evaluateTickets(request);
    }
}
