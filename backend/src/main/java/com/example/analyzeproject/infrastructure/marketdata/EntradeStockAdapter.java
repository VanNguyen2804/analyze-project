package com.example.analyzeproject.infrastructure.marketdata;

import com.example.analyzeproject.application.port.out.StockMarketDataPort;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/** No authentication, browser impersonation, persistent data storage or automatic provider fallback. */
public final class EntradeStockAdapter implements StockMarketDataPort {
    private final HttpClient client;
    private final ObjectMapper json;
    private final Clock clock;
    private final double scale;
    private final boolean verified,enabled;
    private final String basis;
    private final Map<String,Entry> cache=new ConcurrentHashMap<>();
    private final Map<String,ReentrantLock> loadLocks=new ConcurrentHashMap<>();
    private final Semaphore providerSlots=new Semaphore(2);
    private record Entry(Feed feed, Instant retryAfter) {}
    public EntradeStockAdapter(ObjectMapper json,Clock clock,boolean enabled,double scale,String basis,boolean verified){
        this(json,clock,enabled,scale,basis,verified,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
    }
    EntradeStockAdapter(ObjectMapper json,Clock clock,boolean enabled,double scale,String basis,boolean verified,HttpClient client){
        if(!Double.isFinite(scale)||scale<=0||!Set.of("UNKNOWN","RAW","ADJUSTED").contains(basis))throw new IllegalArgumentException("Cấu hình nguồn không hợp lệ");
        this.json=json;this.clock=clock;this.enabled=enabled;this.scale=scale;this.basis=basis;this.verified=verified;
        this.client=client;
    }
    @Override public Feed load(String symbol,Mode mode){return loadBounded(symbol,mode,false);}
    @Override public Feed quote(String symbol){
        var full=cache.get(symbol+Mode.SWING);if(full!=null&&clock.instant().isBefore(full.retryAfter()))return full.feed();
        return loadBounded(symbol,Mode.SWING,true);
    }
    private Feed loadBounded(String symbol,Mode mode,boolean quote){
        if(!Set.of("VIC","FPT","SZC","MBB","VNINDEX").contains(symbol))throw new IllegalArgumentException("Mã không hỗ trợ");
        String key=symbol+(quote?"QUOTE":mode);Instant now=clock.instant();Entry old=cache.get(key);
        if(old!=null&&now.isBefore(old.retryAfter()))return old.feed();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(quote?4:12);
        var lock=loadLocks.computeIfAbsent(key,k->new ReentrantLock());
        try {
            if(!lock.tryLock(Math.max(0,deadline-System.nanoTime()),TimeUnit.NANOSECONDS))return unavailable(old,now);
        }catch(InterruptedException e){Thread.currentThread().interrupt();return unavailable(old,now);}
        try {
            old=cache.get(key);now=clock.instant();
            if(old!=null&&now.isBefore(old.retryAfter()))return old.feed();
            return loadLocked(symbol,mode,quote,key,now,old,deadline);
        }finally{lock.unlock();}
    }
    private Feed loadLocked(String symbol,Mode mode,boolean quote,String key,Instant now,Entry old,long deadline){
        if(!enabled)return new Feed(List.of(),now,"DNSE / Entrade","SOURCE","UNKNOWN",false,List.of("Nguồn chưa được bật. Cần xác minh quyền sử dụng và cấu hình nguồn trước khi sử dụng live."));
        long cooldown=60;
        try{
            long from=now.minus(Duration.ofDays(quote?30:mode==Mode.SWING?1826:60)).getEpochSecond(),to=now.getEpochSecond();
            TreeMap<Long,Candle> bars=new TreeMap<>();List<String> warnings=new ArrayList<>();
            if(!verified)warnings.add("Chưa xác minh đầy đủ cơ sở giá, timestamp và lịch sàn; tín hiệu mua/bán bị hạn chế. Lãi/lỗ theo giá chỉ là số liệu tham khảo.");
            warnings.add("Nguồn công khai thực nghiệm; độ trễ chưa xác định. Chưa xác nhận quyền tái phân phối; chỉ cache tạm trong bộ nhớ.");
            // Provider limits are undocumented. Bounded time windows; never follow arbitrary URLs.
            long step=mode==Mode.SWING?to-from:10*86400L;
            for(long a=from;a<to;a+=step){
                long b=Math.min(to,a+step-1);
                URI uri=URI.create("https://services.entrade.com.vn/chart-api/v2/ohlcs/"+(symbol.equals("VNINDEX")?"index":"stock")+"?symbol="+symbol+"&resolution="+(mode==Mode.SWING?"1D":"15")+"&from="+a+"&to="+b);
                long remaining=deadline-System.nanoTime();if(remaining<=0||!providerSlots.tryAcquire(remaining,TimeUnit.NANOSECONDS))throw new TimeoutException("Provider deadline");
                HttpResponse<String> response;
                try {
                    remaining=deadline-System.nanoTime();if(remaining<=0)throw new TimeoutException("Provider deadline");
                    response=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofNanos(Math.min(remaining,TimeUnit.SECONDS.toNanos(5)))).GET().build(),HttpResponse.BodyHandlers.ofString());
                }finally{providerSlots.release();}
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
            if(mode==Mode.SWING) {
                int before=bars.size();List<Candle> canonical=normalizeDaily(new ArrayList<>(bars.values()));
                bars.clear();canonical.forEach(c->bars.put(c.time(),c));
                if(before>bars.size())warnings.add("Nguồn có "+(before-bars.size())+" bản ghi ngày trùng phiên; chọn timestamp muộn nhất mỗi ngày, không cộng dồn khối lượng.");
            }
            if(bars.isEmpty())warnings.add("Nguồn không trả nến trong khoảng yêu cầu.");
            Feed feed=new Feed(new ArrayList<>(bars.values()),now,"DNSE / Entrade",priceUnit(symbol,scale,verified),basis,verified,warnings);
            cache.put(key,new Entry(feed,now.plusSeconds(60)));return feed;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return failed(key,old,now,cooldown);}
        catch(Exception e){System.getLogger(EntradeStockAdapter.class.getName()).log(System.Logger.Level.WARNING,"Stock source "+symbol+"/"+mode+": "+e.getClass().getSimpleName());return failed(key,old,now,cooldown);}
    }
    static List<Candle> normalizeDaily(List<Candle> input) {
        TreeMap<LocalDate,Candle> days=new TreeMap<>();
        for(Candle candle:input){LocalDate date=Instant.ofEpochSecond(candle.time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
            days.merge(date,candle,(a,b)->a.time()>b.time()?a:b);}
        return List.copyOf(days.values());
    }
    static String priceUnit(String symbol,double scale,boolean verified) {
        if(symbol.equals("VNINDEX"))return "POINTS";
        if(scale==1000)return "VND";
        if(scale==1)return "THOUSAND_VND";
        return verified?"VND":"SOURCE";
    }
    private Feed failed(String key,Entry old,Instant now,long cooldown){
        Feed feed=unavailable(old,now);cache.put(key,new Entry(feed,now.plusSeconds(cooldown)));return feed;
    }
    private Feed unavailable(Entry old,Instant now){
        List<String> warnings=new ArrayList<>(old==null?List.of():old.feed().warnings());
        if(!warnings.contains("Nguồn không khả dụng; dữ liệu cache có thể đã cũ."))warnings.add("Nguồn không khả dụng; dữ liệu cache có thể đã cũ.");
        Feed feed=old==null?new Feed(List.of(),now,"DNSE / Entrade","SOURCE",basis,false,warnings)
                :new Feed(old.feed().candles(),old.feed().fetchedAt(),old.feed().source(),old.feed().unit(),old.feed().priceBasis(),false,warnings);
        return feed;
    }
}
