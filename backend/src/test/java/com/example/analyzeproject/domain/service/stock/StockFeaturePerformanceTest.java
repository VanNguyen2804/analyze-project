package com.example.analyzeproject.domain.service.stock;

import com.example.analyzeproject.domain.model.stock.StockModels.Candle;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StockFeaturePerformanceTest {
    @Test void incrementalFeaturesExactlyMatchTheOriginalPrefixFormulaIncludingFlatAndVolatilePrices() {
        for(boolean flat:List.of(true,false)) {
            var candles=new ArrayList<Candle>();
            for(int i=0;i<450;i++){double price=flat?100:100+Math.sin(i*.7)*8+i*.02;
                candles.add(new Candle(1700000000L+i*900,price,price+2,price-2,price,1000+i,true));}
            var values=StockLearningEngine.featureMatrix(candles,55);
            for(int i=55;i<candles.size();i++)assertArrayEquals(StockLearningEngine.features(candles,i),values[i],0);
            var prefix=StockLearningEngine.featureMatrix(candles.subList(0,300),55);
            for(int i=55;i<300;i++)assertArrayEquals(prefix[i],values[i],0);
        }
    }
}
