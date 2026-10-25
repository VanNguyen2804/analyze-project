package com.example.analyzeproject.service;

import com.example.analyzeproject.model.LotteryNumber;
import java.util.*;

final class AnalysisReportSupport {
    static Map<String,Object> explain(LotteryNumber draw, List<LotteryNumber> prior, List<Integer> predicted) {
        var history=prior.stream().sorted(Comparator.comparing(LotteryNumber::getDrawDate).reversed()).toList();
        List<Map<String,Object>> balls=new ArrayList<>();
        var numbers=new ArrayList<>(draw.getNumbers());
        if(draw.getSpecialNumber()!=null) numbers.add(draw.getSpecialNumber());
        for(int index=0;index<numbers.size();index++) {
            int number=numbers.get(index), frequency=0; Integer gap=null; boolean special=index>=draw.getNumbers().size();
            for(int i=0;i<history.size();i++) {
                var row=history.get(i);
                if(special?Objects.equals(row.getSpecialNumber(),number):row.getNumbers().contains(number)) {
                    frequency++; if(gap==null) gap=i;
                }
            }
            var item=new LinkedHashMap<String,Object>();
            item.put("number",number);item.put("isSpecial",special);item.put("role","Thống kê lịch sử trước kỳ quay");
            item.put("frequency",frequency);item.put("drawGap",gap);
            item.put("explanation","Trong "+history.size()+" kỳ trước, số "+number+" xuất hiện "+frequency+" lần"+(gap==null?"; chưa ghi nhận lần xuất hiện trước.":"; cách lần gần nhất "+gap+" kỳ đã lưu."));
            balls.add(item);
        }
        var matched=predicted.stream().filter(draw.getNumbers()::contains).toList();
        var judgment=new LinkedHashMap<String,Object>();
        judgment.put("summary",predicted.size()==6?"Vé sáu số khớp "+matched.size()+"/"+draw.getNumbers().size()+": "+matched:"Không đủ lịch sử để tạo vé kiểm thử.");
        judgment.put("primaryReason","Đối chiếu dự đoán hồi cứu với kết quả đã lưu.");
        judgment.put("missedFactors",draw.getNumbers().stream().filter(n->!predicted.contains(n)).map(n->"Số "+n+" không nằm trong vé sáu số.").toList());
        judgment.put("correctiveAdjustment","So sánh trên nhiều kỳ trước khi thay đổi tham số; số liệu này chưa xác định nguyên nhân vật lý của kết quả.");
        return Map.of("whyWinningBallsAppeared",balls,"whyAlgorithmMissed",judgment);
    }
    @SuppressWarnings("unchecked")
    static Map<String,Object> summarize(List<Map<String,Object>> draws, int total, String algorithm, String category) {
        int evaluated=0, hits=0, hit3=0;
        for (var draw:draws) {
            var prediction=(Map<String,Object>)draw.get("aiPrediction");
            if (((List<?>)prediction.getOrDefault("predicted6Numbers",List.of())).size()!=6) continue;
            int count=((Number)prediction.getOrDefault("matchedIn6Count",0)).intValue();
            evaluated++; hits+=count; if(count>=3) hit3++;
        }
        var dates=draws.stream().map(d->d.get("drawDate").toString()).sorted().toList();
        var result=new LinkedHashMap<String,Object>();
        result.put("algorithm",algorithm); result.put("category",category); result.put("totalHistoricalDraws",total);
        result.put("requestedDrawsCount",draws.size()); result.put("evaluatedDrawsCount",evaluated); result.put("unevaluatedDrawsCount",draws.size()-evaluated);
        result.put("fromDate",dates.isEmpty()?null:dates.get(0)); result.put("toDate",dates.isEmpty()?null:dates.get(dates.size()-1));
        result.put("mainNumberHits",hits); result.put("averageMatched",evaluated==0?null:(double)hits/evaluated);
        result.put("hit3Draws",hit3); result.put("hit3RatePercent",evaluated==0?null:100.0*hit3/evaluated);
        result.put("scope",evaluated+"/"+draws.size()+" kỳ được chọn đủ lịch sử; "+total+" kỳ hợp lệ trong kho dữ liệu.");
        result.put("conclusion",evaluated==0?"Chưa có kỳ đủ lịch sử để đo hiệu quả.":"Kiểm thử vé sáu số trên "+evaluated+" kỳ: khớp tổng "+hits+" số, "+hit3+" kỳ đạt ít nhất ba số. Dùng cấu hình hiện tại; chưa có so sánh phiên bản dự đoán lưu trước giờ quay.");
        result.put("dominantFlaws",evaluated==0?List.of():List.of("Có "+(evaluated-hit3)+"/"+evaluated+" vé sáu số khớp dưới ba số."));
        result.put("coreRemedies",evaluated==0?List.of():List.of("Đánh giá cùng số vé và cùng cửa sổ thời gian khi thử cấu hình khác."));
        return result;
    }
}
