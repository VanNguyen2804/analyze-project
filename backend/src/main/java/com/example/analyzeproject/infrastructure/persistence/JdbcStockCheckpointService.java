package com.example.analyzeproject.infrastructure.persistence;
import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.application.service.StockCheckpointService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.*;
import java.util.*;
@Service
public class JdbcStockCheckpointService implements StockCheckpointService {
 private final StockService stocks; private final JdbcTemplate db; private final ObjectMapper json;
 private static final ZoneId VN=ZoneId.of("Asia/Ho_Chi_Minh");
 public JdbcStockCheckpointService(StockService stocks,JdbcTemplate db,ObjectMapper json){this.stocks=stocks;this.db=db;this.json=json;}
 @Scheduled(cron="30 15 9 * * MON-FRI",zone="Asia/Ho_Chi_Minh") public void opening(){capture("09:15");}
 @Scheduled(cron="30 30 11 * * MON-FRI",zone="Asia/Ho_Chi_Minh") public void midday(){capture("11:30");}
 @Scheduled(cron="30 15 14 * * MON-FRI",zone="Asia/Ho_Chi_Minh") public void afternoon(){capture("14:15");}
 @Scheduled(initialDelay=30000, fixedDelay=60000)
 public void retryDue(){
  var now=ZonedDateTime.now(VN);
  if(now.getDayOfWeek().getValue()>5)return;
  for(String slot:List.of("09:15","11:30","14:15"))
   if(!now.toLocalTime().isBefore(LocalTime.parse(slot).plusSeconds(30)))capture(slot);
 }
 public synchronized void capture(String checkpoint){
  String day=LocalDate.now(VN).toString();
  Set<String> symbols=new LinkedHashSet<>();symbols.add("VIC");
  symbols.addAll(db.queryForList("SELECT symbol FROM stock_saved_positions",String.class));
  for(String symbol:symbols) captureSymbol(symbol,day,checkpoint);
 }
 private synchronized void captureSymbol(String symbol,String day,String checkpoint){
  try {
   String key=day+":"+checkpoint+":"+symbol;
   if(db.queryForObject("SELECT COUNT(*) FROM stock_checkpoint_reports WHERE report_key=?",Integer.class,key)>0)return;
   Snapshot swing=stocks.analyze(symbol,Mode.SWING), intraday=stocks.analyze(symbol,Mode.INTRADAY);
   Map<String,Object> report=new LinkedHashMap<>();
   report.put("symbol",symbol);report.put("day",day);report.put("checkpoint",checkpoint);report.put("capturedAt",Instant.now().toString());
   report.put("lateCapture", LocalTime.now(VN).isAfter(LocalTime.parse(checkpoint).plusMinutes(2)));report.put("swing",swing);report.put("intraday",intraday);
   report.put("longTermNote","Dài hạn: tham khảo xu hướng nến ngày và EMA200; cần kết hợp dữ liệu doanh nghiệp. Chưa có mô hình dài hạn riêng.");
   var saved=db.queryForList("SELECT input_json FROM stock_saved_positions WHERE symbol=?",String.class,symbol);
   if(!saved.isEmpty()) { PositionInput input=json.readValue(saved.get(0),PositionInput.class);report.put("swingPosition",stocks.position(symbol,swing.snapshotId(),input));report.put("intradayPosition",stocks.position(symbol,intraday.snapshotId(),input)); }
   db.update("INSERT INTO stock_checkpoint_reports(report_key,symbol,report_day,checkpoint,report_json) VALUES (?,?,?,?,?)",key,symbol,day,checkpoint,json.writeValueAsString(report));
  } catch(Exception e){java.util.logging.Logger.getLogger(getClass().getName()).warning("Checkpoint "+checkpoint+" failed for "+symbol+": "+e.getMessage());}
 }
 public List<Object> reports(String symbol,String day){
  if(!symbol.matches("[A-Z][A-Z0-9]{2,9}"))throw new IllegalArgumentException("Invalid symbol");
  LocalDate.parse(day);
  var now=ZonedDateTime.now(VN);
  if(day.equals(now.toLocalDate().toString()) && now.getDayOfWeek().getValue()<=5)
   for(String slot:List.of("09:15","11:30","14:15"))
    if(!now.toLocalTime().isBefore(LocalTime.parse(slot).plusSeconds(30)))captureSymbol(symbol,day,slot);
  return db.query("SELECT report_json FROM stock_checkpoint_reports WHERE symbol=? AND report_day=? ORDER BY checkpoint",(rs,n)->{try{return json.readValue(rs.getString(1),Object.class);}catch(Exception e){throw new IllegalStateException(e);}},symbol,day);
 }
}
