package com.example.analyzeproject.application.service;

import com.example.analyzeproject.application.port.out.StockMarketDataPort;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import com.example.analyzeproject.domain.service.stock.StockLearningEngine;
import com.example.analyzeproject.domain.service.stock.StockRecommendationEngine;
import com.example.analyzeproject.domain.service.stock.StockDailyDesk;
import com.example.analyzeproject.domain.service.stock.StockDataQuality;
import com.example.analyzeproject.domain.service.stock.StockPositionPlanEngine;
import java.time.*;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class StockService {
    private final StockMarketDataPort feed;
    private final Clock clock;
    private final StockLearningEngine learning;
    private final StockRecommendationEngine recommendations;
    private final Map<String,Snapshot> snapshots=new LinkedHashMap<>();
    public StockService(StockMarketDataPort feed,Clock clock,StockLearningEngine learning){this(feed,clock,learning,new StockRecommendationEngine(StockRecommendationEngine.Policy.defaults()));}
    public StockService(StockMarketDataPort feed,Clock clock,StockLearningEngine learning,StockRecommendationEngine recommendations){this.feed=feed;this.clock=clock;this.learning=learning;this.recommendations=recommendations;}
    public List<Map<String,String>> instruments(){return List.of(Map.of("symbol","VIC","name","Vingroup"),Map.of("symbol","FPT","name","FPT"),Map.of("symbol","SZC","name","Sonadezi Châu Đức"),Map.of("symbol","MBB","name","Ngân hàng Quân đội"));}
    public List<Map<String,Object>> watchlist(){
        List<Map<String,Object>> result=new ArrayList<>();
        for(var instrument:instruments()){
            Feed data=feed.load(instrument.get("symbol"),Mode.SWING);
            Candle latest=data.candles().isEmpty()?null:data.candles().get(data.candles().size()-1);
            Map<String,Object> item=new LinkedHashMap<>();item.putAll(instrument);item.put("price",latest==null?null:latest.close());
            item.put("asOf",latest==null?null:latest.time());item.put("unit",data.unit());item.put("source",data.source());
            item.put("freshness",freshness(data,Mode.SWING,latest==null?null:latest.time()));result.add(item);
        }
        return result;
    }
    private void validate(String symbol){if(instruments().stream().noneMatch(i->i.get("symbol").equals(symbol)))throw new IllegalArgumentException("Chỉ hỗ trợ VIC, FPT, SZC, MBB");}
    public synchronized Snapshot analyze(String symbol,Mode mode){
        validate(symbol);Feed data=feed.load(symbol,mode),benchmark=feed.load("VNINDEX",mode);
        Feed daily=mode==Mode.SWING?data:feed.load(symbol,Mode.SWING);
        String id=fingerprint(symbol+mode+data.toString()+benchmark.toString()+daily.toString());
        Snapshot cached=snapshots.get(id);if(cached!=null&&Duration.between(data.fetchedAt(),clock.instant()).getSeconds()<60)return cached;
        List<Candle> closed=data.candles().stream().filter(Candle::closed).toList();
        Long last=closed.isEmpty()?null:closed.get(closed.size()-1).time();
        String fresh=freshness(data,mode,last);List<String> reasons=new ArrayList<>();
        Learning ml=learning.analyze(closed,mode);Indicator indicators=null;String signal="UNAVAILABLE";
        if(closed.size()>=55){
            indicators=StockLearningEngine.indicators(closed,mode);double price=closed.get(closed.size()-1).close();
            reasons.add(indicators.fastMa()>indicators.slowMa()?"Đường trung bình nhanh cao hơn đường chậm.":"Đường trung bình nhanh thấp hơn hoặc bằng đường chậm.");
            reasons.add("RSI14 Wilder: "+Math.round(indicators.rsi())+"; ATR14 dùng trung bình true range.");
            signal="WATCH";
            if("BUY".equals(ml.signal())&&indicators.fastMa()>indicators.slowMa()&&indicators.rsi()<75)signal="BUY";
            if("SELL".equals(ml.signal())&&price<indicators.fastMa())signal="SELL";
            if("VALIDATION_WEAK".equals(ml.status()))reasons.add("Mô hình chưa vượt điều kiện kiểm định; chỉ theo dõi.");
        }else reasons.add("Chưa đủ 55 nến đã đóng để phân tích kỹ thuật.");
        if(!data.verified()||!"RECENT".equals(fresh)||"UNKNOWN".equals(data.priceBasis())){
            signal="UNAVAILABLE";reasons.add("Chưa đủ dữ liệu đã xác minh hoặc dữ liệu không đủ mới để đưa tín hiệu mua/bán.");
        }
        if(mode==Mode.INTRADAY)reasons.add("Tín hiệu trong ngày không xác nhận cổ phiếu có thể bán ngay; cần kiểm tra số lượng sẵn có và thanh toán.");
        Recommendation recommendation=recommendations.evaluate(data,mode,fresh,ml);
        Snapshot result=new Snapshot(symbol,mode,mode==Mode.SWING?"1D":"15",id,data,benchmark,fresh,"UNVERIFIED_CALENDAR",last,new Analysis(signal,List.copyOf(reasons),indicators,ml,recommendation),StockDailyDesk.build(daily,recommendation.volumeConfirmation()));
        snapshots.put(id,result);while(snapshots.size()>24)snapshots.remove(snapshots.keySet().iterator().next());return result;
    }
    private String freshness(Feed data,Mode mode,Long last){
        if(last==null)return "EMPTY";
        if(Duration.between(data.fetchedAt(),clock.instant()).getSeconds()>180)return "STALE";
        ZonedDateTime now=clock.instant().atZone(ZoneId.of("Asia/Ho_Chi_Minh"));
        ZonedDateTime bar=Instant.ofEpochSecond(last).atZone(now.getZone());
        // Conservative: weekends/holidays are not guessed. An unverified calendar can only restrict signals.
        if(mode==Mode.INTRADAY){
            boolean sameDay=bar.toLocalDate().equals(now.toLocalDate());
            boolean afterClose=now.toLocalTime().isAfter(LocalTime.of(15,0))&&bar.toLocalTime().compareTo(LocalTime.of(14,30))>=0;
            boolean lunch=now.toLocalTime().compareTo(LocalTime.of(11,30))>=0&&now.toLocalTime().isBefore(LocalTime.of(13,0))&&bar.toLocalTime().compareTo(LocalTime.of(11,0))>=0;
            return sameDay&&(afterClose||lunch||Duration.between(bar,now).toMinutes()<=45)?"RECENT":"STALE";
        }
        return Duration.between(bar,now).toHours()<=36?"RECENT":"STALE";
    }
    public synchronized PositionResult position(String symbol,String id,PositionInput p){
        validate(symbol);
        if(p==null)throw new IllegalArgumentException("Thiếu thông tin vị thế.");
        if(p.purchaseDate()!=null&&p.purchaseDate().isAfter(LocalDate.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")))))throw new IllegalArgumentException("Ngày mua không hợp lệ");
        valid(p.purchasePrice(),0,1e12,false);valid(p.quantity(),0,1e10,true);valid(p.buyFeePercent(),0,100,true);valid(p.sellCostPercent(),0,100,true);valid(p.maxLossPercent(),0,100,true);valid(p.sellableQuantity(),0,1e10,true);
        if(p.quantity()!=null&&p.quantity()%1!=0||p.sellableQuantity()!=null&&p.sellableQuantity()%1!=0)throw new IllegalArgumentException("Số lượng phải là số nguyên");
        if(p.sellableQuantity()!=null&&(p.quantity()==null||p.sellableQuantity()>p.quantity()))throw new IllegalArgumentException("Số lượng có thể bán không hợp lệ");
        if(p.quantity()!=null&&p.quantity()==0)throw new IllegalArgumentException("Số lượng nắm giữ phải lớn hơn 0");
        Snapshot s=snapshots.get(id);if(s==null||!s.symbol().equals(symbol))throw new IllegalArgumentException("Snapshot hết hạn; hãy tải lại biểu đồ");
        if(s.data().candles().isEmpty())return new PositionResult(id,"UNAVAILABLE",null,null,null,null,null,List.of("Nguồn chưa trả giá; hãy cập nhật lại dữ liệu."));
        double conversion="VND".equals(s.data().unit())?1:"THOUSAND_VND".equals(s.data().unit())?1000:0;
        if(conversion==0||"ADJUSTED".equals(s.data().priceBasis()))
            return new PositionResult(id,"UNAVAILABLE",null,null,null,null,null,List.of("Giá chưa có đơn vị quy đổi sang VND hoặc chỉ có giá lịch sử điều chỉnh; chưa thể so với giá mua."));
        Candle c=s.data().candles().get(s.data().candles().size()-1);double price=c.close()*conversion;
        LocalDate priceDate=Instant.ofEpochSecond(c.time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate();
        if(p.purchaseDate()!=null&&p.purchaseDate().isAfter(priceDate))return new PositionResult(id,"UNAVAILABLE",null,null,null,null,null,List.of("Giá gần nhất có trước ngày mua; cần cập nhật giá sau ngày mua để tính lãi/lỗ."));
        double change=(price/p.purchasePrice()-1)*100,buyFee=p.buyFeePercent()==null?0:p.buyFeePercent()/100,sell=p.sellCostPercent()==null?0:p.sellCostPercent()/100;
        String status="UNAVAILABLE".equals(s.analysis().recommendation().action())?"WATCH":s.analysis().recommendation().action();List<String> reasons=new ArrayList<>();
        reasons.add(s.analysis().recommendation().summary());
        String currentFreshness=freshness(s.data(),s.mode(),s.lastClosedBarAt());
        double maxLoss=p.maxLossPercent()==null?10:p.maxLossPercent();
        if(maxLoss<=0||maxLoss>=100)throw new IllegalArgumentException("Giới hạn lỗ phải trong (0,100%).");
        var closed=s.data().candles().stream().filter(Candle::closed).toList();
        boolean confirmed=StockDataQuality.canConfirm(s.data(),currentFreshness)&&!closed.isEmpty();
        if(confirmed&&p.purchaseDate()!=null)confirmed=!Instant.ofEpochSecond(closed.get(closed.size()-1).time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().isBefore(p.purchaseDate());
        if(!confirmed)status="WATCH";
        if(!closed.isEmpty()&&closed.get(closed.size()-1).close()*conversion<=p.purchasePrice()*(1-maxLoss/100)){
            if(confirmed)status="SELL";
            reasons.add("Nến đã đóng chạm giới hạn lỗ "+maxLoss+"% đã cấu hình; kiểm tra lượng có thể bán.");
        }
        else if("BUY".equals(status))status="HOLD";
        reasons.add("Giá dùng tính: "+price+" VND/cổ phiếu; mốc nến "+Instant.ofEpochSecond(c.time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))+" giờ Việt Nam.");
        reasons.add("Lãi/lỗ theo giá chưa tính phí, thuế, cổ tức hoặc thay đổi số lượng do sự kiện quyền.");
        if(!s.data().verified()||"UNKNOWN".equals(s.data().priceBasis()))reasons.add("Kết quả tính tham khảo; cơ sở điều chỉnh giá chưa được xác nhận. Không dùng số liệu này làm xác nhận mua/bán.");
        if(!"RECENT".equals(freshness(s.data(),s.mode(),s.lastClosedBarAt())))reasons.add("Dữ liệu cũ: kết quả dùng giá gần nhất đang có, không phải giá giao dịch hiện tại.");
        if(!c.closed())reasons.add("Giá thuộc nến đang hình thành và có thể thay đổi.");
        reasons.add(p.sellableQuantity()==null?"Chưa biết số lượng có thể bán; kết quả chỉ là tín hiệu kỹ thuật.":"Số lượng có thể bán do bạn khai báo: "+p.sellableQuantity());
        if(p.purchaseDate()!=null&&!s.data().candles().isEmpty()&&p.purchaseDate().isBefore(Instant.ofEpochSecond(s.data().candles().get(0).time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate()))reasons.add("Ngày mua ngoài lịch sử hiện có; không tái dựng phân tích tại ngày mua.");
        Double gross=p.quantity()==null?null:(price-p.purchasePrice())*p.quantity();
        Double net=p.quantity()==null||p.buyFeePercent()==null||p.sellCostPercent()==null?null:(price*(1-sell)-p.purchasePrice()*(1+buyFee))*p.quantity();
        Double breakEven=p.buyFeePercent()==null||p.sellCostPercent()==null||sell>=1?null:p.purchasePrice()*(1+buyFee)/(1-sell);
        if(p.plan()!=null) {
            var plan=StockPositionPlanEngine.evaluate(s,p,currentFreshness,clock.instant());
            reasons.add(0,plan.summary());
            String action=switch(plan.status()){case "RISK","TAKE_PROFIT" -> "SELL";case "AVERAGE" -> "BUY";case "REFERENCE","REVIEW" -> "WATCH";default -> "HOLD";};
            return new PositionResult(id,action,price,(price/plan.averageCost()-1)*100,(price-plan.averageCost())*plan.quantity(),null,null,List.copyOf(reasons),plan);
        }
        return new PositionResult(id,status,price,change,gross,net,breakEven,List.copyOf(reasons));
    }
    private void valid(Double v,double min,double max,boolean optional){if(v==null){if(optional)return;throw new IllegalArgumentException("Thiếu giá mua");}if(!Double.isFinite(v)||v<min||v>max||(!optional&&v==0))throw new IllegalArgumentException("Giá trị đầu vào ngoài phạm vi");}
    private String fingerprint(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
