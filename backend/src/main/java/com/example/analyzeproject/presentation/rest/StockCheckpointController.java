package com.example.analyzeproject.presentation.rest;
import com.example.analyzeproject.application.service.StockCheckpointService;
import org.springframework.web.bind.annotation.*;
import java.time.*;
@RestController
@RequestMapping("/api/stocks")
public class StockCheckpointController {
 private final StockCheckpointService service;
 public StockCheckpointController(StockCheckpointService service){this.service=service;}
 @GetMapping("/{symbol}/checkpoints") public Object reports(@PathVariable String symbol,@RequestParam(required=false) String day){return service.reports(symbol,day==null?LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString():day);}
}
