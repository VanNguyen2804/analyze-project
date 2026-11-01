package com.example.analyzeproject.service;

import com.example.analyzeproject.repository.MegaPortfolioStore;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real PostgreSQL, new disposable schema; never imports fixtures into the application database. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_TEST_JDBC",matches=".+")
class MegaPortfolioStorePostgresTest {
    @Configuration @EnableTransactionManagement static class Config {
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
        @Bean MegaPortfolioStore store(JdbcTemplate jdbc) { return new MegaPortfolioStore(jdbc); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
    @Test @SuppressWarnings("unchecked") void concurrentCaptureKeepsOneCompleteBatchAndReconciliationIsDurable() throws Exception {
        String schema="mega_portfolio_it_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("WORKSPACE_TEST_JDBC"),user=System.getenv().getOrDefault("WORKSPACE_TEST_USER","workspace_test");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,""));admin.execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource(url+"?currentSchema="+schema,user,"");
        var context=new AnnotationConfigApplicationContext();
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V14__add_mega_portfolio_experiment.sql")).execute(ds);
            context.registerBean(DataSource.class,()->ds);context.register(Config.class);context.refresh();
            var store=context.getBean(MegaPortfolioStore.class);var jdbc=context.getBean(JdbcTemplate.class);
            var first=MegaPortfolioProtocolTest.snapshot();var alternative=new LinkedHashMap<>(first);alternative.put("codeVersion","another-version");
            var pool=Executors.newFixedThreadPool(2);
            Map<String,Object> saved;
            try {
                var a=pool.submit(()->store.capture(first));var b=pool.submit(()->store.capture(alternative));
                saved=a.get(10,TimeUnit.SECONDS);assertEquals(saved,b.get(10,TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mega_portfolio_snapshots",Integer.class));
            assertTrue(MegaPortfolioProtocol.valid(saved));
            assertEquals(saved,store.capture(alternative));
            var before=store.ledger();assertEquals(before,store.ledger());
            assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM mega_portfolio_settlements",Integer.class));
            var actual=MegaPortfolioProtocolTest.actual(List.of(1,2,7,8,11,12));var at=Instant.parse("2026-10-25T12:00:00Z");
            store.reconcile(List.of(actual),at);store.reconcile(List.of(actual),at.plusSeconds(60));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mega_portfolio_settlements",Integer.class));
            actual.setNumbers(List.of(30,31,32,33,34,35));store.reconcile(List.of(actual),at);
            var ledger=store.ledger();var events=(List<Map<String,Object>>)ledger.get("settlements");
            assertEquals("RESULT_UNAVAILABLE",events.get(events.size()-1).get("status"));
            TestDraws.verified(actual);store.reconcile(List.of(actual),at);
            events=(List<Map<String,Object>>)store.ledger().get("settlements");
            assertEquals(3,events.size());assertEquals("SETTLED",events.get(2).get("status"));
            assertEquals(store.ledger(),new MegaPortfolioStore(jdbc).ledger());
            var rows=new ArrayList<>(MegaPortfolioProtocolTest.history());rows.add(actual);
            var conflict=MegaPortfolioProtocol.prepare(rows,MegaPortfolioProtocolTest.prediction(),"code",Instant.parse("2026-10-26T00:00:00Z"));
            conflict.put("id",saved.get("id")); // Same PK on a different date must roll back.
            assertThrows(Exception.class,()->store.capture(conflict));
            assertNull(store.find(LocalDate.of(2026,10,28)));
            var invalid=new LinkedHashMap<>(first);invalid.put("payloadHash","tampered");
            assertThrows(IllegalArgumentException.class,()->store.capture(invalid));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM mega_portfolio_snapshots",Integer.class));
        } finally { context.close();admin.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
