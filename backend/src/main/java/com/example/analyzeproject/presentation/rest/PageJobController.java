package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.port.out.PageJobStore.Job;
import com.example.analyzeproject.application.service.PageJobService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/page-jobs")
public class PageJobController {
    private final PageJobService jobs;
    public PageJobController(PageJobService jobs){this.jobs=jobs;}
    public static ResponseEntity<Job> response(Job job) {
        if(job==null)return ResponseEntity.notFound().build();
        boolean pending=job.status().equals("QUEUED")||job.status().equals("RUNNING");
        return ResponseEntity.status(pending?202:200).cacheControl(CacheControl.noStore())
                .header("Retry-After",pending?"2":"0").body(job);
    }
    @GetMapping("/{id}") public ResponseEntity<Job> status(@PathVariable String id){return response(jobs.find(id));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(){return ResponseEntity.badRequest().body(Map.of("message","Mã tác vụ không hợp lệ."));}
}
