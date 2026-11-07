package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.LotteryNumberRepository;
import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.domain.service.*;
import com.example.analyzeproject.infrastructure.persistence.jdbc.JdbcAiLabStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="WORKSPACE_TEST_JDBC",matches=".+")
class ResearchStorePostgresTest {
    @Test void challengerSnapshotsRetainTheirPoliciesAndHashesAcrossDatabaseReload() {
        String schema="study_it_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("WORKSPACE_TEST_JDBC"),user=System.getenv().getOrDefault("WORKSPACE_TEST_USER","workspace_test");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,""));admin.execute("CREATE SCHEMA "+schema);
        try {
            var ds=new DriverManagerDataSource(url+"?currentSchema="+schema,user,"");
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V12__add_ai_lab.sql")).execute(ds);
            var tx=new TransactionTemplate(new DataSourceTransactionManager(ds));var jdbc=new JdbcTemplate(ds);
            var store=new JdbcAiLabStore(jdbc);var repository=mock(LotteryNumberRepository.class);
            var rows=new ArrayList<LotteryNumber>();LocalDate date=LocalDate.of(2026,9,30);
            while(date.isBefore(LocalDate.of(2026,10,26))) {
                rows.add(TestDraws.verified(new LotteryNumber(date,"MEGA",List.of(1,2,3,4,5,6),null,null)));
                date=TemporalRelationshipModel.scheduledAfter(date,"MEGA");
            }
            when(repository.findAllByOrderByDrawDateDescCreatedAtDesc()).thenReturn(rows);
            var lab=new AiLabService(store,repository,true,AiLabModel.FeedbackPolicy.defaults(),Clock.fixed(Instant.parse("2026-10-26T10:00:00Z"),ZoneOffset.UTC));
            tx.executeWithoutResult(status->lab.run());
            var first=store.snapshots();assertEquals(AiLabModel.MODELS.size()*AiLabModel.BUDGET,first.size());
            first.forEach(f->assertTrue(ForecastProtocol.isProspective(f)));
            String hash=ForecastProtocol.hash(first);tx.executeWithoutResult(status->lab.run());
            assertEquals(hash,ForecastProtocol.hash(new JdbcAiLabStore(jdbc).snapshots()));
            var coverage=first.stream().filter(f->"logistic_coverage".equals(f.get("labModel"))).toList();
            assertEquals(5,coverage.size());assertEquals(5,coverage.stream().map(f->f.get("numbers")).distinct().count());
            assertTrue(((Map<?,?>)coverage.get(0).get("configuration")).containsKey("coveragePolicy"));
            var actual=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,28),"MEGA",List.of(1,2,3,7,8,9),null,null));
            tx.executeWithoutResult(status->store.reconcile(List.of(actual),Instant.parse("2026-10-28T13:00:00Z")));
            @SuppressWarnings("unchecked") var events=(List<Map<String,Object>>)store.ledger().get("settlements");
            assertEquals(AiLabModel.MODELS.size()*AiLabModel.BUDGET,events.stream().filter(e->"SETTLED".equals(e.get("status"))).count());
        } finally { admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
