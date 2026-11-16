package com.example.analyzeproject.presentation.rest;
import com.example.analyzeproject.application.service.StockTradingPlanService;
import com.example.analyzeproject.domain.model.stock.StockModels.PositionInput;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/stocks")
public class StockTradingPlanController {
 private final StockTradingPlanService service;
 public StockTradingPlanController(StockTradingPlanService service){this.service=service;}
 @PostMapping("/{symbol}/trading-plans") public Object plans(@PathVariable String symbol,@RequestBody PositionInput input,@RequestParam(defaultValue="SR_ATR") StockTradingPlanService.Calculation calculation){return service.plans(symbol,input,calculation);}
}
