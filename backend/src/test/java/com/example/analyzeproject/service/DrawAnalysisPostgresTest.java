package com.example.analyzeproject.service;

import com.example.analyzeproject.repository.*;
import com.example.analyzeproject.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

/** Real stores, isolated schema, with draw input supplied explicitly; never accesses production. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_TEST_JDBC",matches=".+")
class DrawAnalysisPostgresTest {
    @Configuration @EnableTransactionManagement static class Config {
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean ForecastStore forecasts(JdbcTemplate j) { return new ForecastStore(j); }
        @Bean AiLabStore lab(JdbcTemplate j) { return new AiLabStore(j); }
        @Bean WorkspaceStore workspace(JdbcTemplate j) { return new WorkspaceStore(j); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean LotteryNumberRepository draws() { return mock(LotteryNumberRepository.class); }
        @Bean UserTicketRepository users() { return mock(UserTicketRepository.class); }
        @Bean DrawAnalysisService analysis(LotteryNumberRepository d,UserTicketRepository u,ForecastStore f,AiLabStore l,WorkspaceStore w) {
            return new DrawAnalysisService(d,u,f,l,w,Clock.fixed(Instant.parse("2026-10-29T14:00:00Z"),ZoneOffset.UTC));
        }
    }
    @Test void frozenAndLegacySnapshotsReadBackWithoutCreatingAnyRows() {
        String schema="analysis_it_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("WORKSPACE_TEST_JDBC"), user=System.getenv().getOrDefault("WORKSPACE_TEST_USER","workspace_test");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,"")); admin.execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource(url+"?currentSchema="+schema,user,"");
        var context=new AnnotationConfigApplicationContext();
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V11__add_forecast_ledger.sql"),new ClassPathResource("db/migration/V12__add_ai_lab.sql"),new ClassPathResource("db/migration/V13__add_prediction_workspace.sql")).execute(ds);
            context.registerBean(DataSource.class,()->ds);context.register(Config.class);context.refresh();
            var history=List.of(17,20,22,24,27).stream().map(day->TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,day),"POWER",List.of(7,9,18,27,31,50),53,null))).toList();
            var actual=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,29),"POWER",List.of(9,29,33,38,45,46),53,null));
            when(context.getBean(LotteryNumberRepository.class).findByCategoryOrderByDrawDateDescCreatedAtDesc("POWER")).thenReturn(List.of(actual));
            var f=ForecastProtocol.prepare(history,"POWER","temporal_learning","v1","code",Map.of(),List.of(7,11,14,18,23,27),Instant.parse("2026-10-27T13:00:00Z"));
            context.getBean(ForecastStore.class).capture(f);
            var suggestion=new LinkedHashMap<>(f); suggestion.put("id","suggestion");suggestion.put("status","READY");suggestion.put("primaryAlgorithm","temporal_learning");suggestion.put("secondaryAlgorithm","bayesian_graph");
            suggestion.put("primaryRanking",java.util.stream.IntStream.rangeClosed(1,55).boxed().toList());
            suggestion.put("tickets",List.of(map("id","primary","numbers",List.of(1,2,3,4,5,6))));
            context.getBean(WorkspaceStore.class).saveSuggestion(suggestion);
            var jdbc=context.getBean(JdbcTemplate.class);var before=counts(jdbc);
            var report=context.getBean(DrawAnalysisService.class).report("POWER","2026-10-29","temporal_learning",null);
            assertEquals(2,report.evidence().size());
            assertTrue(report.evidence().stream().anyMatch(e->e.explanationStatus().equals("NOT_SAVED")));
            assertTrue(report.evidence().stream().anyMatch(e->e.explanationStatus().equals("RANKING_ONLY")));
            context.getBean(DrawAnalysisService.class).report("POWER","2026-10-29","temporal_learning","workspace:suggestion:primary");
            assertEquals(before,counts(jdbc));
            assertEquals(f,context.getBean(ForecastStore.class).forecasts().get(0));
        } finally { context.close();admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
    private static List<Integer> counts(JdbcTemplate jdbc) {
        return List.of("forecast_snapshots","forecast_settlements","ai_lab_snapshots","ai_lab_settlements","prediction_suggestions","prediction_user_batches","prediction_user_settlements")
                .stream().map(table->jdbc.queryForObject("SELECT COUNT(*) FROM "+table,Integer.class)).toList();
    }
}
