package com.example.analyzeproject.service;
import com.example.analyzeproject.model.LotteryNumber;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisReportSupportTest {
    @Test void measuresTicketsAndDoesNotInventRatesForEmptyHistory() {
        var draws=List.<Map<String,Object>>of(
            Map.of("drawDate","2026-10-23","aiPrediction",Map.of("predicted6Numbers",List.of(1,2,3,4,5,6),"matchedIn6Count",3)),
            Map.of("drawDate","2026-10-21","aiPrediction",Map.of("predicted6Numbers",List.of(1,2,3,4,5,6),"matchedIn6Count",0)),
            Map.of("drawDate","2026-10-18","aiPrediction",Map.of("predicted6Numbers",List.of(),"matchedIn6Count",0)));
        var report=AnalysisReportSupport.summarize(draws,39,"temporal_learning","MEGA");
        assertEquals(2,report.get("evaluatedDrawsCount")); assertEquals(1.5,report.get("averageMatched"));
        assertEquals(50.0,report.get("hit3RatePercent")); assertEquals(39,report.get("totalHistoricalDraws"));
        assertNull(AnalysisReportSupport.summarize(List.of(),0,"temporal_learning","MEGA").get("averageMatched"));
    }
    @Test void explanationsUseObservedFrequencyAndUnknownGaps() {
        var prior=new LotteryNumber(LocalDate.of(2026,10,22),"POWER",List.of(1,2,3,4,5,6),7,null);
        var draw=new LotteryNumber(LocalDate.of(2026,10,24),"POWER",List.of(1,2,8,9,10,11),7,null);
        var explanation=AnalysisReportSupport.explain(draw,List.of(prior),List.of(1,2,3,4,5,6));
        var balls=(List<?>)explanation.get("whyWinningBallsAppeared");
        var absent=(Map<?,?>)balls.get(2); assertEquals(0,absent.get("frequency")); assertNull(absent.get("drawGap"));
        var special=(Map<?,?>)balls.get(6); assertEquals(true,special.get("isSpecial")); assertEquals(1,special.get("frequency"));
    }
}
