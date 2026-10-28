package com.example.analyzeproject.service;

import com.example.analyzeproject.dto.WorkspaceRequests.Commit;
import com.example.analyzeproject.repository.WorkspaceStore;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static com.example.analyzeproject.service.ForecastProtocol.map;

/** Opt-in real PostgreSQL test, isolated in a new disposable schema. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_TEST_JDBC",matches=".+")
class WorkspaceStorePostgresTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean JdbcTemplate jdbc(DataSource dataSource) { return new JdbcTemplate(dataSource); }
        @Bean WorkspaceStore store(JdbcTemplate jdbc) { return new WorkspaceStore(jdbc); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
    }
    @Test void migrationConcurrentRetryConflictRollbackAndResultCorrectionAreDurable() throws Exception {
        String schema="workspace_it_"+UUID.randomUUID().toString().replace("-","");
        var admin=new DriverManagerDataSource(System.getenv("WORKSPACE_TEST_JDBC"),System.getenv().getOrDefault("WORKSPACE_TEST_USER","workspace_test"),"");
        var adminJdbc=new JdbcTemplate(admin); adminJdbc.execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource(System.getenv("WORKSPACE_TEST_JDBC")+"?currentSchema="+schema,System.getenv().getOrDefault("WORKSPACE_TEST_USER","workspace_test"),"");
        var context=new AnnotationConfigApplicationContext();
        try {
            new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__add_prediction_workspace.sql")).execute(ds);
            context.registerBean(DataSource.class,()->ds); context.register(Config.class); context.refresh();
            var store=context.getBean(WorkspaceStore.class); var jdbc=context.getBean(JdbcTemplate.class);
            var suggestion=map("id","suggestion","category","MEGA","targetDate","2099-01-02","tickets",List.of()); store.saveSuggestion(suggestion);
            assertEquals(suggestion,store.suggestion("suggestion"));
            var request=new Commit("MEGA","2099-01-02","suggestion",0,"same-request",List.of(),5); var builds=new AtomicInteger();
            java.util.function.Supplier<Map<String,Object>> build=()->{ builds.incrementAndGet(); return map("id","batch","category","MEGA","targetDate","2099-01-02","tickets",List.of(map("numbers",List.of(1,2,3,4,5,6))),"suggestion",suggestion,"frequencyTickets",List.of(List.of(1,2,7,8,9,10)),"randomTickets",List.of(List.of(1,11,12,13,14,15))); };
            var pool=Executors.newFixedThreadPool(2);
            try {
                var first=pool.submit(()->store.commit(request,build)); var second=pool.submit(()->store.commit(request,build));
                assertEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            } finally { pool.shutdownNow(); }
            assertEquals(1,builds.get()); assertEquals(1,store.revision("MEGA","2099-01-02"));
            assertThrows(Exception.class,()->store.commit(new Commit("MEGA","2099-01-02","suggestion",0,"different-request",List.of(),5),build));
            assertThrows(Exception.class,()->store.commit(new Commit("MEGA","2099-01-02","suggestion",1,"same-request",List.of(),5),build));
            // Same batch PK forces insertion failure after validation. The revision remains intact.
            assertThrows(Exception.class,()->store.commit(new Commit("MEGA","2099-01-02","suggestion",1,"rollback-request",List.of(),5),build));
            assertEquals(1,store.revision("MEGA","2099-01-02"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM prediction_user_batches",Integer.class));
            var draw=TestDraws.verified(new com.example.analyzeproject.model.LotteryNumber(LocalDate.of(2099,1,2),"MEGA",List.of(1,2,3,30,31,32),null,null));
            store.reconcile(List.of(draw),Instant.parse("2099-01-02T12:00:00Z")); store.reconcile(List.of(draw),Instant.parse("2099-01-02T12:01:00Z"));
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM prediction_user_settlements",Integer.class));
            draw.setNumbers(List.of(1,2,3,4,5,6)); store.reconcile(List.of(draw),Instant.parse("2099-01-02T12:02:00Z"));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM prediction_user_settlements",Integer.class));
            var rows=(List<?>)store.history("MEGA").get("batches"); var settlement=(Map<?,?>)((Map<?,?>)rows.get(0)).get("settlement");
            assertEquals("RESULT_UNAVAILABLE",settlement.get("status"));
            // A new store instance reads the same persisted snapshot and batch.
            assertEquals(1,new WorkspaceStore(jdbc).revision("MEGA","2099-01-02"));
        } finally { context.close(); adminJdbc.execute("DROP SCHEMA "+schema+" CASCADE"); }
    }
}
