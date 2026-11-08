package com.example.analyzeproject.infrastructure.scheduling;

import com.example.analyzeproject.application.port.out.PageJobStore;
import com.example.analyzeproject.application.service.PageJobTasks;
import java.time.*;
import java.util.concurrent.*;

/** Dedicated workers and heartbeats do not occupy the Spring scheduling thread or servlet threads. */
public final class PageJobRunner implements AutoCloseable {
    private final ExecutorService workers;
    private final ScheduledExecutorService heartbeat;
    private volatile boolean closed;
    public PageJobRunner(PageJobStore store,PageJobTasks tasks,Clock clock,int concurrency,String name) {
        if(concurrency<1||concurrency>4)throw new IllegalArgumentException("Worker concurrency must be 1..4");
        workers=Executors.newFixedThreadPool(concurrency,r->{var t=new Thread(r,name);t.setDaemon(true);return t;});
        heartbeat=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,name+"-lease");t.setDaemon(true);return t;});
        for(int i=0;i<concurrency;i++)workers.submit(()->{
            while(!closed&&!Thread.currentThread().isInterrupted()) {
                try {
                    var work=store.claim(clock.instant());
                    if(work==null){Thread.sleep(2000);continue;}
                    long started=System.nanoTime();
                    Thread executing=Thread.currentThread();
                    var deadline=heartbeat.schedule(()->{
                        try{store.fail(work,clock.instant());}finally{executing.interrupt();}
                    },600,TimeUnit.SECONDS);
                    var lease=heartbeat.scheduleAtFixedRate(()->{
                        try{store.heartbeat(work,clock.instant());}catch(Exception error){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Job heartbeat failed: "+work.id());}
                    },15,15,TimeUnit.SECONDS);
                    try {
                        var result=tasks.execute(work);
                        store.complete(work,result,clock.instant());
                        System.getLogger(getClass().getName()).log(System.Logger.Level.INFO,"Page job "+work.kind()+" completed in "+TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started)+" ms");
                    }catch(Exception error){
                        store.fail(work,clock.instant());
                        System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Page job "+work.kind()+" failed",error);
                    }finally{lease.cancel(false);deadline.cancel(false);if(!closed)Thread.interrupted();}
                }catch(InterruptedException stop){Thread.currentThread().interrupt();}
                catch(Exception error){
                    System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Page worker unavailable",error);
                    try{Thread.sleep(5000);}catch(InterruptedException stop){Thread.currentThread().interrupt();}
                }
            }
        });
    }
    @Override public void close(){closed=true;workers.shutdownNow();heartbeat.shutdownNow();}
}
