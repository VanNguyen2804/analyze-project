package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.PageJobStore.Work;
import com.example.analyzeproject.application.dto.WorkspaceRequests.Create;
import com.example.analyzeproject.domain.model.stock.StockModels.Mode;
import java.util.Objects;
import java.time.Clock;

/** Use cases executed by a bounded worker, never by an HTTP polling request. */
public final class PageJobTasks {
    private final StockService stocks;
    private final AiLabService lab;
    private final WorkspaceService workspace;
    private final Clock clock;
    public PageJobTasks(StockService stocks,AiLabService lab,WorkspaceService workspace,Clock clock) {
        this.stocks=stocks;this.lab=lab;this.workspace=workspace;this.clock=clock;
    }
    public Object execute(Work work) {
        var p=work.input();String category=Objects.toString(p.get("category"),"MEGA");
        if(work.kind().equals("ANALYZE")&&!ForecastProtocol.target(category,clock.instant()).toString().equals(p.get("_target")))
            throw new IllegalStateException("Kỳ mục tiêu vừa thay đổi. Hãy tải lại.");
        if(work.kind().equals("LAB_OVERVIEW")||work.kind().equals("LAB_HIGHLIGHTS"))lab.refreshReadCache();
        return switch(work.kind()) {
            case "STOCK_ANALYSIS" -> stocks.analyzeCached((String)p.get("symbol"),Mode.valueOf((String)p.get("mode")));
            case "STOCK_WATCHLIST" -> stocks.watchlist();
            case "LAB_OVERVIEW" -> lab.overview(category,((Number)p.get("page")).intValue());
            case "LAB_HIGHLIGHTS" -> lab.highlights(category);
            case "LAB_RUN" -> lab.run();
            case "ANALYZE" -> workspace.create(new Create(category,(String)p.get("algorithm"),(String)p.get("algorithm2")));
            default -> throw new IllegalArgumentException("Unknown page task");
        };
    }
}
