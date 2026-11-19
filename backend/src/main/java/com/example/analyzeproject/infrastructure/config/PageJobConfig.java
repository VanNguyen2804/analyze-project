package com.example.analyzeproject.infrastructure.config;

import com.example.analyzeproject.application.port.out.PageJobStore;
import com.example.analyzeproject.application.service.*;
import com.example.analyzeproject.infrastructure.persistence.jdbc.JdbcPageJobStore;
import com.example.analyzeproject.infrastructure.scheduling.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;

@Configuration
public class PageJobConfig {
    @Bean("durablePageJobs") PageJobStore durablePageJobs(JdbcTemplate jdbc,ObjectMapper json,
            @Value("${app.jobs.capacity:64}") int capacity) {
        if(capacity<1||capacity>1000)throw new IllegalArgumentException("Job capacity must be 1..1000");
        return new JdbcPageJobStore(jdbc,json,capacity);
    }
    @Bean("localPageJobs") PageJobStore localPageJobs(){return new LocalPageJobStore();}
    @Bean PageJobService pageJobService(@Qualifier("durablePageJobs") PageJobStore durable,
            @Qualifier("localPageJobs") PageJobStore local,Clock clock){return new PageJobService(durable,local,clock);}
    @Bean PageJobTasks pageJobTasks(StockService stocks,AiLabService lab,WorkspaceService workspace,Clock clock,
            com.example.analyzeproject.infrastructure.sync.LotteryFeedClient feed,
            LotteryResultImporter importer,
            com.example.analyzeproject.infrastructure.sync.LotterySyncProperties properties){
        return new PageJobTasks(stocks,lab,workspace,clock,input -> {
            String category=(String)input.get("category");
            var date=java.time.LocalDate.parse((String)input.get("drawDate"));
            try {
                var fetched=feed.fetchWithSource(category,date,java.time.ZonedDateTime.now(clock));
                var matching=fetched.draws().stream().filter(draw -> draw.date().equals(date)).toList();
                if(matching.isEmpty())throw new IllegalStateException("Official result not published for " + date);
                var result=importer.importDraws(matching,fetched.sourceUrl());
                if(!result.conflicts().isEmpty())throw new IllegalStateException("Official result conflicts with saved numbers");
            } catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException(error);}
            catch(java.io.IOException error){throw new IllegalStateException("Cannot verify official result",error);}
        });
    }
    @Bean(destroyMethod="close") @ConditionalOnProperty(name="app.jobs.worker-enabled",havingValue="true",matchIfMissing=true)
    PageJobRunner durablePageJobRunner(@Qualifier("durablePageJobs") PageJobStore store,PageJobTasks tasks,Clock clock,
            @Value("${app.jobs.concurrency:1}") int concurrency){return new PageJobRunner(store,tasks,clock,concurrency,"lottery-page-worker");}
    @Bean(destroyMethod="close") @ConditionalOnProperty(name="app.jobs.stock-enabled",havingValue="true",matchIfMissing=true)
    PageJobRunner stockPageJobRunner(@Qualifier("localPageJobs") PageJobStore store,PageJobTasks tasks,Clock clock){return new PageJobRunner(store,tasks,clock,1,"stock-page-worker");}
}
