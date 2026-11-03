package com.example.analyzeproject.presentation.rest;

import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/stocks")
public class StockController {
    private final StockService stocks;
    public StockController(StockService stocks){this.stocks=stocks;}
    @GetMapping("/instruments") public Object instruments(){return stocks.instruments();}
    @GetMapping("/watchlist") public Object watchlist(){return stocks.watchlist();}
    @GetMapping("/{symbol}/analysis") public Snapshot analyze(@PathVariable String symbol,@RequestParam(defaultValue="SWING") Mode mode){return stocks.analyze(symbol,mode);}
    @PostMapping("/{symbol}/position-analysis") public ResponseEntity<PositionResult> position(@PathVariable String symbol,@RequestParam String snapshotId,@RequestBody PositionInput request){return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(stocks.position(symbol,snapshotId,request));}
    @ExceptionHandler(IllegalArgumentException.class) public ResponseEntity<?> invalid(IllegalArgumentException e){return ResponseEntity.badRequest().body(Map.of("message",e.getMessage()));}
}
