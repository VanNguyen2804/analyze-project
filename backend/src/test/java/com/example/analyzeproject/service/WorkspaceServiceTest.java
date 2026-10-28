package com.example.analyzeproject.service;
import com.example.analyzeproject.dto.*;
import com.example.analyzeproject.dto.WorkspaceRequests.*;
import com.example.analyzeproject.model.LotteryNumber;
import com.example.analyzeproject.repository.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

class WorkspaceServiceTest {
    private final AnalyzeService analyze=mock(AnalyzeService.class);
    private final LotteryNumberRepository draws=mock(LotteryNumberRepository.class);
    private final AiLabStore lab=mock(AiLabStore.class);
    private final WorkspaceStore store=mock(WorkspaceStore.class);
    private final Map<String,Map<String,Object>> saved=new HashMap<>();
    private final Instant now=Instant.parse("2026-10-26T08:00:00Z");
    private final List<LotteryNumber> rows=WorkspaceTest.history("MEGA",LocalDate.of(2026,10,25));
    private final WorkspaceService service=new WorkspaceService(analyze,draws,lab,store,Clock.fixed(now,ZoneOffset.UTC));
    @BeforeEach void setup() {
        when(analyze.getAlgorithmCatalog()).thenReturn(List.of(map("id","temporal_learning"),map("id","bayesian_graph")));
        when(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc("MEGA")).thenReturn(rows); when(lab.snapshots()).thenReturn(List.of());
        var prediction=new PredictionResponseDto();
        var first=IntStream.rangeClosed(1,45).boxed().toList(); var second=new ArrayList<>(first); Collections.reverse(second);
        prediction.setPrimaryAlgorithmResult(map("numbers",List.of(1,2,3,4,5,6),"ranking",first));
        prediction.setSecondaryAlgorithmResult(map("numbers",List.of(40,41,42,43,44,45),"ranking",second));
        prediction.setCombinedAlgorithmResult(map("numbers",List.of(1,2,3,43,44,45)));
        when(analyze.analyzeAndPredictWithConfiguration(anyString(),anyString(),anyString(),anyList(),isNull())).thenReturn(prediction);
        doAnswer(call->{var value=call.<Map<String,Object>>getArgument(0);saved.put((String)value.get("id"),value);return null;}).when(store).saveSuggestion(anyMap());
        when(store.suggestion(anyString())).thenAnswer(call->saved.get(call.<String>getArgument(0)));
        when(store.commit(any(),any())).thenAnswer(call->call.<Supplier<Map<String,Object>>>getArgument(1).get());
    }
    @Test void createSavesThreeImmutableSourcesAndCombinationDoesNotMutateTheOriginal() {
        var initial=service.create(new Create("MEGA","temporal_learning","bayesian_graph")); var source=initial.getWorkspace();
        assertEquals("SUCCESS",initial.getStatus()); assertNull(initial.getForecastSnapshot()); assertEquals(3,((List<?>)source.get("tickets")).size());
        assertFalse(source.containsKey("history")); assertFalse(source.containsKey("primaryRanking"));
        var variant=service.combine((String)source.get("id"),new Combine(0.0,List.of(22),List.of(45)));
        assertNotEquals(source.get("id"),variant.get("id")); assertEquals(2,saved.size());
        assertEquals(List.of(1,2,3,43,44,45),((Map<?,?>)((List<?>)source.get("tickets")).get(2)).get("numbers"));
        assertEquals(List.of(22,40,41,42,43,44),((Map<?,?>)((List<?>)variant.get("tickets")).get(2)).get("numbers"));
        verify(lab,never()).captureBatch(anyList()); verify(lab,never()).reconcile(anyList(),any());
    }
    @Test void commitBindsEditedTicketToServerOriginalAndRejectsUnknownSourcesOrDuplicates() {
        var source=service.create(new Create("MEGA","temporal_learning","bayesian_graph")).getWorkspace(); String id=(String)source.get("id");
        var request=new Commit("MEGA","2026-10-28",id,0,"request",List.of(new Ticket("primary",List.of(1,2,3,4,5,22))),3);
        var batch=service.commit(request); var ticket=(Map<?,?>)((List<?>)batch.get("tickets")).get(0);
        assertEquals(List.of(1,2,3,4,5,6),ticket.get("originalNumbers")); assertEquals(true,ticket.get("edited")); assertEquals(3,batch.get("labCount"));
        assertThrows(IllegalArgumentException.class,()->service.commit(new Commit("MEGA","2026-10-28",id,0,"bad-source",List.of(new Ticket("not-offered",List.of(1,2,3,4,5,6))),5)));
        assertThrows(IllegalArgumentException.class,()->service.commit(new Commit("MEGA","2026-10-28",id,0,"duplicate",List.of(new Ticket("primary",List.of(1,2,3,4,5,6)),new Ticket(null,List.of(6,5,4,3,2,1))),5)));
    }
    @Test void changedHistoryBlocksSourceCommitButManualInputRemainsPossibleWithoutInventingSuggestions() {
        var source=service.create(new Create("MEGA","temporal_learning","bayesian_graph")).getWorkspace(); String id=(String)source.get("id");
        rows.get(0).setNumbers(List.of(10,11,12,13,14,15)); TestDraws.verified(rows.get(0));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.commit(new Commit("MEGA","2026-10-28",id,0,"stale",List.of(new Ticket("primary",List.of(1,2,3,4,5,6))),5)));
        var manual=service.commit(new Commit("MEGA","2026-10-28",id,0,"manual",List.of(new Ticket(null,List.of(1,2,3,4,5,6))),5));
        assertEquals("MANUAL",((Map<?,?>)((List<?>)manual.get("tickets")).get(0)).get("source"));
        when(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc("MEGA")).thenReturn(List.of());
        var waiting=service.create(new Create("MEGA","temporal_learning","bayesian_graph"));
        assertEquals("WAITING",waiting.getStatus()); assertTrue(((List<?>)waiting.getWorkspace().get("tickets")).isEmpty());
    }
    @Test void cutoffRejectsStaleScreenInsteadOfMovingItsTicketsToTheNextDraw() {
        var source=service.create(new Create("MEGA","temporal_learning","bayesian_graph")).getWorkspace();
        var later=new WorkspaceService(analyze,draws,lab,store,Clock.fixed(Instant.parse("2026-10-28T11:00:00Z"),ZoneOffset.UTC));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->later.commit(new Commit("MEGA","2026-10-28",(String)source.get("id"),0,"late",List.of(new Ticket(null,List.of(1,2,3,4,5,6))),5)));
    }
}
