package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import java.util.*;

/** Alternative candle-based scenarios, not a capital mandate or an execution ledger. All output prices are VND. */
public final class StockCandleScenarios {
    public static final String VERSION="stock-candle-scenarios-v1";
    public record Policy(double bufferAtr,double fallbackStopAtr,double secondTargetAtr,double buyFraction,double firstSellFraction,double maxBuyRsi) {
        public Policy {
            if(!Double.isFinite(bufferAtr)||bufferAtr<=0||bufferAtr>2||!Double.isFinite(fallbackStopAtr)||fallbackStopAtr<=bufferAtr||fallbackStopAtr>10
                    ||!Double.isFinite(secondTargetAtr)||secondTargetAtr<=0||secondTargetAtr>10||!Double.isFinite(buyFraction)||buyFraction<=0||buyFraction>1
                    ||!Double.isFinite(firstSellFraction)||firstSellFraction<=0||firstSellFraction>=1||!Double.isFinite(maxBuyRsi)||maxBuyRsi<=0||maxBuyRsi>=100)
                throw new IllegalArgumentException("Invalid candle scenario policy");
        }
        public static Policy defaults(){return new Policy(.25,1.5,2,.25,.5,70);}
    }
    private StockCandleScenarios() {}
    public static CandleScenarios evaluate(Snapshot snapshot,PositionInput input,String freshness,Policy policy) {
        double conversion=switch(snapshot.data().unit()){case "VND"->1;case "THOUSAND_VND"->1000;default->0;};
        var bars=snapshot.data().candles().stream().filter(Candle::closed).toList();
        if(conversion==0||"ADJUSTED".equals(snapshot.data().priceBasis())||bars.size()<55||input.quantity()==null)return null;
        var indicator=StockLearningEngine.indicators(bars,snapshot.mode());
        double atr=indicator.atr()*conversion;if(!Double.isFinite(atr)||atr<=0)return null;
        var last=bars.get(bars.size()-1);double price=last.close()*conversion,base=input.purchasePrice(),held=input.quantity();
        boolean confirmed=StockDataQuality.canConfirm(snapshot.data(),freshness)
                &&(input.purchaseDate()==null||!java.time.Instant.ofEpochSecond(last.time()).atZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().isBefore(input.purchaseDate()));
        double support=indicator.support()*conversion,resistance=indicator.resistance()*conversion;
        double stop=support>policy.bufferAtr()*atr?support-policy.bufferAtr()*atr:Math.max(price*.01,price-policy.fallbackStopAtr()*atr);
        double pullback=support+policy.bufferAtr()*atr,breakout=resistance+policy.bufferAtr()*atr;
        double target1=resistance>stop?resistance:price+atr,target2=target1+policy.secondTargetAtr()*atr;
        boolean risk=price<=stop,up=indicator.fastMa()>indicator.slowMa(),rsi=indicator.rsi()<policy.maxBuyRsi();
        var recommendation=snapshot.analysis().recommendation();
        boolean volume=recommendation!=null&&recommendation.volumeRatio()!=null&&recommendation.volumeRatio()>=recommendation.volumeConfirmation();
        double buyQty=Math.floor(held*policy.buyFraction()),sell1=Math.min(held,Math.max(1,Math.floor(held*policy.firstSellFraction()))),sell2=held-sell1;
        var data=new PlanCondition("Dữ liệu RAW đủ mới, đơn vị rõ ràng và đã xác minh",confirmed);
        List<CandleScenario> items=new ArrayList<>();
        items.add(scenario("BUY_PULLBACK","BUY","Mua thêm khi hồi phục tại hỗ trợ",pullback,buyQty,base,held,List.of(data,
                new PlanCondition("Giá chưa phá vùng giảm rủi ro",!risk),new PlanCondition("Nến kiểm tra hỗ trợ và đóng lại trên hỗ trợ, trong vùng đệm ATR",last.low()*conversion<=pullback&&price>=support&&price<=pullback+policy.bufferAtr()*atr),
                new PlanCondition("Nến đóng không thấp hơn giá mở",last.close()>=last.open()),new PlanCondition("Xu hướng trung bình tăng và RSI chưa quá cao",up&&rsi),
                new PlanCondition("Khối lượng xác nhận",volume),new PlanCondition("Lượng mô phỏng đủ một cổ phiếu",buyQty>=1))));
        items.add(scenario("BUY_BREAKOUT","BUY","Mua thêm khi vượt kháng cự",breakout,buyQty,base,held,List.of(data,
                new PlanCondition("Giá chưa phá vùng giảm rủi ro",!risk),new PlanCondition("Nến đóng vượt mốc mua, chưa đi xa hơn một ATR",price>breakout&&price<=breakout+atr),
                new PlanCondition("Xu hướng trung bình tăng và RSI chưa quá cao",up&&rsi),new PlanCondition("Khối lượng xác nhận",volume),new PlanCondition("Lượng mô phỏng đủ một cổ phiếu",buyQty>=1))));
        items.add(scenario("SELL_RESISTANCE","SELL",target1>base?"Chốt một phần tại kháng cự":"Giảm một phần khi hồi lên kháng cự",target1,sell1,base,held,List.of(data,
                new PlanCondition("Nến đóng đạt vùng kháng cự",price>=target1),new PlanCondition("Chưa phá vùng giảm rủi ro",!risk))));
        items.add(scenario("SELL_EXTENSION","SELL",target2>base?"Chốt phần còn lại nếu tiếp tục tăng":"Thoát phần còn lại nếu hồi phục tiếp",target2,sell2,base,held-sell1,List.of(data,
                new PlanCondition("Nến đóng đạt kháng cự cộng vùng mở rộng ATR",price>=target2),new PlanCondition("Chưa phá vùng giảm rủi ro",!risk),new PlanCondition("Còn cổ phiếu sau phần bán đầu",sell2>=1))));
        items.add(scenario("SELL_PROTECT","SELL","Giảm rủi ro khi mất hỗ trợ",stop,held,base,held,List.of(data,new PlanCondition("Nến đóng tại hoặc dưới mốc giảm rủi ro",risk))));
        String status=!confirmed?"REFERENCE":risk?"RISK":items.stream().anyMatch(i->i.side().equals("BUY")&&i.status().equals("CONDITION_MET"))?"BUY_SETUP":"MONITOR";
        String summary=switch(status){case "REFERENCE"->"Các mức giá là kịch bản tham khảo; dữ liệu chưa đủ điều kiện xác nhận tín hiệu.";case "RISK"->"Nến đóng đã mất vùng bảo vệ: dừng xem xét mua thêm, ưu tiên đánh giá giảm vị thế.";case "BUY_SETUP"->"Nến hiện tại đạt điều kiện kỹ thuật của một kịch bản mua thêm; kiểm tra vốn khả dụng trước khi quyết định.";default->"Theo dõi các vùng mua và bán bên dưới; chỉ đánh giá điều kiện khi nến đóng.";};
        return new CandleScenarios(VERSION,snapshot.snapshotId(),status,summary,confirmed,last.time(),price,base,held,stop,List.copyOf(items),
                List.of("Hỗ trợ/kháng cự lấy từ lịch sử nến đã đóng của khung đang xem; ATR đo biên độ biến động.",
                        "Hai cách mua là phương án thay thế, mỗi cách mô phỏng thêm "+(policy.buyFraction()*100)+"% lượng đang giữ; không cộng hai phương án.",
                        "Bán tại kháng cự mô phỏng "+(policy.firstSellFraction()*100)+"% lượng đang giữ, phần còn lại ở kháng cự + "+policy.secondTargetAtr()+" ATR; giảm rủi ro là phương án thay thế.",
                        "Mốc giá và lượng tự cập nhật theo snapshot; không phải các lệnh chờ đã đặt."),
                List.of("Chưa biết ngân sách và sức chịu lỗ của bạn; vốn mua thêm chỉ là số tiền cần cho lượng mô phỏng.",
                        "Lượng đang giữ không đồng nghĩa lượng có thể bán. Kiểm tra cổ phiếu đã về và lượng khả dụng trước khi bán.",
                        "Không dự báo chắc chắn giá tương lai; hỗ trợ/kháng cự và ATR không bảo đảm khớp lệnh hoặc lợi nhuận.",
                        "Giá trị trước phí, thuế, trượt giá và sự kiện quyền; lượng nguyên chưa quy đổi theo lô giao dịch."));
    }
    private static CandleScenario scenario(String id,String side,String title,double trigger,double qty,double base,double held,List<PlanCondition> conditions) {
        boolean buy=side.equals("BUY");double after=buy?held+qty:held-qty,average=buy?(base*held+trigger*qty)/after:base;
        var missing=conditions.stream().filter(c->!c.met()).map(PlanCondition::label).toList();
        return new CandleScenario(id,side,title,qty<1?"NO_QUANTITY":missing.isEmpty()?"CONDITION_MET":"WAITING",trigger,qty,buy?trigger*qty:0,average,after,(trigger-base)*(buy?held:qty),conditions,missing);
    }
}
