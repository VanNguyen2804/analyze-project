package com.example.analyzeproject.application.service;
import com.example.analyzeproject.application.service.StockService;
import com.example.analyzeproject.domain.model.stock.StockModels.*;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class StockTradingPlanService {
 public enum Calculation { SR_ATR, EMA200 }
 private final StockService stocks;
 private final StockTradingPolicy policy;
 public StockTradingPlanService(StockService stocks, StockTradingPolicy policy){this.stocks=stocks;this.policy=policy;}
 public Object plans(String symbol,PositionInput input,Calculation calculation){
  if(input.purchasePrice()==null || !Double.isFinite(input.purchasePrice()) || input.purchasePrice()<=0 || input.quantity()==null || !Double.isFinite(input.quantity()) || input.quantity()<=0 || input.quantity()%1!=0)throw new IllegalArgumentException("Invalid position");
  Snapshot snapshot=stocks.analyzeCached(symbol,Mode.SWING);
  double factor=snapshot.data().unit().equals("VND")?1:snapshot.data().unit().equals("THOUSAND_VND")?1000:0;
  var bars=snapshot.data().candles().stream().filter(Candle::closed).toList();
  if(factor==0 || !snapshot.data().verified() || !snapshot.data().priceBasis().equals("RAW") || !snapshot.freshness().equals("RECENT"))return Map.of("plans",List.of(),"message","Cần nến ngày RAW đã xác minh và đủ mới để lập kế hoạch.");
  if(bars.size()<policy.minimumBars())return Map.of("plans",List.of(),"message","Cần tối thiểu 15 nến ngày đã đóng để tính biên độ và lập kế hoạch lướt sóng.");
  if(calculation==Calculation.EMA200 && bars.size()<200)return Map.of("plans",List.of(),"message","EMA200 cần ít nhất 200 nến ngày đã đóng; còn thiếu "+(200-bars.size())+" nến.");
  List<Object> plans=new ArrayList<>();
  plans.add(plan(bars,Math.min(policy.swingWindow(),bars.size()),factor,input,"SWING","Lướt sóng","2–8 tuần"));
  if(bars.size()>=policy.longMinimumBars())plans.add(plan(bars,Math.min(policy.longWindow(),bars.size()),factor,input,"LONG","Dài hạn",bars.size()<policy.standardBars()?"Tạm tính · cần bổ sung lịch sử":"6–18 tháng"));
  if(calculation==Calculation.EMA200)plans.replaceAll(p -> emaPlan((Map<String,Object>)p,bars,factor,input));
  return Map.of("plans",plans,"policy",policy,"asOf",bars.get(bars.size()-1).time(),"message",bars.size()<policy.longMinimumBars()?"Đã có kế hoạch lướt sóng. Dài hạn cần tối thiểu 30 nến ngày; không suy diễn từ lịch sử quá ngắn.":bars.size()<policy.standardBars()?"Kế hoạch dài hạn tạm tính từ lịch sử ngắn; cần tích lũy thêm nến trước khi đánh giá xu hướng dài hạn.":"");
 }
 public Map<String,Object> plan(List<Candle> bars,int window,double factor,PositionInput input,String id,String title,String duration){
  var range=bars.subList(bars.size()-window,bars.size());
  double support=range.stream().mapToDouble(Candle::low).min().orElseThrow()*factor;
  double resistance=range.stream().mapToDouble(Candle::high).max().orElseThrow()*factor;
  double price=bars.get(bars.size()-1).close()*factor;
  double atr=0;
  for(int i=1;i<bars.size();i++){
   var c=bars.get(i);double prev=bars.get(i-1).close();
   double tr=Math.max(c.high()-c.low(),Math.max(Math.abs(c.high()-prev),Math.abs(c.low()-prev)))*factor;
   if(i<=14)atr+=tr/14;else atr=(atr*13+tr)/14;
  }
  if(!Double.isFinite(atr)||atr<=0)throw new IllegalArgumentException("Insufficient volatility data");
  boolean longer=id.equals("LONG");
  double buyBuffer=longer?policy.longBuyAtr():policy.swingBuyAtr(),stopBuffer=longer?policy.longStopAtr():policy.swingStopAtr(),extension=longer?policy.longExtensionAtr():policy.swingExtensionAtr();
  double stop=Math.max(Math.ulp(support),support-stopBuffer*atr),entry=support+buyBuffer*atr,risk=entry-stop;
  double t1=Math.max(support,resistance-policy.targetBufferAtr()*atr),t2=resistance+extension*atr;
  double qty=input.quantity(),cost=input.purchasePrice();
  Map<String,Object> p=new LinkedHashMap<>();p.put("id",id);p.put("title",title);p.put("duration",duration);p.put("entryLow",support);p.put("entryHigh",entry);p.put("stop",stop);p.put("target1",t1);p.put("target2",t2);p.put("profit1",(t1-cost)*qty);p.put("profit2",(t2-cost)*qty);p.put("riskAmount",(stop-cost)*qty);p.put("rewardRisk",Math.max(0,(t1-entry)/risk));p.put("support",support);p.put("resistance",resistance);p.put("atr14",atr);p.put("status",price<=stop?"Ưu tiên bảo vệ vốn":price>=t1?"Theo dõi chốt lời":(t1-entry)/risk<policy.minimumRewardRisk()?"Chưa mua: dư địa tới kháng cự chưa đủ":price<=entry?"Chờ nến xác nhận vùng mua":"Chờ giá về vùng mua");p.put("barsUsed",window);p.put("dataQuality",window<policy.longMinimumBars()?"LIMITED":window<policy.standardBars()?"PROVISIONAL":"STANDARD");p.put("method", "Hỗ trợ/kháng cự: đáy/đỉnh "+window+" phiên đóng. ATR14 Wilder. Mua tại hỗ trợ đến hỗ trợ + "+buyBuffer+" ATR; bảo vệ vốn dưới hỗ trợ "+stopBuffer+" ATR. Chốt 1: kháng cự − "+policy.targetBufferAtr()+" ATR; chốt 2: kháng cự + "+extension+" ATR khi vượt kháng cự có xác nhận. Chưa mua nếu dư địa mục tiêu 1 dưới "+policy.minimumRewardRisk()+"R. Trước phí/thuế.");return p;
 }
 public Map<String,Object> emaPlan(Map<String,Object> base,List<Candle> bars,double factor,PositionInput input){
  double ema=bars.subList(0,200).stream().mapToDouble(Candle::close).average().orElseThrow();
  for(int i=200;i<bars.size();i++)ema+=(bars.get(i).close()-ema)*2/201;
  ema*=factor;
  boolean longer=base.get("id").equals("LONG");double atr=(Double)base.get("atr14"),price=bars.get(bars.size()-1).close()*factor;
  double low=Math.max(Math.ulp(ema),ema-policy.emaBuyLowerAtr()*atr),high=ema+(longer?policy.longBuyAtr():policy.swingBuyAtr())*atr,stop=Math.max(Math.ulp(ema),ema-(longer?policy.longEmaStopAtr():policy.swingEmaStopAtr())*atr);
  double risk=high-stop,t1=high+policy.emaFirstR()*risk,t2=high+(longer?policy.longEmaSecondR():policy.swingEmaSecondR())*risk;
  base.put("ema200",ema);base.put("entryLow",low);base.put("entryHigh",high);base.put("stop",stop);base.put("target1",t1);base.put("target2",t2);base.put("rewardRisk",policy.emaFirstR());
  base.put("profit1",(t1-input.purchasePrice())*input.quantity());base.put("profit2",(t2-input.purchasePrice())*input.quantity());base.put("riskAmount",(stop-input.purchasePrice())*input.quantity());
  base.put("status",price<ema?"Chưa mua: giá dưới EMA200":price>=t1?"Theo dõi chốt lời":price<=high?"Chờ nến xác nhận EMA200":"Chờ giá hồi về EMA200");
  base.put("method","EMA200 khởi tạo bằng SMA200, làm mượt 2/201. Vùng mua quanh EMA200 với đệm ATR14; chỉ xem xét khi giá trên EMA200. Bảo vệ vốn dưới EMA200 "+(longer?policy.longEmaStopAtr():policy.swingEmaStopAtr())+" ATR. Mục tiêu "+policy.emaFirstR()+"R và "+(longer?policy.longEmaSecondR():policy.swingEmaSecondR())+"R từ biên mua cao. Đây là kịch bản kỹ thuật trước phí/thuế.");return base;
 }
}