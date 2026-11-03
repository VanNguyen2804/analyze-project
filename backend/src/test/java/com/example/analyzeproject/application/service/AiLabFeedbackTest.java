package com.example.analyzeproject.application.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.analyzeproject.application.port.out.*;

class AiLabFeedbackTest {
    private final Instant captured = Instant.parse("2026-10-25T10:00:00Z");
    private final Instant settled = Instant.parse("2026-10-25T12:01:00Z");
    private final LocalDate next = LocalDate.of(2026,10,28);

    private List<LotteryNumber> prior() {
        List<LotteryNumber> rows = new ArrayList<>();
        for (LocalDate date=LocalDate.of(2026,9,30);date.isBefore(LocalDate.of(2026,10,25));date=TemporalRelationshipModel.scheduledAfter(date,"MEGA"))
            rows.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
        return rows;
    }
    private List<LotteryNumber> known() {
        var rows = prior(); rows.add(TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,25),"MEGA",List.of(1,2,3,4,5,6),null,null)));
        return rows;
    }
    private List<Map<String,Object>> snapshots() {
        var rows = prior(); var fit = AiLabModel.fit(rows,"MEGA","logistic");
        var numbers = List.of(List.of(1,20,21,22,23,24),List.of(1,2,20,21,22,23),List.of(20,21,22,23,24,25),List.of(2,26,27,28,29,30),List.of(2,3,31,32,33,34));
        List<Map<String,Object>> snapshots = new ArrayList<>();
        for(int slot=0;slot<5;slot++) {
            var f = ForecastProtocol.prepare(rows,"MEGA","lab-logistic-"+slot,"ai-lab-logistic-v1","old-code",Map.of(),numbers.get(slot),captured);
            f.put("labModel","logistic");f.put("slot",slot);f.put("explanation",PredictionExplanation.lab(fit,"logistic"));snapshots.add(f);
        }
        return snapshots;
    }
    @Test void oneAndTwoHitsUpdateLearnedWeightsWithoutWaitingForThreeAndReplayIsDeterministic() {
        var snapshots=snapshots();String original=ForecastProtocol.hash(snapshots);
        var evidence=AiLabFeedback.collect(snapshots,known(),"MEGA",next,settled,60);
        assertEquals(1,evidence.rounds().size());assertEquals(5,evidence.audit().get("tickets"));
        assertEquals(List.of(1,2,2,0,0,0,0),evidence.audit().get("ticketMatchHistogram"));
        var base=AiLabModel.fit(known(),"MEGA","logistic");
        var learned=AiLabModel.fit(known(),"MEGA","logistic",evidence.rounds(),AiLabModel.FeedbackPolicy.defaults());
        assertFalse(Arrays.equals(base.weights(),learned.weights()));assertFalse(Arrays.equals(base.scores(),learned.scores()));
        assertArrayEquals(learned.weights(),AiLabModel.fit(known(),"MEGA","logistic",evidence.rounds(),AiLabModel.FeedbackPolicy.defaults()).weights());
        assertEquals(original,ForecastProtocol.hash(snapshots));
        assertEquals(45,evidence.rounds().get(0).examples().size());
        assertEquals(6,evidence.rounds().get(0).examples().stream().filter(AiLabModel.FeedbackExample::occurred).count());
        assertEquals(45,evidence.rounds().get(0).examples().stream().map(AiLabModel.FeedbackExample::number).distinct().count());
        var reversed=new ArrayList<>(snapshots);Collections.reverse(reversed);
        var reordered=AiLabFeedback.collect(reversed,known(),"MEGA",next,settled,60);
        assertEquals(evidence.audit().get("dataHash"),reordered.audit().get("dataHash"));
        assertArrayEquals(learned.weights(),AiLabModel.fit(known(),"MEGA","logistic",reordered.rounds(),AiLabModel.FeedbackPolicy.defaults()).weights());
        assertArrayEquals(AiLabModel.fit(known(),"MEGA","recency").scores(),AiLabModel.fit(known(),"MEGA","recency",evidence.rounds(),AiLabModel.FeedbackPolicy.defaults()).scores());
    }
    @Test void noFutureUnknownPartialUnverifiedOrLatePredictionCanEnterFeedback() {
        var snapshots=snapshots();
        assertTrue(AiLabFeedback.collect(snapshots,prior(),"MEGA",next,settled,60).rounds().isEmpty());
        assertTrue(AiLabFeedback.collect(snapshots,known(),"MEGA",next,captured,60).rounds().isEmpty());
        assertTrue(AiLabFeedback.collect(snapshots,known(),"MEGA",LocalDate.of(2026,10,25),settled,60).rounds().isEmpty());
        assertTrue(AiLabFeedback.collect(snapshots,known(),"POWER",next,settled,60).rounds().isEmpty());
        assertTrue(AiLabFeedback.collect(snapshots.subList(0,4),known(),"MEGA",next,settled,60).rounds().isEmpty());
        snapshots.get(0).put("createdAt","2026-10-25T12:00:00Z");
        assertTrue(AiLabFeedback.collect(snapshots,known(),"MEGA",next,settled,60).rounds().isEmpty());
        snapshots=snapshots(); snapshots.get(0).remove("explanation");
        assertTrue(AiLabFeedback.collect(snapshots,known(),"MEGA",next,settled,60).rounds().isEmpty());
        var incomplete=AiLabFeedback.collect(snapshots,known(),"MEGA",next,settled,60);assertEquals(0,incomplete.audit().get("tickets"));
        snapshots=snapshots();snapshots.get(0).remove("modelVersion");
        assertTrue(AiLabFeedback.collect(snapshots,known(),"MEGA",next,settled,60).rounds().isEmpty());
    }
    @Test void correctedOrWithdrawnResultsRebuildFeedbackInsteadOfAccumulatingOldRewards() {
        var snapshots=snapshots();var rows=known();
        var original=AiLabFeedback.collect(snapshots,rows,"MEGA",next,settled,60);
        rows.get(rows.size()-1).setNumbers(List.of(20,21,22,23,24,25));TestDraws.verified(rows.get(rows.size()-1));
        var corrected=AiLabFeedback.collect(snapshots,rows,"MEGA",next,settled,60);
        assertNotEquals(original.audit().get("dataHash"),corrected.audit().get("dataHash"));
        assertNotEquals(original.audit().get("ticketMatchHistogram"),corrected.audit().get("ticketMatchHistogram"));
        var withdrawn=AiLabFeedback.collect(snapshots,prior(),"MEGA",next,settled,60);assertTrue(withdrawn.rounds().isEmpty());
        assertThrows(IllegalArgumentException.class,()->new AiLabModel.FeedbackPolicy(Double.NaN,.25,60));
        assertThrows(IllegalArgumentException.class,()->new AiLabModel.FeedbackPolicy(.05,2,60));
    }
    @Test @SuppressWarnings("unchecked") void captureUsesFeedbackAndFreezesItsLineageWithoutOverwritingPreviousTickets() {
        var store=mock(AiLabStore.class);var repository=mock(LotteryNumberRepository.class);
        var old=snapshots();String original=ForecastProtocol.hash(old);List<Map<String,Object>> saved=new ArrayList<>(old);
        when(store.snapshots()).thenAnswer(call->new ArrayList<>(saved));
        when(repository.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(known());
        doAnswer(call->{saved.addAll((List<Map<String,Object>>)call.getArgument(0));return null;}).when(store).captureBatch(anyList());
        Clock clock=Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"),ZoneOffset.UTC);
        var service=new AiLabService(store,repository,true,AiLabModel.FeedbackPolicy.defaults(),clock);service.run();
        assertEquals(15,saved.size());assertEquals(original,ForecastProtocol.hash(old));
        var next=saved.get(5);assertEquals(AiLabModel.VERSION,next.get("modelVersion"));assertTrue(ForecastProtocol.isProspective(next));
        var explanation=(Map<String,Object>)next.get("explanation");var feedback=(Map<String,Object>)explanation.get("feedback");
        assertEquals(1,feedback.get("draws"));assertEquals(List.of(1,2,2,0,0,0,0),feedback.get("ticketMatchHistogram"));
        var evidence=AiLabFeedback.collect(old,known(),"MEGA",this.next,clock.instant(),60);
        var fit=AiLabModel.fit(known(),"MEGA","logistic",evidence.rounds(),AiLabModel.FeedbackPolicy.defaults());
        var weights=(Map<String,Double>)explanation.get("modelWeights");assertEquals(fit.weights()[0],weights.get("intercept"));
        assertEquals(fit.weights()[1],weights.get("frequency"));
        service.run();assertEquals(15,saved.size());verify(store,times(2)).captureBatch(anyList());
    }
}
