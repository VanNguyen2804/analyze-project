package com.example.analyzeproject.infrastructure.scheduling;

import com.example.analyzeproject.application.port.out.PageJobStore;
import com.example.analyzeproject.application.exception.ServiceBusyException;
import java.time.*;
import java.util.*;

/** Bounded, process-local stock queue. No market feed or position input is persisted. */
public final class LocalPageJobStore implements PageJobStore {
    private static final class Entry {
        String id,key,kind,status="QUEUED",token;Map<String,Object> input;Object data;Instant updated,expires;int ttl;
        Job view(){return new Job(id,status,data,"FAILED".equals(status)?"Không hoàn tất tác vụ. Hãy thử lại.":null,updated);}
    }
    private final LinkedHashMap<String,Entry> entries=new LinkedHashMap<>();
    private void expireQueued(Instant now){for(var e:entries.values())if("QUEUED".equals(e.status)&&e.updated.isBefore(now.minusSeconds(600))){e.status="FAILED";e.updated=now;}}
    @Override public synchronized Job enqueue(String key,String kind,Map<String,Object> input,int ttl,Instant now) {
        expireQueued(now);
        entries.values().removeIf(e->Set.of("SUCCEEDED","FAILED").contains(e.status)&&e.updated.isBefore(now.minusSeconds(600)));
        for(var e:entries.values())if(e.key.equals(key)&&(Set.of("QUEUED","RUNNING").contains(e.status)||"SUCCEEDED".equals(e.status)&&now.isBefore(e.expires)))return e.view();
        if(entries.values().stream().filter(e->Set.of("QUEUED","RUNNING").contains(e.status)).count()>=16)throw new ServiceBusyException("Bộ phân tích đang bận. Hãy thử lại sau ít giây.");
        while(entries.size()>=64){var removable=entries.values().stream().filter(e->Set.of("SUCCEEDED","FAILED").contains(e.status)).findFirst();if(removable.isEmpty())break;entries.remove(removable.get().id);}
        var e=new Entry();e.id="s-"+UUID.randomUUID();e.key=key;e.kind=kind;e.input=Map.copyOf(input);e.ttl=ttl;e.updated=now;entries.put(e.id,e);return e.view();
    }
    @Override public synchronized Job find(String id){var e=entries.get(id);return e==null?null:e.view();}
    @Override public synchronized Work claim(Instant now) {
        expireQueued(now);
        var next=entries.values().stream().filter(e->"QUEUED".equals(e.status)).min(Comparator.comparingInt(e->"STOCK_ANALYSIS".equals(e.kind)?0:1));
        if(next.isEmpty())return null;
        var e=next.get();e.status="RUNNING";e.updated=now;e.token=UUID.randomUUID().toString();return new Work(e.id,e.kind,e.input,e.token);
    }
    @Override public synchronized void complete(Work work,Object result,Instant now){var e=entries.get(work.id());if(e!=null&&"RUNNING".equals(e.status)&&Objects.equals(e.token,work.token())){e.status="SUCCEEDED";e.token=null;e.data=result;e.updated=now;e.expires=now.plusSeconds(e.ttl);}}
    @Override public synchronized void fail(Work work,Instant now){var e=entries.get(work.id());if(e!=null&&"RUNNING".equals(e.status)&&Objects.equals(e.token,work.token())){e.status="FAILED";e.token=null;e.updated=now;}}
    @Override public void heartbeat(Work work,Instant now) {}
}
