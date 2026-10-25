package com.example.analyzeproject.service;
import com.example.analyzeproject.model.LotteryNumber;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DrawQualityTest {
    LotteryNumber old() { return new LotteryNumber(LocalDate.of(2026,10,10),"POWER",List.of(4,5,6,29,32,44),53,"Original note"); }
    LotteryNumber source() { return new LotteryNumber(LocalDate.of(2026,10,10),"POWER",List.of(14,17,21,23,24,39),30,null); }
    @Test void reviewedCorrectionIsAuditedAndIdempotent() {
        var row=old(); var source=source();
        assertTrue(DrawQuality.reconcile(row,source,"https://example.test/feed","01409",null));
        assertTrue(DrawQuality.eligible(row));
        assertEquals(source.getNumbers(),row.getNumbers());
        var event=(Map<?,?>)((List<?>)row.getQuality().get("audit")).get(0);
        assertEquals("Original note",((Map<?,?>)event.get("before")).get("note"));
        assertNotNull(event.get("evidenceUrl"));
        assertFalse(DrawQuality.reconcile(row,source,"https://example.test/feed","01409",null));
    }
    @Test void missingSpecialIsFilledButUnknownConflictIsPreserved() {
        var row=source(); row.setSpecialNumber(null);
        DrawQuality.reconcile(row,source(),"https://example.test/feed","01409",null);
        assertEquals(30,row.getSpecialNumber());
        var unknown=old(); unknown.setSpecialNumber(54);
        DrawQuality.reconcile(unknown,source(),"https://example.test/feed","01409",null);
        assertEquals("QUARANTINED",unknown.getQuality().get("status"));
        assertEquals(54,unknown.getSpecialNumber());
    }
    @Test void editedAndUnverifiedResultsCannotEnterLearning() {
        var row=old();
        assertFalse(DrawQuality.eligible(row));
        DrawQuality.reconcile(row,source(),"https://example.test/feed","01409",null);
        assertEquals(1,TemporalRelationshipModel.fit(List.of(row),"POWER").analysis().get("trainingDraws"));
        row.setNumbers(List.of(1,2,3,4,5,6));
        assertEquals(0,TemporalRelationshipModel.fit(List.of(row),"POWER").analysis().get("trainingDraws"));
        DrawQuality.reconcile(row,null,"https://example.test/feed",null,"SOURCE_NOT_FOUND");
        assertFalse(DrawQuality.eligible(row));
    }
    @Test void invalidDuplicateMainBallsAreNotTreatedAsMissingSpecialOnly() {
        var row=source(); row.setNumbers(List.of(14,17,21,23,24,39,39)); row.setSpecialNumber(null);
        DrawQuality.reconcile(row,source(),"https://example.test/feed","01409",null);
        assertEquals("QUARANTINED",row.getQuality().get("status"));
        assertEquals(7,row.getNumbers().size());
        row.setNumbers(null);
        DrawQuality.reconcile(row,source(),"https://example.test/feed","01409",null);
        assertFalse(DrawQuality.eligible(row));
    }
    @Test void latestUnverifiedRevisionDoesNotFallBackToOldSourceMatch() {
        var matched=old(); DrawQuality.reconcile(matched,source(),"https://example.test/feed","01409",null);
        var newer=old(); newer.setCreatedAt(matched.getCreatedAt().plusSeconds(1));
        assertTrue(DrawQuality.history(List.of(matched,newer)).isEmpty());
        assertEquals(0,TemporalRelationshipModel.fit(List.of(matched,newer),"POWER").analysis().get("trainingDraws"));
    }
}
