package com.example.analyzeproject.presentation.rest;
import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController
@RequestMapping("/api/stocks")
public class StockTradingPlanController {
 private final StockService stocks;
 public StockTradingPlanController(StockService stocks){this.stocks=stocks;}
 @PostMapping("/{symbol}/trading-plans") public Object plans(@PathVariable String symbol,@RequestBody PositionInput input){
  if(input.purchasePrice()==null || !Double.isFinite(input.purchasePrice()) || input.purchasePrice()<=0 || input.quantity()==null || !Double.isFinite(input.quantity()) || input.quantity()<=0 || input.quantity()%1!=0)throw new IllegalArgumentException("Invalid position");
  Snapshot snapshot=stocks.analyzeCached(symbol,Mode.SWING);
  double factor=snapshot.data().unit().equals("VND")?1:snapshot.data().unit().equals("THOUSAND_VND")?1000:0;
  var bars=snapshot.data().candles().stream().filter(Candle::closed).toList();
  if(factor==0 || !snapshot.data().verified() || !snapshot.data().priceBasis().equals("RAW") || !snapshot.freshness().equals("RECENT") || bars.size()<200)return Map.of("plans",List.of(),"message","Cần ít nhất 200 nến ngày RAW đã xác minh và đủ mới để lập hai kế hoạch.");
  return Map.of("plans",List.of(plan(bars,20,factor,input,"SWING","Lướt sóng","2–8 tuần",2,3),plan(bars,200,factor,input,"LONG","Dài hạn","6–18 tháng",3,5)),"asOf",bars.get(bars.size()-1).time());
 }
 private Object plan(List<Candle> bars,int window,double factor,PositionInput input,String id,String title,String duration,double r1,double r2){
  var range=bars.subList(bars.size()-window,bars.size());
  double support=range.stream().mapToDouble(Candle::low).min().orElseThrow()*factor;
  double resistance=range.stream().mapToDouble(Candle::high).max().orElseThrow()*factor;
  double price=bars.get(bars.size()-1).close()*factor;
  double atr=0;for(int i=bars.size()-14;i<bars.size();i++){var c=bars.get(i);double prev=bars.get(i-1).close();atr+=Math.max(c.high()-c.low(),Math.max(Math.abs(c.high()-prev),Math.abs(c.low()-prev)))*factor/14;}
  if(atr<=0)throw new IllegalArgumentException("Insufficient volatility data");
  double stop=Math.max(factor,support-atr),entry=support+atr*.25,risk=entry-stop;
  double t1=Math.max(resistance,entry+r1*risk),t2=Math.max(t1+atr,entry+r2*risk);
  double qty=input.quantity(),cost=input.purchasePrice();
  Map<String,Object> p=new LinkedHashMap<>();p.put("id",id);p.put("title",title);p.put("duration",duration);p.put("entryLow",support);p.put("entryHigh",entry);p.put("stop",stop);p.put("target1",t1);p.put("target2",t2);p.put("profit1",(t1-cost)*qty);p.put("profit2",(t2-cost)*qty);p.put("riskAmount",(stop-cost)*qty);p.put("rewardRisk",(t1-entry)/risk);p.put("status",price<=stop?"Ưu tiên bảo vệ vốn":price>=t1?"Theo dõi chốt lời":price<=entry?"Chờ nến xác nhận vùng mua":"Chờ giá về vùng mua");p.put("method", "Hỗ trợ/kháng cự "+window+" phiên; đệm ATR14. Mục tiêu tối thiểu "+r1+"R / "+r2+"R tính từ vùng mua. Chốt từng phần theo lượng thực tế có thể bán.");return p;
 }
}
