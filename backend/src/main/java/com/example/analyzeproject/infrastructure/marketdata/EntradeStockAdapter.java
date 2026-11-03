package com.example.analyzeproject.infrastructure.marketdata;

import com.example.analyzeproject.application.port.out.StockMarketDataPort;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

/** No authentication, browser impersonation, persistent data storage or automatic provider fallback. */
public final class EntradeStockAdapter implements StockMarketDataPort {
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json;
    private final Clock clock;
    private final double scale;
    private final boolean verified,enabled;
    private final String basis;
    private final Map<String,Entry> cache=new HashMap<>();
    private record Entry(Feed feed, Instant retryAfter) {}
    public EntradeStockAdapter(ObjectMapper json,Clock clock,boolean enabled,double scale,String basis,boolean verified){
        if(!Double.isFinite(scale)||scale<=0||!Set.of("UNKNOWN","RAW","ADJUSTED").contains(basis))throw new IllegalArgumentException("Cấu hình nguồn không hợp lệ");
        this.json=json;this.clock=clock;this.enabled=enabled;this.scale=scale;this.basis=basis;this.verified=verified;
    }
    @Override public synchronized Feed load(String symbol,Mode mode){
        if(!Set.of("VIC","FPT","SZC","MBB","VNINDEX").contains(symbol))throw new IllegalArgumentException("Mã không hỗ trợ");
        String key=symbol+mode;Instant now=clock.instant();Entry old=cache.get(key);
        if(old!=null&&now.isBefore(old.retryAfter()))return old.feed();
        if(!enabled)return new Feed(List.of(),now,"DNSE / Entrade","SOURCE","UNKNOWN",false,List.of("Nguồn chưa được bật. Cần xác minh quyền sử dụng và cấu hình nguồn trước khi sử dụng live."));
        long cooldown=60;
        try{
            long from=now.minus(Duration.ofDays(mode==Mode.SWING?1826:60)).getEpochSecond(),to=now.getEpochSecond();
            TreeMap<Long,Candle> bars=new TreeMap<>();List<String> warnings=new ArrayList<>();
            if(!verified)warnings.add("Chưa xác minh đầy đủ đơn vị, cơ sở giá, timestamp và lịch sàn; khóa tín hiệu hành động và tính lãi/lỗ VND.");
            warnings.add("Nguồn công khai thực nghiệm; độ trễ chưa xác định. Chưa xác nhận quyền tái phân phối; chỉ cache tạm trong bộ nhớ.");
            // Provider limits are undocumented. Bounded time windows; never follow arbitrary URLs.
            long step=mode==Mode.SWING?to-from:10*86400L;
            for(long a=from;a<to;a+=step){
                long b=Math.min(to,a+step-1);
                URI uri=URI.create("https://services.entrade.com.vn/chart-api/v2/ohlcs/"+(symbol.equals("VNINDEX")?"index":"stock")+"?symbol="+symbol+"&resolution="+(mode==Mode.SWING?"1D":"15")+"&from="+a+"&to="+b);
                HttpResponse<String> response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()==429){
                    String retry=response.headers().firstValue("Retry-After").orElse("60");
                    try{cooldown=Math.max(60,Long.parseLong(retry));}catch(NumberFormatException e){
                        try{cooldown=Math.max(60,Duration.between(now,ZonedDateTime.parse(retry,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds());}catch(Exception ignored){cooldown=60;}
                    }
                }
                if(response.statusCode()!=200)throw new IllegalStateException("Nguồn trả HTTP "+response.statusCode());
                if(response.body().length()>4_000_000)throw new IllegalStateException("Phản hồi nguồn quá lớn");
                JsonNode root=json.readTree(response.body());int n=root.path("t").size();
                for(String field:List.of("t","o","h","l","c","v"))if(!root.path(field).isArray()||root.path(field).size()!=n)throw new IllegalStateException("Mảng OHLCV không khớp");
                for(int i=0;i<n;i++){
                    long t=root.path("t").get(i).asLong();if(t<a||t>b||t>to)throw new IllegalStateException("Timestamp ngoài phạm vi");
                    double unit=symbol.equals("VNINDEX")?1:scale;
                    ZonedDateTime at=Instant.ofEpochSecond(t).atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
                    ZonedDateTime localNow=now.atZone(at.getZone());
                    boolean closed=mode==Mode.SWING?at.toLocalDate().isBefore(localNow.toLocalDate())
                            ||(at.toLocalDate().equals(localNow.toLocalDate())&&localNow.toLocalTime().isAfter(LocalTime.of(16,0))):t+900<=to;
                    double[] values=new double[5];int j=0;
                    for(String f:List.of("o","h","l","c","v")){JsonNode v=root.path(f).get(i);if(!v.isNumber())throw new IllegalStateException("OHLCV không phải số");values[j++]=v.asDouble();}
                    Candle candle=new Candle(t,values[0]*unit,values[1]*unit,values[2]*unit,values[3]*unit,values[4],closed);
                    if(bars.putIfAbsent(t,candle)!=null)throw new IllegalStateException("Nguồn trả timestamp trùng");
                }
            }
            if(bars.isEmpty())warnings.add("Nguồn không trả nến trong khoảng yêu cầu.");
            Feed feed=new Feed(new ArrayList<>(bars.values()),now,"DNSE / Entrade",symbol.equals("VNINDEX")?"POINTS":verified?"VND":"SOURCE",basis,verified,warnings);
            cache.put(key,new Entry(feed,now.plusSeconds(60)));return feed;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return failed(key,old,now,cooldown);}
        catch(Exception e){System.getLogger(EntradeStockAdapter.class.getName()).log(System.Logger.Level.WARNING,"Stock source "+symbol+"/"+mode+": "+e.getClass().getSimpleName());return failed(key,old,now,cooldown);}
    }
    private Feed failed(String key,Entry old,Instant now,long cooldown){
        List<String> warnings=new ArrayList<>(old==null?List.of():old.feed().warnings());
        if(!warnings.contains("Nguồn không khả dụng; dữ liệu cache có thể đã cũ."))warnings.add("Nguồn không khả dụng; dữ liệu cache có thể đã cũ.");
        Feed feed=old==null?new Feed(List.of(),now,"DNSE / Entrade","SOURCE",basis,false,warnings)
                :new Feed(old.feed().candles(),old.feed().fetchedAt(),old.feed().source(),old.feed().unit(),old.feed().priceBasis(),false,warnings);
        cache.put(key,new Entry(feed,now.plusSeconds(cooldown)));return feed;
    }
}
