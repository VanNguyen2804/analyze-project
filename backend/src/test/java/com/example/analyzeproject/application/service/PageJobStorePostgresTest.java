package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.PageJobStore;
import com.example.analyzeproject.infrastructure.persistence.jdbc.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

/** Actual PostgreSQL, isolated schema: claim fencing, bounded enqueue and projection invalidation. */
@EnabledIfEnvironmentVariable(named="WORKSPACE_TEST_JDBC",matches=".+")
class PageJobStorePostgresTest {
    @Configuration @EnableTransactionManagement
    static class Config {
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean PlatformTransactionManager transactionManager(DataSource ds){return new DataSourceTransactionManager(ds);}
        @Bean PageJobStore jobs(JdbcTemplate jdbc){return new JdbcPageJobStore(jdbc,new ObjectMapper().findAndRegisterModules(),2);}
        @Bean JdbcAiLabStore lab(JdbcTemplate jdbc){return new JdbcAiLabStore(jdbc);}
    }
    @Test void deduplicationRecoveryFencingAndCompactProjectionSurviveReload() throws Exception {
        String schema="page_jobs_it_"+UUID.randomUUID().toString().replace("-","");
        String url=System.getenv("WORKSPACE_TEST_JDBC"),user=System.getenv().getOrDefault("WORKSPACE_TEST_USER","workspace_test");
        var admin=new JdbcTemplate(new DriverManagerDataSource(url,user,""));admin.execute("CREATE SCHEMA "+schema);
        var ds=new DriverManagerDataSource(url+"?currentSchema="+schema,user,"");
        try(var context=new AnnotationConfigApplicationContext()) {
            var jdbc=new JdbcTemplate(ds);
            jdbc.execute("CREATE TABLE lottery_numbers(id bigint);CREATE TABLE lottery_selected_numbers(id bigint);CREATE TABLE algorithm_hyperparameters(id bigint)");
            for(String file:List.of("V12__add_ai_lab.sql","V16__add_page_jobs.sql","V17__add_lab_read_projection.sql"))
                jdbc.execute(new ClassPathResource("db/migration/"+file).getContentAsString(StandardCharsets.UTF_8));
            context.registerBean(DataSource.class,()->ds);context.register(Config.class);context.refresh();
            var store=context.getBean(PageJobStore.class);Instant now=Instant.parse("2026-11-08T10:00:00Z");
            var pool=Executors.newFixedThreadPool(4);
            try {
                var requests=new ArrayList<Future<PageJobStore.Job>>();
                for(int i=0;i<4;i++)requests.add(pool.submit(()->store.enqueue("same","LAB_OVERVIEW",Map.of("category","MEGA"),30,now)));
                var ids=new HashSet<String>();for(var f:requests)ids.add(f.get(5,TimeUnit.SECONDS).id());assertEquals(1,ids.size());
            } finally {pool.shutdownNow();}
            var original=store.claim(now);assertNotNull(original);assertNull(store.claim(now));
            var recovered=store.claim(now.plusSeconds(61));assertEquals(original.id(),recovered.id());assertNotEquals(original.token(),recovered.token());
            store.complete(original,Map.of("wrong",true),now.plusSeconds(62));assertEquals("RUNNING",store.find(original.id()).status());
            store.complete(recovered,Map.of("correct",true),now.plusSeconds(63));
            assertEquals(Map.of("correct",true),new JdbcPageJobStore(jdbc,new ObjectMapper(),2).find(original.id()).data());
            var mutation=store.enqueue("write","ANALYZE",Map.of(),30,now.plusSeconds(64));
            store.claim(now.plusSeconds(64));assertNull(store.claim(now.plusSeconds(125)));
            assertEquals("FAILED",store.find(mutation.id()).status()); // no implicit replay of a write
            assertNotEquals(original.id(),store.enqueue("same","LAB_OVERVIEW",Map.of(),30,now.plusSeconds(100)).id());

            var lab=context.getBean(com.example.analyzeproject.application.port.out.AiLabStore.class);
            jdbc.update("INSERT INTO ai_lab_snapshots(id,snapshot_json) VALUES (?,?)","MEGA:2026-11-08:invalid","{\"id\":\"MEGA:2026-11-08:invalid\",\"category\":\"MEGA\",\"targetDate\":\"2026-11-08\",\"numbers\":[1,2,3,4,5,6]}");
            String revision=store.revision();var view=lab.readOverview("MEGA");assertEquals(1,view.forecasts().size());assertFalse(view.forecasts().get(0).prospective());
            assertEquals(revision,store.revision(),"backfill of a read projection must not invalidate the source revision");
            assertNotNull(jdbc.queryForObject("SELECT read_projection_json FROM ai_lab_snapshots",String.class));
            jdbc.update("UPDATE ai_lab_snapshots SET snapshot_json=replace(snapshot_json,'1,2,3,4,5,6','2,3,4,5,6,7')");
            assertNull(jdbc.queryForObject("SELECT read_projection_json FROM ai_lab_snapshots",String.class));assertNotEquals(revision,store.revision());
            var reread=lab.readOverview("MEGA");assertEquals(List.of(2,3,4,5,6,7),reread.forecasts().get(0).data().get("numbers"));
            jdbc.update("INSERT INTO ai_lab_snapshots(id,snapshot_json) VALUES (?,?)","POWER:2026-11-08:invalid","{\"category\":\"MEGA\",\"numbers\":[1,2,3,4,5,6]}");
            lab.readOverview("POWER");
            assertEquals(List.of("MEGA:2026-11-08:invalid"),lab.pageIds("MEGA",0).orElseThrow());
            assertTrue(lab.pageIds("MEGA",1).orElseThrow().isEmpty());
            assertEquals(1,lab.snapshotsForTarget("MEGA","2026-11-08").size());assertTrue(lab.snapshotsForTarget("POWER","2026-11-08").isEmpty());
        } finally {admin.execute("DROP SCHEMA "+schema+" CASCADE");}
    }
}
