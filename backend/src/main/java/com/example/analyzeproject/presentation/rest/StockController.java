package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.application.service.PageJobService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/stocks")
public class StockController {
    private final StockService stocks;
    private final PageJobService jobs;
    public StockController(StockService stocks,PageJobService jobs){this.stocks=stocks;this.jobs=jobs;}
    @GetMapping("/instruments") public Object instruments(){return stocks.instruments();}
    @GetMapping("/watchlist") public Object watchlist(@RequestParam(defaultValue="false") boolean async){return async?PageJobController.response(jobs.submit("STOCK_WATCHLIST",Map.of())):stocks.watchlist();}
    @GetMapping("/{symbol}/analysis") public Object analyze(@PathVariable String symbol,@RequestParam(defaultValue="SWING") Mode mode,@RequestParam(defaultValue="false") boolean async){
        if(stocks.instruments().stream().noneMatch(i->i.get("symbol").equals(symbol)))throw new IllegalArgumentException("Mã không hỗ trợ");
        return async?PageJobController.response(jobs.submit("STOCK_ANALYSIS",Map.of("symbol",symbol,"mode",mode.name()))):stocks.analyze(symbol,mode);
    }
    @PostMapping("/{symbol}/position-analysis") public ResponseEntity<PositionResult> position(@PathVariable String symbol,@RequestParam String snapshotId,@RequestBody PositionInput request){return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(stocks.position(symbol,snapshotId,request));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
