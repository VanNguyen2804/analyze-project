package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import java.time.*;
import java.util.*;

/** Explainable price scenarios; no classifier score is presented as a probability of profit. */
public final class StockRecommendationEngine {
    public static final String VERSION = "stock-price-scenarios-v2-wilder";
    public record Policy(double breakoutAtr, double volumeMultiple, double stopAtr, double targetAtr, double maxBuyRsi) {
        public Policy {
            if (!Double.isFinite(breakoutAtr) || breakoutAtr < 0 || breakoutAtr > 2
                    || !Double.isFinite(volumeMultiple) || volumeMultiple < 1 || volumeMultiple > 5
                    || !Double.isFinite(stopAtr) || stopAtr <= 0 || stopAtr > 10
                    || !Double.isFinite(targetAtr) || targetAtr <= stopAtr || targetAtr > 20
                    || !Double.isFinite(maxBuyRsi) || maxBuyRsi < 50 || maxBuyRsi > 90)
                throw new IllegalArgumentException("Invalid technical recommendation policy");
        }
        public static Policy defaults() { return new Policy(.25, 1.2, 1.5, 3, 70); }
    }
    private final Policy policy;
    public StockRecommendationEngine(Policy policy) { this.policy = policy; }

    public Recommendation evaluate(Feed feed, Mode mode, String freshness, Learning learning) {
        List<Candle> bars = feed.candles().stream().filter(Candle::closed).toList();
        List<String> buy = List.of("Chờ nến đóng vượt mốc mua; giá chạm hoặc vượt trong nến đang chạy chưa xác nhận.",
                "Khối lượng đạt ít nhất " + policy.volumeMultiple() + " lần mức so sánh; xu hướng trung bình nhanh cao hơn trung bình chậm.",
                "RSI dưới " + policy.maxBuyRsi() + "; nếu giá đã cách mốc mua hơn 1 ATR, chờ kiểm tra lại vùng vừa vượt thay vì mua đuổi.");
        List<String> sell = List.of("Nếu đang nắm giữ, nến đóng dưới mốc giảm/bán là cảnh báo phá hỗ trợ; cân nhắc giảm rủi ro.",
                "Vượt kháng cự rồi đóng trở lại dưới vùng vừa vượt là dấu hiệu vượt giả; đánh giá lại kế hoạch mua.",
                "Chỉ bán trong số lượng có thể bán; mốc tham khảo không phải lệnh bán tự động hoặc khuyến nghị bán khống.");
        List<String> cautions = new ArrayList<>();
        if (bars.size() < 55 || !Set.of("VND", "THOUSAND_VND").contains(feed.unit()))
            return new Recommendation(VERSION,"UNAVAILABLE","Chưa đủ dữ liệu lập kịch bản","Cần ít nhất 55 nến đã đóng và đơn vị giá rõ ràng.",feed.unit(),null,null,null,null,null,null,null,policy.volumeMultiple(),buy,sell,List.of("Không tự tạo mốc giá khi thiếu dữ liệu."));
        Indicator i = StockLearningEngine.indicators(bars, mode);
        buy=new ArrayList<>(buy);
        buy.add("Hiện tại: " + (i.fastMa()>i.slowMa()?"xu hướng trung bình đang tăng":"xu hướng trung bình chưa xác nhận tăng") + "; RSI " + Math.round(i.rsi()) + ".");
        Candle last = bars.get(bars.size()-1);
        double buyAbove = i.resistance()+policy.breakoutAtr()*i.atr();
        double sellBelow = Math.max(0,i.support()-policy.breakoutAtr()*i.atr());
        Double ratio = volumeRatio(bars,mode);
        boolean up = i.fastMa()>i.slowMa();
        boolean breakout = last.close()>buyAbove;
        boolean breakdown = last.close()<sellBelow;
        boolean volume = ratio!=null && ratio>=policy.volumeMultiple();
        boolean extended = last.close()>buyAbove+i.atr();
        String action="WATCH",title="Chờ giá xác nhận",summary="Giá chưa xác nhận vượt kháng cự hoặc phá hỗ trợ. Theo dõi các mốc bên dưới.";
        if (breakdown) {action="SELL";title="Cân nhắc giảm / bán nếu đang nắm giữ";summary="Nến đã đóng dưới vùng hỗ trợ có đệm biến động. Ưu tiên kiểm soát rủi ro; không chờ mô hình AI đổi điểm.";}
        else if (breakout && up && volume && i.rsi()<policy.maxBuyRsi() && !extended) {
            action="BUY";title="Cân nhắc mua theo kịch bản vượt kháng cự";summary="Nến đã đóng vượt mốc mua, khối lượng và xu hướng cùng xác nhận. Kiểm tra vùng dừng lỗ trước khi quyết định.";
        } else if (breakout) {
            title=extended?"Chờ kiểm tra lại vùng kháng cự vừa vượt":"Đã vượt mốc giá, còn thiếu xác nhận";
            List<String> missing=new ArrayList<>();
            if(!volume)missing.add(ratio==null?"chưa đủ khối lượng so sánh":"khối lượng chưa đạt mức xác nhận");
            if(!up)missing.add("xu hướng chưa xác nhận tăng");
            if(i.rsi()>=policy.maxBuyRsi())missing.add("RSI đang cao ("+Math.round(i.rsi())+")");
            summary=extended?"Giá đã đi xa hơn mốc mua trên 1 ATR; chờ giá kiểm tra lại vùng vừa vượt và giữ được vùng này.":"Chưa cân nhắc mua vì " + String.join("; ",missing) + ". Chờ thêm xác nhận sau khi nến đóng.";
        } else if (last.high()>buyAbove) {
            title="Chưa xác nhận vượt kháng cự";summary="Giá cao nhất trong nến đã vượt mốc mua nhưng giá đóng nến không giữ được phía trên; có nguy cơ vượt giả.";
        } else {
            summary="Giá đóng "+String.format(Locale.US,"%,.2f",last.close())+" chưa vượt mốc mua "+String.format(Locale.US,"%,.2f",buyAbove)
                    +"; RSI "+String.format(Locale.US,"%.2f",i.rsi())+"; khối lượng "+(ratio==null?"chưa đủ mẫu":String.format(Locale.US,"%.2f",ratio)+" lần mức so sánh (cần "+policy.volumeMultiple()+" lần).")
                    +" Xem bảng theo ngày để biết điều kiện nào đã đạt hoặc còn thiếu.";
        }
        if (!feed.verified() || "UNKNOWN".equals(feed.priceBasis()) || !"RECENT".equals(freshness)) {
            if ("BUY".equals(action)) title="Chờ xác nhận dữ liệu trước khi cân nhắc mua";
            if ("SELL".equals(action)) title="Theo dõi nguy cơ phá hỗ trợ; chờ xác nhận dữ liệu";
            summary="Quan sát trên dữ liệu tham khảo. " + summary;
            action="WATCH";
            cautions.add("Kịch bản có điều kiện: dữ liệu chưa được xác minh đầy đủ hoặc đã cũ; chưa xác nhận hành động mua/bán hiện tại.");
        }
        if (ratio==null) cautions.add("Chưa đủ khối lượng so sánh; chưa xác nhận tín hiệu mua.");
        if (mode==Mode.INTRADAY) cautions.add("Khối lượng so với cùng giờ của ít nhất 5 phiên trước; dự báo và khả năng bán trong ngày là hai việc riêng.");
        if (!"EXPERIMENTAL".equals(learning.status())) cautions.add("Học máy chưa đủ điều kiện kiểm định; kịch bản trên dựa trên quy tắc giá–khối lượng, không phải xác suất AI.");
        cautions.add("Mốc dừng lỗ/chốt lời tính giả định từ mốc mua theo ATR, chưa tính phí, trượt giá, thanh toán hoặc sự kiện quyền; chưa chứng minh hiệu quả giao dịch.");
        double entry=buyAbove;
        if (!Double.isFinite(i.atr()) || i.atr()<=0) {
            action="WATCH";cautions.add("Biến động ATR bằng 0; chưa lập được vùng quản trị rủi ro.");
        }
        return new Recommendation(VERSION,action,title,summary,feed.unit(),last.time(),last.close(),buyAbove,sellBelow,
                i.atr()>0?Math.max(0,entry-policy.stopAtr()*i.atr()):null,i.atr()>0?entry+policy.targetAtr()*i.atr():null,
                ratio,policy.volumeMultiple(),buy,sell,List.copyOf(cautions));
    }

    private Double volumeRatio(List<Candle> bars, Mode mode) {
        Candle last=bars.get(bars.size()-1);double sum=0;int count=0;
        var zone=ZoneId.of("Asia/Ho_Chi_Minh");var time=Instant.ofEpochSecond(last.time()).atZone(zone);
        Set<LocalDate> dates=new HashSet<>();
        for (int n=bars.size()-2;n>=0 && count<20;n--) {
            Candle previous=bars.get(n);
            var at=Instant.ofEpochSecond(previous.time()).atZone(zone);
            if (mode==Mode.INTRADAY && (!at.toLocalTime().equals(time.toLocalTime()) || !at.toLocalDate().isBefore(time.toLocalDate()) || !dates.add(at.toLocalDate()))) continue;
            sum+=previous.volume();count++;
        }
        return count<(mode==Mode.INTRADAY?5:20) || sum<=0?null:last.volume()/(sum/count);
    }
}
