package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import java.time.*;
import java.util.*;

/** Deterministic, gross-of-cost scenarios. Executions are explicit user records, never inferred from candles. */
public final class StockPositionPlanEngine {
    public static final String VERSION = "stock-position-plan-v1";
    private StockPositionPlanEngine() {}
    private static final Set<String> IDS=Set.of("AVG1","AVG2","TP1","TP2","REDUCE");
    private static void require(boolean ok,String message) { if(!ok) throw new IllegalArgumentException(message); }
    private static boolean finite(double x) { return Double.isFinite(x)&&x>=0&&x<=1e18; }
    private static double loss(double capital,double proceeds,double qty,double stop) { return Math.max(0,capital-proceeds-qty*stop); }
    private static double rise(double avg,double price) { return Math.max(0,(avg/price-1)*100); }

    public static PositionPlan evaluate(Snapshot s,PositionInput p,String freshness,Instant now) {
        var input=p.plan(); var policy=input.policy();
        require(policy!=null,"Thiếu cấu hình kế hoạch.");
        require(p.quantity()!=null&&p.quantity()>0,"Kế hoạch cần số lượng đang giữ.");
        require(input.startedAt()!=null&&!input.startedAt().isAfter(now),"Mốc lập kế hoạch không hợp lệ.");
        double initialQty=p.quantity(),base=p.purchasePrice(),hard=p.maxLossPercent()==null?10:p.maxLossPercent();
        require(hard>0&&hard<100&&Double.isFinite(hard),"Giới hạn lỗ phải lớn hơn 0 và nhỏ hơn 100%.");
        require(policy.alertLossPercent()>0&&policy.alertLossPercent()<hard,"Ngưỡng cảnh báo phải nhỏ hơn giới hạn dừng lỗ.");
        require(finite(policy.maxLossVnd())&&policy.maxLossVnd()>0,"Cần trần lỗ bằng tiền lớn hơn 0.");
        require(finite(policy.additionalBudget())&&finite(policy.maxCapital())&&policy.maxCapital()>=base*initialQty,"Giới hạn vốn phải đủ cho vị thế ban đầu.");
        require(policy.averagingBudgets()!=null&&policy.averagingBudgets().size()<=2,"Tối đa hai đợt bình giá.");
        double allocations=0;
        for(Double budget:policy.averagingBudgets()) { require(budget!=null&&finite(budget),"Ngân sách từng đợt không hợp lệ.");allocations+=budget; }
        require(allocations<=policy.additionalBudget()+.001,"Tổng ngân sách các đợt vượt vốn mua thêm.");
        require(policy.targets()!=null&&policy.targets().size()==2,"Cần hai mục tiêu chốt lời.");
        List<Double> targets=new ArrayList<>();double previous=base,totalSellPercent=0;
        for(var target:policy.targets()) {
            require(target!=null,"Thiếu mục tiêu chốt lời.");
            require((target.price()!=null) != (target.gainPercent()!=null),"Chọn giá mục tiêu hoặc % lãi, không nhập cả hai.");
            double price=target.price()!=null?target.price():base*(1+target.gainPercent()/100);
            require(finite(price)&&price>previous,"TP2 phải cao hơn TP1; mục tiêu phải cao hơn giá vốn gốc.");
            require(finite(target.sellPercent())&&target.sellPercent()>0&&target.sellPercent()<=100,"Tỷ lệ bán mỗi đợt phải trong (0,100].");
            targets.add(price);previous=price;totalSellPercent+=target.sellPercent();
        }
        require(totalSellPercent<=100,"Tổng tỷ lệ bán không được vượt 100% lượng gốc.");
        require(policy.trailingPercent()==null||(Double.isFinite(policy.trailingPercent())&&policy.trailingPercent()>0&&policy.trailingPercent()<100),"Khoảng bảo vệ lợi nhuận phải trong (0,100%).");
        require(input.observedPeak()==null||(finite(input.observedPeak())&&input.observedPeak()>0),"Đỉnh đã theo dõi không hợp lệ.");
        double conversion="VND".equals(s.data().unit())?1:"THOUSAND_VND".equals(s.data().unit())?1000:0;
        require(conversion>0&&!"ADJUSTED".equals(s.data().priceBasis()),"Không thể quy đổi giá nguồn để lập kế hoạch.");
        var candles=s.data().candles();require(!candles.isEmpty(),"Chưa có giá để lập kế hoạch.");
        var valuation=candles.get(candles.size()-1);double price=valuation.close()*conversion;
        var closed=candles.stream().filter(Candle::closed).toList();
        Candle decision=closed.isEmpty()?null:closed.get(closed.size()-1);
        Double close=decision==null?null:decision.close()*conversion;
        boolean decisionAfterPurchase=decision!=null&&(p.purchaseDate()==null||!Instant.ofEpochSecond(decision.time()).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDate().isBefore(p.purchaseDate()));
        boolean confirmed=StockDataQuality.canConfirm(s.data(),freshness)&&decisionAfterPurchase;
        double stop=base*(1-hard/100),alert=base*(1-policy.alertLossPercent()/100);
        double held=initialQty,avg=base,committed=base*initialQty,spent=0,proceeds=0,realized=0;
        Double sellable=p.sellableQuantity();
        List<PositionExecution> executions=input.executions()==null?List.of():input.executions();
        require(executions.size()<=5,"Quá nhiều bước giao dịch trong một kế hoạch.");
        Set<String> done=new HashSet<>(); Map<String,PlanStep> completed=new HashMap<>();
        Instant lastExecution=input.startedAt(),firstProfit=null;
        for(var e:executions) {
            require(e!=null&&e.stepId()!=null&&IDS.contains(e.stepId())&&done.add(e.stepId()),"Bước giao dịch không hợp lệ hoặc đã được ghi nhận.");
            require(finite(e.price())&&e.price()>0&&finite(e.quantity())&&e.quantity()>0&&e.quantity()%1==0,"Giá và lượng giao dịch thực tế phải hợp lệ.");
            require(e.executedAt()!=null&&!e.executedAt().isBefore(lastExecution)&&!e.executedAt().isAfter(now),"Thời gian giao dịch phải theo thứ tự và không ở tương lai.");
            lastExecution=e.executedAt();double cash=e.price()*e.quantity();Double profit=null;
            boolean buy=e.stepId().startsWith("AVG");
            if(buy) {
                int slot=e.stepId().equals("AVG1")?0:1;
                require(slot<policy.averagingBudgets().size(),"Đợt mua thêm chưa được cấu hình.");
                require(firstProfit==null&&!done.contains("REDUCE"),"Kế hoạch đã chuyển sang giảm vị thế; cần lập kế hoạch mới để mua thêm.");
                require(slot==0||done.contains("AVG1")||policy.averagingBudgets().get(0)==0,"Ghi nhận đợt bình giá đầu trước.");
                require(cash<=policy.averagingBudgets().get(slot)+.001&&spent+cash<=policy.additionalBudget()+.001
                        &&committed+cash<=policy.maxCapital()+.001,"Giao dịch mua vượt ngân sách kế hoạch.");
                avg=(avg*held+cash)/(held+e.quantity());held+=e.quantity();committed+=cash;spent+=cash;
                // Newly bought shares do not become sellable without explicit user confirmation.
            } else {
                if(e.sellableBefore()!=null) {
                    require(finite(e.sellableBefore())&&e.sellableBefore()%1==0&&e.sellableBefore()<=held,"Lượng có thể bán tại giao dịch không hợp lệ.");
                    sellable=e.sellableBefore();
                }
                require(e.quantity()<=held&&sellable!=null&&e.quantity()<=sellable,"Lượng bán vượt số có thể bán đã xác nhận.");
                if(e.stepId().startsWith("TP")) {
                    int slot=e.stepId().equals("TP1")?0:1;
                    require(slot==0||done.contains("TP1"),"Ghi nhận TP1 trước TP2.");
                    require(e.quantity()<=Math.floor(initialQty*policy.targets().get(slot).sellPercent()/100),"Lượng bán vượt tỷ lệ của bước chốt lời.");
                    if(firstProfit==null)firstProfit=e.executedAt();
                }
                profit=(e.price()-avg)*e.quantity();realized+=profit;held-=e.quantity();sellable-=e.quantity();proceeds+=cash;
            }
            completed.put(e.stepId(),new PlanStep(e.stepId(),buy?"BUY":"SELL","Đã ghi nhận "+e.stepId(),"DONE",e.price(),e.quantity(),cash,avg,held,
                    (price-avg)*held,loss(committed,proceeds,held,stop),rise(avg,price),profit,List.of(),List.of()));
        }
        if(input.availableQuantity()!=null) {
            require(finite(input.availableQuantity())&&input.availableQuantity()%1==0&&input.availableQuantity()<=held,"Số cổ phiếu hiện có thể bán phải nằm trong lượng đang giữ.");
            sellable=input.availableQuantity();
        }
        Double peak=null;double effectiveStop=stop;
        if(firstProfit!=null&&policy.trailingPercent()!=null) {
            peak=input.observedPeak();
            if(confirmed)for(Candle bar:closed)if(!Instant.ofEpochSecond(bar.time()).isBefore(firstProfit))
                peak=peak==null?bar.close()*conversion:Math.max(peak,bar.close()*conversion);
            if(peak!=null)effectiveStop=Math.max(stop,peak*(1-policy.trailingPercent()/100));
        }
        boolean breached=close!=null&&(close<=effectiveStop||loss(committed,proceeds,held,close)>=policy.maxLossVnd());
        var rec=s.analysis().recommendation();
        boolean technicalBreak=rec!=null&&rec.sellBelow()!=null&&close!=null&&close<rec.sellBelow()*conversion;
        boolean risk=held>0&&(breached||technicalBreak);
        boolean alerted=close!=null&&close<=alert;
        double remaining=Math.max(0,Math.min(policy.additionalBudget()-spent,policy.maxCapital()-committed));
        List<PlanStep> steps=new ArrayList<>();List<String> cautions=new ArrayList<>();
        if(!confirmed)cautions.add("Chưa xác nhận hành động: cần dữ liệu RAW đã xác minh, đủ mới và nến đóng sau ngày mua.");
        if(!valuation.closed())cautions.add("P&L dùng giá nến đang hình thành; các bước chỉ xét nến đã đóng.");
        cautions.add("Số lượng là cổ phiếu nguyên giả định; chưa xác minh lô/bước giá của sàn. Mốc giá không bảo đảm khớp lệnh.");
        cautions.add("Các số tiền là gộp trước phí, thuế và sự kiện quyền. Bình giá không xóa khoản lỗ hiện tại.");
        double simulationQty=held,simulationAvg=avg,simulationCapital=committed,simulationRemaining=remaining;
        boolean previousPending=false;
        var indicator=s.analysis().indicators();
        double support=indicator==null?0:indicator.support()*conversion;
        double atr=indicator==null?0:indicator.atr()*conversion;
        for(int slot=0;slot<policy.averagingBudgets().size();slot++) {
            String id="AVG"+(slot+1);
            if(completed.containsKey(id)) {steps.add(completed.get(id));continue;}
            double allocation=policy.averagingBudgets().get(slot);
            if(allocation==0)continue;
            double trigger=support>0?Math.min(alert,support+(slot==0?Math.max(0,atr)*.25:0)):alert;
            double cashLimit=Math.min(allocation,simulationRemaining);
            double riskRoom=policy.maxLossVnd()-loss(simulationCapital,proceeds,simulationQty,stop);
            double qty=trigger>stop&&trigger<simulationAvg?Math.max(0,Math.floor(Math.min(cashLimit/trigger,riskRoom/(trigger-stop)))):0;
            double cash=qty*trigger,afterQty=simulationQty+qty,afterAvg=afterQty>0?(simulationQty*simulationAvg+cash)/afterQty:simulationAvg;
            List<PlanCondition> conditions=List.of(
                    new PlanCondition("Dữ liệu và nến đánh giá được xác nhận",confirmed),
                    new PlanCondition("Đã chạm ngưỡng cảnh báo, còn trên giới hạn dừng",alerted&&!risk),
                    new PlanCondition("Giá đóng trong vùng mua thêm, thấp hơn giá vốn",close!=null&&close<=trigger&&close<avg&&close>effectiveStop),
                    new PlanCondition("Giữ/lấy lại hỗ trợ và không thấp hơn nến trước",support>0&&close!=null&&close>=support&&closed.size()>1&&decision.close()>=closed.get(closed.size()-2).close()),
                    new PlanCondition("Khối lượng đạt xác nhận của khung đang xem",rec!=null&&rec.volumeRatio()!=null&&rec.volumeRatio()>=rec.volumeConfirmation()),
                    new PlanCondition("Còn ngân sách, hạn mức lỗ và chưa chuyển sang bán",qty>=1&&firstProfit==null&&!done.contains("REDUCE")&&held>0));
            List<String> blockers=conditions.stream().filter(c->!c.met()).map(PlanCondition::label).toList();
            String status=risk||firstProfit!=null||done.contains("REDUCE")||qty<1||held==0?"BLOCKED":previousPending?"WAIT_PREVIOUS":blockers.isEmpty()?"READY":"WAITING";
            steps.add(new PlanStep(id,"BUY","Bình giá đợt "+(slot+1),status,trigger,qty,cash,afterAvg,afterQty,
                    (price-afterAvg)*afterQty,loss(simulationCapital+cash,proceeds,afterQty,stop),rise(afterAvg,price),null,conditions,blockers));
            simulationQty=afterQty;simulationAvg=afterAvg;simulationCapital+=cash;simulationRemaining-=cash;previousPending=true;
        }
        double sellRoom=sellable==null?held:Math.min(held,sellable),remainingHeld=held,simulatedProceeds=proceeds;
        boolean priorProfitPending=false;
        for(int slot=0;slot<2;slot++) {
            String id="TP"+(slot+1);
            if(completed.containsKey(id)) {steps.add(completed.get(id));continue;}
            double qty=Math.max(0,Math.min(Math.floor(initialQty*policy.targets().get(slot).sellPercent()/100),sellRoom));
            double target=targets.get(slot),cash=qty*target;
            List<PlanCondition> conditions=List.of(new PlanCondition("Dữ liệu và nến đánh giá được xác nhận",confirmed),
                    new PlanCondition("Nến đóng đạt mục tiêu",close!=null&&close>=target),
                    new PlanCondition("Đã xác nhận đủ cổ phiếu có thể bán",sellable!=null&&qty>0));
            List<String> blockers=conditions.stream().filter(c->!c.met()).map(PlanCondition::label).toList();
            String status=held==0||risk||qty==0?"BLOCKED":sellable==null?"NEED_SELLABLE":priorProfitPending?"WAIT_PREVIOUS":blockers.isEmpty()?"READY":"WAITING";
            steps.add(new PlanStep(id,"SELL","Chốt lời "+(slot+1),status,target,qty,cash,avg,remainingHeld-qty,
                    (price-avg)*(remainingHeld-qty),loss(committed,simulatedProceeds+cash,remainingHeld-qty,stop),rise(avg,price),(target-avg)*qty,conditions,blockers));
            sellRoom-=qty;remainingHeld-=qty;simulatedProceeds+=cash;priorProfitPending=true;
        }
        if(completed.containsKey("REDUCE"))steps.add(completed.get("REDUCE"));
        else {
            double qty=sellable==null?held:Math.min(held,sellable);
            List<PlanCondition> conditions=List.of(new PlanCondition("Dữ liệu và nến đánh giá được xác nhận",confirmed),
                    new PlanCondition("Đã chạm mốc dừng, trần lỗ hoặc mất hỗ trợ",risk),new PlanCondition("Đã xác nhận lượng có thể bán",sellable!=null&&qty>0));
            steps.add(new PlanStep("REDUCE","SELL","Giảm rủi ro",held==0||qty==0?"BLOCKED":sellable==null?"NEED_SELLABLE":confirmed&&risk?"READY":"WAITING",
                    effectiveStop,qty,qty*price,avg,held-qty,0,loss(committed,proceeds+qty*price,held-qty,stop),rise(avg,price),(price-avg)*qty,conditions,
                    conditions.stream().filter(c->!c.met()).map(PlanCondition::label).toList()));
        }
        boolean profitReady=steps.stream().anyMatch(t->t.id().startsWith("TP")&&"READY".equals(t.status()));
        boolean buyReady=steps.stream().anyMatch(t->t.side().equals("BUY")&&"READY".equals(t.status()));
        String status=held==0?"CLOSED":!confirmed?"REFERENCE":risk?"RISK":profitReady?"TAKE_PROFIT":buyReady?"AVERAGE":alerted?"REVIEW":"HOLD";
        String summary=switch(status) {
            case "CLOSED" -> "Đã ghi nhận bán hết vị thế trong kế hoạch này.";
            case "REFERENCE" -> "Kịch bản tham khảo; dữ liệu chưa đủ điều kiện xác nhận hành động.";
            case "RISK" -> "Đã mất điều kiện bảo vệ. Dừng mua thêm và kiểm tra lượng có thể bán.";
            case "TAKE_PROFIT" -> "Đã đạt mục tiêu chốt lời. Xem lượng bán của bước tiếp theo.";
            case "AVERAGE" -> "Đủ điều kiện thử nghiệm cho đợt bình giá tiếp theo trong hạn mức đã đặt.";
            case "REVIEW" -> "Đã vượt cảnh báo lỗ. Kiểm tra các điều kiện còn thiếu trước khi mua thêm.";
            default -> "Theo dõi các mốc đã đặt; chưa có bước mới đủ điều kiện.";
        };
        return new PositionPlan(VERSION,s.snapshotId(),input.startedAt(),status,summary,confirmed,price,valuation.time(),valuation.closed(),close,
                decision==null?null:decision.time(),base,avg,held,sellable,committed,spent,proceeds,realized,proceeds+held*price-committed,
                remaining,alert,stop,effectiveStop,peak,List.copyOf(steps),List.copyOf(cautions));
    }
}
