package com.example.analyzeproject.infrastructure.marketdata;
import com.example.analyzeproject.application.port.out.StockMarketDataPort;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
/** Durable cache retains original observation time; fallback data cannot confirm trades. */
public final class PersistentStockMarketData implements StockMarketDataPort {
 private final StockMarketDataPort provider;private final JdbcTemplate db;private final ObjectMapper json;private final Clock clock;private final TransactionTemplate tx;
 public PersistentStockMarketData(StockMarketDataPort provider,JdbcTemplate db,ObjectMapper json,Clock clock,PlatformTransactionManager manager){this.provider=provider;this.db=db;this.json=json;this.clock=clock;this.tx=new TransactionTemplate(manager);}
 @Override public Feed load(String symbol,Mode mode){
  Feed saved=read(symbol,mode);
  if(saved!=null && saved.verified() && Duration.between(saved.fetchedAt(),clock.instant()).getSeconds()>=0 && Duration.between(saved.fetchedAt(),clock.instant()).getSeconds()<60)return saved;
  Feed fresh;
  try { fresh=provider.load(symbol,mode); } catch(RuntimeException e){if(saved!=null)return fallback(saved);throw e;}
  if(fresh.candles().isEmpty())return saved==null?fresh:fallback(saved);
  // Failed provider refreshes retain their old timestamp; never replace a newer durable copy.
  if(saved!=null && fresh.fetchedAt().isBefore(saved.fetchedAt()))return fallback(saved);
  try { persist(symbol,mode,fresh); } catch(RuntimeException e){System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,"Cannot persist stock candles: "+e.getClass().getSimpleName());}
  return fresh;
 }
 private Feed read(String symbol,Mode mode){
  var rows=db.queryForList("SELECT feed_json FROM stock_feed_cache WHERE symbol=? AND timeframe=?",String.class,symbol,mode.name());
  if(rows.isEmpty())return null;
  try{return json.readValue(rows.get(0),Feed.class);}catch(Exception e){return null;}
 }
 private Feed fallback(Feed saved){var warnings=new ArrayList<>(saved.warnings());warnings.add("Đang dùng lịch sử đã lưu do nguồn chưa cập nhật; cần xác minh lại trước khi giao dịch.");return new Feed(saved.candles(),saved.fetchedAt(),saved.source(),saved.unit(),saved.priceBasis(),false,warnings);}
 private void persist(String symbol,Mode mode,Feed feed){
  try {
   String identity=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((feed.source()+"|"+feed.unit()+"|"+feed.priceBasis()+"|"+feed.verified()).getBytes(StandardCharsets.UTF_8)));
   String encoded=json.writeValueAsString(feed);
   tx.executeWithoutResult(status->{
    db.batchUpdate("INSERT INTO stock_candle_history(symbol,timeframe,data_identity,candle_time,open_price,high_price,low_price,close_price,volume,closed,source,unit,price_basis,verified,fetched_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(symbol,timeframe,data_identity,candle_time) DO UPDATE SET open_price=EXCLUDED.open_price,high_price=EXCLUDED.high_price,low_price=EXCLUDED.low_price,close_price=EXCLUDED.close_price,volume=EXCLUDED.volume,closed=EXCLUDED.closed,fetched_at=EXCLUDED.fetched_at WHERE EXCLUDED.fetched_at >= stock_candle_history.fetched_at",feed.candles(),500,(ps,c)->{
     ps.setString(1,symbol);ps.setString(2,mode.name());ps.setString(3,identity);ps.setLong(4,c.time());ps.setDouble(5,c.open());ps.setDouble(6,c.high());ps.setDouble(7,c.low());ps.setDouble(8,c.close());ps.setDouble(9,c.volume());ps.setBoolean(10,c.closed());ps.setString(11,feed.source());ps.setString(12,feed.unit());ps.setString(13,feed.priceBasis());ps.setBoolean(14,feed.verified());ps.setObject(15,feed.fetchedAt().atOffset(ZoneOffset.UTC));
    });
    db.update("INSERT INTO stock_feed_cache(symbol,timeframe,feed_json) VALUES (?,?,?) ON CONFLICT(symbol,timeframe) DO UPDATE SET feed_json=EXCLUDED.feed_json",symbol,mode.name(),encoded);
   });
  }catch(java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
 }
}
