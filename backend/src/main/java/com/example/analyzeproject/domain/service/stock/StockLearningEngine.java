package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.*;
import java.util.*;

/** Deterministic regularized multinomial logistic regression. Every training label matures before its test block. */
public final class StockLearningEngine {
    public static final String VERSION = "stock-softmax-purged-v2-wilder";
    private static final String[] NAMES = {"Lợi suất 1 nến", "Lợi suất 5 nến", "Khoảng cách EMA", "RSI", "ATR", "Khối lượng tương đối"};
    private final double buyThreshold, sellThreshold, cost;
    public StockLearningEngine(double buyThreshold, double sellThreshold, double cost) {
        if (!Double.isFinite(buyThreshold) || !Double.isFinite(sellThreshold) || !Double.isFinite(cost)
                || buyThreshold <= 0.34 || buyThreshold >= 1 || sellThreshold <= 0.34 || sellThreshold >= 1 || cost < 0 || cost > .1)
            throw new IllegalArgumentException("Cấu hình học máy không hợp lệ");
        this.buyThreshold=buyThreshold; this.sellThreshold=sellThreshold; this.cost=cost;
    }
    public Learning analyze(List<Candle> candles, Mode mode) {
        for(int i=0;i<candles.size();i++)if(!candles.get(i).closed()||(i>0&&candles.get(i).time()<=candles.get(i-1).time()))throw new IllegalArgumentException("Học máy chỉ nhận nến đã đóng theo thứ tự thời gian, không trùng");
        int h = mode == Mode.SWING ? 10 : 4;
        int end=candles.size()-h, start=55, total=end-start;
        if (total < 240) return unavailable(h, "Cần ít nhất " + (295+h) + " nến đã đóng để huấn luyện và kiểm định.");
        double[][] features = featureMatrix(candles,start);
        int[] labels=new int[candles.size()];Arrays.fill(labels,-1);
        for (int i=start;i<end;i++) {
            if(mode==Mode.INTRADAY&&!sameSession(candles.get(i),candles.get(i+h)))continue;
            double ret=candles.get(i+h).close()/candles.get(i+1).open()-1;
            // Neutral band includes configured hypothetical round-trip costs plus volatility.
            double band=cost+Math.max(.002, features[i][4]*Math.sqrt(h)*.35);
            labels[i]=ret>band?2:ret < -band?0:1;
        }
        int first=start+(int)(total*.65), correct=0, baseline=0, count=0;
        int[] actual=new int[3], hits=new int[3]; double loss=0;List<ValidationFold> folds=new ArrayList<>();
        for (int block=0;block<3;block++) {
            int a=first+(end-first)*block/3, b=first+(end-first)*(block+1)/3;
            int trainEnd=a-h; // Strict label-end < first test feature timestamp.
            Model model=fit(features,labels,start,trainEnd);
            folds.add(new ValidationFold(candles.get(trainEnd-1+h).time(),candles.get(a).time(),candles.get(b-1).time()));
            int[] frequencies=new int[3]; for(int i=start;i<trainEnd;i++) if(labels[i]>=0)frequencies[labels[i]]++;
            int majority=argmax(frequencies);
            for(int i=a;i<b;i++) {
                if(labels[i]<0)continue;
                double[] p=model.predict(features[i]); int prediction=argmax(p), y=labels[i];
                count++; actual[y]++; if(prediction==y){correct++;hits[y]++;}
                if(majority==y) baseline++;
                loss-=Math.log(Math.max(1e-12,p[y]));
            }
        }
        if(count<30)return unavailable(h,"Không đủ nhãn cùng phiên cho kiểm định; không nối mục tiêu trong ngày qua phiên sau.");
        double balanced=0; int classes=0;
        for(int j=0;j<3;j++) if(actual[j]>0){balanced+=(double)hits[j]/actual[j];classes++;}
        balanced/=Math.max(1,classes);
        Model model=fit(features,labels,start,end);
        double[] p=model.predict(features[candles.size()-1]);
        boolean qualified=classes==3 && Arrays.stream(actual).min().orElse(0)>=10 && count>=60
                && (double)correct/count > (double)baseline/count && balanced > 1.0/3;
        String signal=qualified?(p[2]>=buyThreshold?"BUY":p[0]>=sellThreshold?"SELL":"WATCH"):"WATCH";
        List<FeatureWeight> weights=new ArrayList<>();
        for(int j=0;j<NAMES.length;j++) weights.add(new FeatureWeight(NAMES[j],model.w[2][j+1],model.w[0][j+1]));
        int trained=0;for(int i=start;i<end;i++)if(labels[i]>=0)trained++;
        return new Learning(VERSION,qualified?"EXPERIMENTAL":"VALIDATION_WEAK",signal,h,trained,count,
                (double)correct/count,balanced,(double)baseline/count,loss/count,p[2],p[1],p[0],
                candles.get(end-1+h).time(),weights,List.copyOf(folds),
                "Softmax 3 lớp: giảm / trung tính / tăng. Kiểm định mở rộng 3 đoạn theo thời gian, purge " + h
                + " nến; chuẩn hóa chỉ trên tập huấn luyện. Nhãn từ open nến sau đến close sau " + h
                + " nến, chi phí giả định " + cost*100 + "%. Điểm mô hình chưa hiệu chuẩn, không phải xác suất lời."
                + " Đây là kiểm định phân loại, chưa mô phỏng lợi nhuận, thanh toán hoặc khả năng khớp lệnh.");
    }
    private static boolean sameSession(Candle a,Candle b){var zone=java.time.ZoneId.of("Asia/Ho_Chi_Minh");return java.time.Instant.ofEpochSecond(a.time()).atZone(zone).toLocalDate().equals(java.time.Instant.ofEpochSecond(b.time()).atZone(zone).toLocalDate());}
    private Learning unavailable(int h,String reason){return new Learning(VERSION,"INSUFFICIENT_DATA","UNAVAILABLE",h,0,0,null,null,null,null,null,null,null,null,List.of(),List.of(),reason);}
    public static double[] features(List<Candle> c,int i){
        double close=c.get(i).close(), fast=ema(c,i,9), slow=ema(c,i,21), volume=0;
        for(int j=i-20;j<i;j++)volume+=c.get(j).volume();
        return new double[]{close/c.get(i-1).close()-1,close/c.get(i-5).close()-1,
                fast/slow-1,(rsi(c,i)-50)/50,atr(c,i)/close,Math.log1p(c.get(i).volume()/Math.max(1,volume/20))};
    }
    /** Same recurrences and arithmetic as features(), computed once per chronological prefix. */
    static double[][] featureMatrix(List<Candle> candles,int start) {
        double[][] result=new double[candles.size()][];if(candles.isEmpty())return result;
        double fast=candles.get(0).close(),slow=fast,gain=0,loss=0;
        double fastAlpha=2.0/(9+1),slowAlpha=2.0/(21+1);
        for(int i=1;i<candles.size();i++) {
            double close=candles.get(i).close(),change=close-candles.get(i-1).close();
            fast+=fastAlpha*(close-fast);slow+=slowAlpha*(close-slow);
            if(i<=14){gain+=Math.max(0,change)/14;loss+=Math.max(0,-change)/14;}
            else {gain=(gain*13+Math.max(0,change))/14;loss=(loss*13+Math.max(0,-change))/14;}
            if(i<start)continue;
            double rsi=gain+loss==0?50:loss==0?100:100-100/(1+gain/loss),volume=0;
            for(int j=i-20;j<i;j++)volume+=candles.get(j).volume();
            result[i]=new double[]{close/candles.get(i-1).close()-1,close/candles.get(i-5).close()-1,
                    fast/slow-1,(rsi-50)/50,atr(candles,i)/close,Math.log1p(candles.get(i).volume()/Math.max(1,volume/20))};
        }
        return result;
    }
    public static Indicator indicators(List<Candle> c,Mode mode){
        int i=c.size()-1; double support=Double.POSITIVE_INFINITY,resistance=0;
        for(int j=Math.max(0,i-20);j<i;j++){support=Math.min(support,c.get(j).low());resistance=Math.max(resistance,c.get(j).high());}
        return new Indicator(ema(c,i,mode==Mode.SWING?20:9),ema(c,i,mode==Mode.SWING?50:21),rsi(c,i),atr(c,i),support,resistance);
    }
    private static double ema(List<Candle> c,int i,int period){return StockDailyDesk.ema(c,i,period);}
    private static double rsi(List<Candle> c,int i){return StockDailyDesk.wilderRsi(c,i);}
    private static double atr(List<Candle> c,int i){double sum=0;for(int j=i-13;j<=i;j++){Candle b=c.get(j);double p=c.get(j-1).close();sum+=Math.max(b.high()-b.low(),Math.max(Math.abs(b.high()-p),Math.abs(b.low()-p)));}return sum/14;}
    private static Model fit(double[][] x,int[] y,int from,int to){
        int d=NAMES.length,n=0;for(int i=from;i<to;i++)if(y[i]>=0)n++;n=Math.max(1,n);
        double[] mean=new double[d],scale=new double[d];double[][] w=new double[3][d+1];
        for(int i=from;i<to;i++)if(y[i]>=0)for(int j=0;j<d;j++)mean[j]+=x[i][j]/n;
        for(int i=from;i<to;i++)if(y[i]>=0)for(int j=0;j<d;j++)scale[j]+=Math.pow(x[i][j]-mean[j],2)/n;
        for(int j=0;j<d;j++)scale[j]=Math.max(1e-8,Math.sqrt(scale[j]));
        Model m=new Model(w,mean,scale);
        double[][] transformed=new double[to-from][];
        for(int i=from;i<to;i++)if(y[i]>=0)transformed[i-from]=m.transform(x[i]);
        double[][] grad=new double[3][d+1];double[] probabilities=new double[3];
        for(int epoch=0;epoch<180;epoch++){
            for(var row:grad)Arrays.fill(row,0);
            for(int i=from;i<to;i++){if(y[i]<0)continue;double[] z=transformed[i-from];m.predictInto(z,probabilities);for(int k=0;k<3;k++)for(int j=0;j<=d;j++)grad[k][j]+=(probabilities[k]-(y[i]==k?1:0))*z[j]/n;}
            for(int k=0;k<3;k++)for(int j=0;j<=d;j++)w[k][j]-=.08*(grad[k][j]+(j==0?0:.02*w[k][j]));
        }
        return m;
    }
    private record Model(double[][] w,double[] mean,double[] scale){
        double[] transform(double[] x){double[] z=new double[x.length+1];z[0]=1;for(int j=0;j<x.length;j++)z[j+1]=Math.max(-6,Math.min(6,(x[j]-mean[j])/scale[j]));return z;}
        double[] predict(double[] x){double[] p=new double[3];predictInto(transform(x),p);return p;}
        void predictInto(double[] z,double[] p){Arrays.fill(p,0);for(int k=0;k<3;k++)for(int j=0;j<z.length;j++)p[k]+=w[k][j]*z[j];double max=Double.NEGATIVE_INFINITY,sum=0;for(double v:p)max=Math.max(max,v);for(int k=0;k<3;k++){p[k]=Math.exp(p[k]-max);sum+=p[k];}for(int k=0;k<3;k++)p[k]/=sum;}
    }
    private static int argmax(double[] v){int a=0;for(int i=1;i<v.length;i++)if(v[i]>v[a])a=i;return a;}
    private static int argmax(int[] v){int a=0;for(int i=1;i<v.length;i++)if(v[i]>v[a])a=i;return a;}
}
