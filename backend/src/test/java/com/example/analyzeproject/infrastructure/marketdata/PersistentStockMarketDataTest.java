package com.example.analyzeproject.infrastructure.marketdata;
import com.example.analyzeproject.application.port.out.StockMarketDataPort;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class PersistentStockMarketDataTest {
 private final Instant now=Instant.parse("2026-11-16T03:00:00Z");
 private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
 private Feed feed(Instant at){return new Feed(List.of(new Candle(now.minusSeconds(86400).getEpochSecond(),100,110,95,105,1000,true)),at,"test","VND","RAW",true,List.of());}
 @Test void freshDurableCacheAvoidsProviderRequest() throws Exception {
  var db=mock(JdbcTemplate.class);var provider=mock(StockMarketDataPort.class);
  when(db.queryForList("SELECT feed_json FROM stock_feed_cache WHERE symbol=? AND timeframe=?",String.class,"VIC","SWING")).thenReturn(List.of(json.writeValueAsString(feed(now.minusSeconds(20)))));
  var store=new PersistentStockMarketData(provider,db,json,Clock.fixed(now,ZoneOffset.UTC),mock(PlatformTransactionManager.class));
  assertTrue(store.load("VIC",Mode.SWING).verified());verifyNoInteractions(provider);
 }
 @Test void failedRefreshRetainsTimestampAndDisablesConfirmation() throws Exception {
  var db=mock(JdbcTemplate.class);var provider=mock(StockMarketDataPort.class);var original=feed(now.minusSeconds(3600));
  when(db.queryForList("SELECT feed_json FROM stock_feed_cache WHERE symbol=? AND timeframe=?",String.class,"VIC","SWING")).thenReturn(List.of(json.writeValueAsString(original)));
  when(provider.load("VIC",Mode.SWING)).thenThrow(new IllegalStateException("offline"));
  var store=new PersistentStockMarketData(provider,db,json,Clock.fixed(now,ZoneOffset.UTC),mock(PlatformTransactionManager.class));
  Feed restored=store.load("VIC",Mode.SWING);assertFalse(restored.verified());assertEquals(original.fetchedAt(),restored.fetchedAt());assertEquals(original.candles(),restored.candles());
 }
}
