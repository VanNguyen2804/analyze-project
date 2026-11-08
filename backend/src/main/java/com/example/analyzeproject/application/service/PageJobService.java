package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.PageJobStore;
import java.time.*;
import java.util.*;

/** HTTP-independent entry point shared by the three pages. No computation is done while enqueueing. */
public final class PageJobService {
    private final PageJobStore durable, local;
    private final Clock clock;
    private final String version = WorkspaceProtocol.codeVersion()+WorkspaceProtocol.fingerprint(PageJobService.class,PageJobTasks.class,AiLabService.class,com.example.analyzeproject.domain.service.AiLabModel.class);
    public PageJobService(PageJobStore durable, PageJobStore local, Clock clock) {
        this.durable=durable; this.local=local; this.clock=clock;
    }
    public PageJobStore.Job submit(String kind, Map<String,Object> input) {
        boolean stock=kind.startsWith("STOCK_");
        PageJobStore store=stock?local:durable;
        Instant now=clock.instant();
        String category=Objects.toString(input.get("category"),"MEGA");
        String target=stock?"":ForecastProtocol.target(category,now).toString();
        String key="page-v1:"+version+":"+store.revision()+":"+target+":"+kind+":"+new TreeMap<>(input);
        var captured=new TreeMap<String,Object>(input);
        if(kind.equals("ANALYZE"))captured.put("_target",target);
        return store.enqueue(key,kind,captured,kind.equals("ANALYZE")?300:30,now);
    }
    public PageJobStore.Job find(String id) {
        if(id==null||!id.matches("[sl]-[a-f0-9-]{36}"))throw new IllegalArgumentException("Mã tác vụ không hợp lệ.");
        return (id.startsWith("s-")?local:durable).find(id);
    }
}
