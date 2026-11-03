package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import java.time.*;
import java.util.*;

/** Causal daily review. Every dated review uses only that date and earlier closed candles. */
public final class StockDailyDesk {
    public static final String VERSION="daily-desk-wilder-ichimoku-fib-v1";
    private StockDailyDesk() {}
    public static DailyDesk build(Feed feed, double volumeConfirmation) {
        var bars=feed.candles().stream().filter(Candle::closed).toList();List<DailyAssessment> days=new ArrayList<>();
        for(int i=bars.size()-1;i>=Math.max(14,bars.size()-10);i--)days.add(at(bars,i,feed,volumeConfirmation));
        return new DailyDesk(VERSION,feed.unit(),feed.priceBasis(),List.copyOf(days));
    }
    public static DailyAssessment at(List<Candle> bars,int i,Feed feed,double volumeConfirmation) {
        Candle current=bars.get(i);double close=current.close(),previous=bars.get(i-1).close();
        double rsi=wilderRsi(bars,i),previousRsi=i>14?wilderRsi(bars,i-1):rsi;
        Double ema20=i>=19?ema(bars,i,20):null,ema50=i>=49?ema(bars,i,50):null,ema200=i>=199?ema(bars,i,200):null;
        Double tenkan=midpoint(bars,i,9),kijun=midpoint(bars,i,26);
        // Spans visible on date i were computed 26 bars earlier. Never compare close against a future cloud.
        int origin=i-26;Double oldTenkan=midpoint(bars,origin,9),oldKijun=midpoint(bars,origin,26);
        Double cloudA=oldTenkan==null||oldKijun==null?null:(oldTenkan+oldKijun)/2,cloudB=midpoint(bars,origin,52);
        double volumeSum=0;int count=0;for(int k=Math.max(0,i-20);k<i;k++){volumeSum+=bars.get(k).volume();count++;}
        Double average=count==20?volumeSum/20:null,ratio=average==null||average<=0?null:current.volume()/average;
        int lowIndex=Math.max(0,i-60),highIndex=lowIndex;
        for(int k=lowIndex;k<i;k++){if(bars.get(k).low()<bars.get(lowIndex).low())lowIndex=k;if(bars.get(k).high()>bars.get(highIndex).high())highIndex=k;}
        double low=bars.get(lowIndex).low(),high=bars.get(highIndex).high();boolean rising=lowIndex<=highIndex;
        List<FibonacciLevel> fib=new ArrayList<>();
        if(i>=60&&high>low)for(double level:new double[]{.236,.382,.5,.618,.786})fib.add(new FibonacciLevel(level,rising?high-(high-low)*level:low+(high-low)*level));
        Double fibBuy=fib.isEmpty()?null:fib.get(rising?1:3).price(),fibSell=fib.isEmpty()?null:fib.get(rising?3:1).price();
        List<Reading> readings=new ArrayList<>();
        readings.add(new Reading("rsi","RSI14 · Wilder",f(rsi)+" (trước "+f(previousRsi)+")",
                "RSI tăng so với phiên trước và nằm trong vùng 50–70; hiện cần "+(rsi>=70?"hạ nhiệt dưới 70":rsi<50?"vượt 50":"giữ trên 50 và tiếp tục tăng"),
                "RSI giảm xuống dưới 50 cảnh báo yếu; dưới 30 là quá bán, cần xem thêm giá/hỗ trợ",
                rsi>=50&&rsi<70&&rsi>previousRsi,rsi<50&&rsi<previousRsi,"RSI cao không tự xác nhận mua; quá mua/quá bán không tự quyết định bán/mua."));
        boolean emaBuy=ema20!=null&&ema50!=null&&close>ema20&&ema20>ema50;
        boolean emaSell=ema20!=null&&ema50!=null&&close<ema20&&ema20<ema50;
        readings.add(new Reading("ema","EMA20 / EMA50", "EMA20 "+f(ema20)+" · EMA50 "+f(ema50),
                "Giá đóng > EMA20 ("+f(ema20)+") và EMA20 > EMA50 ("+f(ema50)+")",
                "Giá đóng < EMA20 ("+f(ema20)+") và EMA20 < EMA50 ("+f(ema50)+")",emaBuy,emaSell,
                "Giá hiện "+(ema20==null?"chưa đủ nến":close>ema20?"trên EMA20":"dưới/bằng EMA20")+"; các đường sẽ thay đổi khi có nến mới."));
        readings.add(new Reading("ema200","EMA200 · nền dài hạn",f(ema200),
                "Giá đóng trên EMA200 ("+f(ema200)+") để củng cố nền tăng dài hạn",
                "Giá đóng dưới EMA200 ("+f(ema200)+") là cảnh báo nền dài hạn yếu",
                ema200!=null&&close>ema200,ema200!=null&&close<ema200,"Cần ít nhất 200 nến; EMA200 là bối cảnh, không phải điểm khớp lệnh."));
        Double top=cloudA==null||cloudB==null?null:Math.max(cloudA,cloudB),bottom=top==null?null:Math.min(cloudA,cloudB);
        boolean cloudBuy=top!=null&&close>top&&tenkan!=null&&kijun!=null&&tenkan>kijun;
        boolean cloudSell=bottom!=null&&close<bottom&&tenkan!=null&&kijun!=null&&tenkan<kijun;
        readings.add(new Reading("ichimoku","Ichimoku 9 / 26 / 52",top==null?"Chưa đủ 78 nến":(close>top?"Trên mây":close<bottom?"Dưới mây":"Trong mây")+" · "+f(bottom)+" – "+f(top)+" · Tenkan "+f(tenkan)+" / Kijun "+f(kijun),
                "Đóng trên đỉnh mây "+f(top)+"; Tenkan "+f(tenkan)+" > Kijun "+f(kijun),
                "Đóng dưới đáy mây "+f(bottom)+"; Tenkan "+f(tenkan)+" < Kijun "+f(kijun),cloudBuy,cloudSell,
                "Mây tại ngày đang xem dùng Senkou A/B tính từ 26 phiên trước; giá trong mây cho thấy tín hiệu dễ xung đột."));
        readings.add(new Reading("volume","Khối lượng / trung bình 20 phiên",f(current.volume())+" CP · TB20 "+f(average)+" · "+f(ratio)+" lần",
                "Khối lượng ≥ "+f(average==null?null:average*volumeConfirmation)+" CP ("+volumeConfirmation+"× TB20), cùng giá tăng/vượt kháng cự",
                "Khối lượng ≥ "+f(average==null?null:average*volumeConfirmation)+" CP cùng giá giảm/phá hỗ trợ là cảnh báo lực bán",
                ratio!=null&&ratio>=volumeConfirmation&&close>previous,ratio!=null&&ratio>=volumeConfirmation&&close<previous,
                ratio==null?"Chưa đủ mẫu so sánh.":"Hiện "+f(ratio)+"× TB20; "+(ratio>=volumeConfirmation?"đã đạt":"còn thiếu "+f(Math.max(0,average*volumeConfirmation-current.volume()))+" CP để đạt")+" mức xác nhận. Volume tự nó không chỉ hướng giá."));
        readings.add(new Reading("fibonacci","Fibonacci · biên 60 nến trước",fib.isEmpty()?"Chưa đủ biên 60 nến":"Đáy "+f(low)+" ("+date(bars.get(lowIndex))+") → đỉnh "+f(high)+" ("+date(bars.get(highIndex))+") · "+(rising?"nhịp tăng":"nhịp giảm"),
                "Đóng vượt "+(rising?"38,2%":"61,8%")+" tại "+f(fibBuy)+" và giá tăng, kèm EMA/volume xác nhận",
                "Đóng mất "+(rising?"61,8%":"38,2%")+" tại "+f(fibSell)+" và giá giảm; kiểm tra thêm hỗ trợ",
                fibBuy!=null&&close>fibBuy&&close>previous,fibSell!=null&&close<fibSell&&close<previous,
                "Đo trên high/low của 60 nến trước; đây là biên tham khảo, không phải nhận diện sóng hay dự báo chắc giá quay đầu."));
        int buys=(int)readings.stream().filter(Reading::buyReady).count(),sells=(int)readings.stream().filter(Reading::sellReady).count();
        String bias=buys>=4&&sells<=1?"BULLISH":sells>=4&&buys<=1?"BEARISH":"MIXED";
        String summary=(bias.equals("BULLISH")?"Nhiều điều kiện đang nghiêng tăng":bias.equals("BEARISH")?"Nhiều điều kiện đang nghiêng giảm":"Các chỉ báo chưa đồng thuận")
                +": "+buys+"/6 điều kiện nghiêng mua, "+sells+"/6 nghiêng bán. Đây là số điều kiện, không phải xác suất thắng.";
        List<String> cautions=new ArrayList<>(List.of("Đánh giá theo ngày dùng nến đã đóng. Chọn ngày cũ là tính lại bằng lịch sử tới ngày đó, không phải dự đoán đã chốt trước kỳ.",
                "Các ngưỡng là kịch bản kỹ thuật tham khảo; kiểm tra nguồn giá, sự kiện quyền và khả năng giao dịch trước khi quyết định."));
        if(!feed.verified()||"UNKNOWN".equals(feed.priceBasis()))cautions.add("Nguồn/cơ sở giá chưa xác minh đầy đủ: nhận định có điều kiện, chưa xác nhận lệnh mua/bán.");
        return new DailyAssessment(date(current),current.time(),close,(close/previous-1)*100,bias,summary,buys,sells,
                new DailyValues(rsi,previousRsi,ema20,ema50,ema200,tenkan,kijun,cloudA,cloudB,average,ratio,i>=60?low:null,i>=60?high:null,
                        i>=60?date(bars.get(lowIndex)):null,i>=60?date(bars.get(highIndex)):null,rising?"UP":"DOWN",List.copyOf(fib)),List.copyOf(readings),List.copyOf(cautions));
    }
    public static double ema(List<Candle> bars,int end,int period) {double value=bars.get(0).close(),alpha=2.0/(period+1);for(int i=1;i<=end;i++)value+=alpha*(bars.get(i).close()-value);return value;}
    public static double wilderRsi(List<Candle> bars,int end) {
        double gain=0,loss=0;for(int i=1;i<=14;i++){double change=bars.get(i).close()-bars.get(i-1).close();gain+=Math.max(0,change)/14;loss+=Math.max(0,-change)/14;}
        for(int i=15;i<=end;i++){double change=bars.get(i).close()-bars.get(i-1).close();gain=(gain*13+Math.max(0,change))/14;loss=(loss*13+Math.max(0,-change))/14;}
        return gain+loss==0?50:loss==0?100:100-100/(1+gain/loss);
    }
    private static Double midpoint(List<Candle> bars,int end,int period) {if(end<period-1)return null;double high=-Double.MAX_VALUE,low=Double.MAX_VALUE;for(int i=end-period+1;i<=end;i++){high=Math.max(high,bars.get(i).high());low=Math.min(low,bars.get(i).low());}return (high+low)/2;}
    private static String date(Candle c) {return Instant.ofEpochSecond(c.time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().toString();}
    private static String f(Double value) {return value==null?"chưa đủ dữ liệu":String.format(Locale.US,"%,.2f",value);}
}
