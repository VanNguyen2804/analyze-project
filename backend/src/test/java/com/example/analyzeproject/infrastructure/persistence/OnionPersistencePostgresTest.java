package com.example.analyzeproject.infrastructure.persistence;

import com.example.analyzeproject.application.port.out.*;
import com.example.analyzeproject.application.service.*;
import com.example.analyzeproject.domain.model.*;
import com.example.analyzeproject.infrastructure.config.ApplicationConfig;
import com.example.analyzeproject.infrastructure.persistence.entity.LotteryNumberEntity;
import com.example.analyzeproject.infrastructure.persistence.jdbc.*;
import com.example.analyzeproject.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.jdbc.*;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.io.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Validates the new adapters against the pre-refactor schema; Hibernate may not change it. */
@EnabledIfEnvironmentVariable(named = "WORKSPACE_TEST_JDBC", matches = ".+")
class OnionPersistencePostgresTest {
    @Configuration(proxyBeanMethods = false)
    @EntityScan(basePackageClasses = LotteryNumberEntity.class)
    @EnableJpaRepositories(basePackageClasses = SpringDataLotteryNumberRepository.class)
    @Import({ApplicationConfig.class, JpaLotteryNumberAdapter.class, JpaUserTicketAdapter.class,
            JpaAlgorithmHyperparameterAdapter.class, JpaLotteryDeviationVariableAdapter.class,
            JdbcForecastStore.class, JdbcAiLabStore.class, JdbcWorkspaceStore.class, JdbcMegaPortfolioStore.class})
    static class Config {}

    @Test void originalSchemaAndTransactionsWorkThroughApplicationPorts() throws Exception {
        String schema = "onion_it_" + UUID.randomUUID().toString().replace("-", "");
        String url = System.getenv("WORKSPACE_TEST_JDBC");
        String user = System.getenv().getOrDefault("WORKSPACE_TEST_USER", "workspace_test");
        var admin = new JdbcTemplate(new DriverManagerDataSource(url, user, ""));
        admin.execute("CREATE SCHEMA " + schema);
        String schemaUrl = url + "?currentSchema=" + schema;
        try {
            var schemaDataSource = new DriverManagerDataSource(schemaUrl, user, "");
            // Use only the original migrations' DDL; do not import historical seed data.
            var setup = new ResourceDatabasePopulator();
            for (String name : List.of("V1__init_database_schema.sql", "V3__create_lottery_deviation_variables_table.sql")) {
                String ddl = new ClassPathResource("db/migration/" + name).getContentAsString(StandardCharsets.UTF_8).split("INSERT INTO", 2)[0];
                setup.addScript(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
            }
            for (String name : List.of("V10__add_draw_quality_audit.sql", "V11__add_forecast_ledger.sql", "V12__add_ai_lab.sql",
                    "V13__add_prediction_workspace.sql", "V14__add_mega_portfolio_experiment.sql", "V15__version_mega_portfolio_batches.sql"))
                setup.addScript(new ClassPathResource("db/migration/" + name));
            setup.execute(schemaDataSource);
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
                            JdbcTemplateAutoConfiguration.class, TransactionAutoConfiguration.class,
                            org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class))
                    .withUserConfiguration(Config.class)
                    .withPropertyValues("spring.datasource.url=" + schemaUrl, "spring.datasource.username=" + user,
                            "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false")
                    .run(context -> {
                        assertNull(context.getStartupFailure(), () -> Objects.toString(context.getStartupFailure()));
                        for (Class<?> service : List.of(AnalyzeService.class, ForecastService.class, AiLabService.class,
                                WorkspaceService.class, MegaPortfolioService.class, PredictionResearchService.class)) assertNotNull(context.getBean(service));
                        var importer = context.getBean(LotteryResultImporter.class);
                        assertTrue(AopUtils.isAopProxy(importer));
                        assertTrue(AopUtils.isAopProxy(context.getBean(DrawAnalysisService.class)));
                        var draws = context.getBean(LotteryNumberRepository.class);
                        var date = LocalDate.of(2026, 10, 29);
                        var input = List.of(new FeedDraw("POWER", date, "test", List.of(1,2,3,4,5,6), 7));
                        assertEquals(1, importer.importDraws(input, "https://example.test/feed").inserted());
                        assertEquals(1, importer.importDraws(input, "https://example.test/feed").unchanged());
                        var restored = draws.findByDrawDateAndCategoryOrderByCreatedAtDesc(date,"POWER").get(0);
                        assertNotNull(restored.getId()); assertEquals(7, restored.getSpecialNumber());
                        assertEquals(List.of(1,2,3,4,5,6), restored.getNumbers());
                        assertEquals("SOURCE_MATCHED", restored.getQuality().get("status"));
                        assertEquals(1, ((List<?>)restored.getQuality().get("audit")).size());
                        var tx = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                        assertThrows(IllegalStateException.class, () -> tx.execute(status -> {
                            importer.importDraws(List.of(new FeedDraw("MEGA", date, "rollback", List.of(1,2,3,4,5,6), null)), "https://example.test/feed");
                            throw new IllegalStateException("rollback");
                        }));
                        assertTrue(draws.findByCategoryOrderByDrawDateDescCreatedAtDesc("MEGA").isEmpty());
                        var tickets = context.getBean(UserTicketRepository.class);
                        var ticket = new UserTicket(); ticket.setCategory("POWER"); ticket.setDrawDate(date.toString());
                        ticket.setNumbers(List.of(1,2,3,4,5,6)); ticket.setCheckedAt(LocalDateTime.of(2026,10,29,20,0));
                        var saved = tickets.save(ticket);
                        assertEquals(new HashSet<>(ticket.getNumbers()), new HashSet<>(tickets.findById(saved.getId()).orElseThrow().getNumbers()));
                        var parameters = context.getBean(AlgorithmHyperparameterRepository.class);
                        var parameter = parameters.save(new AlgorithmHyperparameter("test",date.toString(),"POWER","model","{}","readme","note"));
                        assertEquals("readme",parameters.findById(parameter.getId()).orElseThrow().getReadmeContent());
                        var deviations = context.getBean(LotteryDeviationVariableRepository.class);
                        deviations.save(new LotteryDeviationVariable("POWER","2026-10-27",date.toString(),1,2,1,"NEIGHBOR_DRIFT","pattern",0.2,"rule","note"));
                        assertEquals(1,deviations.findByCategoryOrderByCreatedAtDesc("POWER").size());
                        var report = context.getBean(DrawAnalysisService.class).report("POWER",date.toString(),"temporal_learning",null);
                        assertEquals("SOURCE_MATCHED",report.draw().quality());
                        assertEquals(1,draws.count());
                    });
        } finally {
            admin.execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }
}
