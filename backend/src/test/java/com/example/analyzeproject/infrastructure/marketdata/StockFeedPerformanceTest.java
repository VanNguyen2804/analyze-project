package com.example.analyzeproject.infrastructure.marketdata;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StockFeedPerformanceTest {
    @SuppressWarnings("unchecked") private HttpResponse<String> response(int status,String json) {
        var response=(HttpResponse<String>)mock(HttpResponse.class);when(response.statusCode()).thenReturn(status);when(response.body()).thenReturn(json);return response;
    }
    private final String empty="{\"t\":[],\"o\":[],\"h\":[],\"l\":[],\"c\":[],\"v\":[]}";
    @Test void sourceCallsArePerKeyAndDuplicateRequestsShareOneFetch() throws Exception {
        var client=mock(HttpClient.class);var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(client.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenAnswer(call->{
            HttpRequest request=call.getArgument(0);assertTrue(request.timeout().orElseThrow().compareTo(Duration.ofSeconds(5))<=0);
            if(request.uri().getQuery().contains("symbol=FPT")){entered.countDown();assertTrue(release.await(4,TimeUnit.SECONDS));}
            return response(200,empty);
        });
        var adapter=new EntradeStockAdapter(new ObjectMapper(),Clock.systemUTC(),true,1000,"RAW",true,client);var pool=Executors.newFixedThreadPool(3);
        try {
            var first=pool.submit(()->adapter.load("FPT",Mode.SWING));assertTrue(entered.await(2,TimeUnit.SECONDS));
            var duplicate=pool.submit(()->adapter.load("FPT",Mode.SWING));
            assertNotNull(pool.submit(()->adapter.load("MBB",Mode.SWING)).get(2,TimeUnit.SECONDS));
            release.countDown();assertSame(first.get(2,TimeUnit.SECONDS),duplicate.get(2,TimeUnit.SECONDS));
            verify(client,times(2)).send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any());
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void lightweightQuotesDoNotReplaceFullHistoryAndFailuresKeepOriginalTimestamp() throws Exception {
        var client=mock(HttpClient.class);var now=new AtomicReference<>(Instant.parse("2026-11-07T01:00:00Z"));
        var clock=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return now.get();}};
        String json="{\"t\":["+now.get().minusSeconds(86400).getEpochSecond()+"],\"o\":[10],\"h\":[11],\"l\":[9],\"c\":[10],\"v\":[100]}";
        List<HttpRequest> requests=new ArrayList<>();when(client.send(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<String>>any())).thenAnswer(call->{
            requests.add(call.getArgument(0));return response(requests.size()>2?503:200,json);
        });
        var adapter=new EntradeStockAdapter(new ObjectMapper(),clock,true,1000,"RAW",true,client);
        adapter.quote("FPT");var original=adapter.load("FPT",Mode.SWING);assertEquals(2,requests.size());
        Map<String,Long> query=new HashMap<>();for(String part:requests.get(0).uri().getQuery().split("&")){var item=part.split("=");if(Set.of("from","to").contains(item[0]))query.put(item[0],Long.parseLong(item[1]));}
        assertTrue(query.get("to")-query.get("from")<=31*86400L);
        now.set(now.get().plusSeconds(61));var failed=adapter.load("FPT",Mode.SWING);
        assertEquals(original.candles(),failed.candles());assertEquals(original.fetchedAt(),failed.fetchedAt());assertFalse(failed.verified());
        adapter.load("FPT",Mode.SWING);assertEquals(3,requests.size());
    }
}
