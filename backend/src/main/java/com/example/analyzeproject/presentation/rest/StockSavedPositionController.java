package com.example.analyzeproject.presentation.rest;
import com.example.analyzeproject.domain.model.stock.StockModels.PositionInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.*;
@RestController
@RequestMapping("/api/stocks/saved-positions")
public class StockSavedPositionController {
 private final JdbcTemplate db; private final ObjectMapper json;
 public StockSavedPositionController(JdbcTemplate db,ObjectMapper json){this.db=db;this.json=json;}
 @GetMapping public ResponseEntity<Map<String,PositionInput>> all(){
  Map<String,PositionInput> result=new LinkedHashMap<>();
  db.query("SELECT symbol,input_json FROM stock_saved_positions",rs->{try{result.put(rs.getString(1),json.readValue(rs.getString(2),PositionInput.class));}catch(Exception e){throw new IllegalStateException("Cannot restore position",e);}});
  return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(result);
 }
 @PutMapping("/{symbol}") @Transactional public void save(@PathVariable String symbol,@RequestBody PositionInput input) throws Exception {
  if(!symbol.matches("[A-Z][A-Z0-9]{2,9}") || input.purchasePrice()==null || !Double.isFinite(input.purchasePrice()) || input.purchasePrice()<=0 || input.quantity()==null || !Double.isFinite(input.quantity()) || input.quantity()<=0 || input.quantity()%1!=0) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"Invalid position");
  String data=json.writeValueAsString(input);
  if(db.update("UPDATE stock_saved_positions SET input_json=? WHERE symbol=?",data,symbol)==0) db.update("INSERT INTO stock_saved_positions(symbol,input_json) VALUES (?,?)",symbol,data);
 }
}
