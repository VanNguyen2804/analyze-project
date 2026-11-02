package com.example.analyzeproject.domain.service;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PairRelationshipModelTest {
    @Test void equalFrequenciesCanHaveDifferentPairScoresAndContextChangesTheResult() {
        int[] frequency = new int[46]; int[][] pairs = new int[46][46];
        for(var ticket:List.of(List.of(1,2,3,4,5,7),List.of(6,8,9,10,11,12))) {
            for(int number:ticket) { frequency[number]+=4; for(int partner:ticket) if(number!=partner) pairs[number][partner]+=4; }
        }
        var result = PairRelationshipModel.score(45,8,frequency,pairs,List.of(1,2,3,4,5,6),8,3);
        assertEquals(frequency[7],frequency[8]);
        assertTrue(result.scores()[7]>0); assertTrue(result.scores()[8]<0);
        assertEquals(6,result.supportedPairs()[7]);
        var changed = PairRelationshipModel.score(45,8,frequency,pairs,List.of(9,10,11,12,13,14),8,3);
        assertTrue(changed.scores()[8]>changed.scores()[7]);
        var shuffled = PairRelationshipModel.score(45,8,frequency,pairs,List.of(6,5,4,3,2,1,1),8,3);
        assertArrayEquals(result.scores(),shuffled.scores());
    }

    @Test void uniformFixedSixNullHasZeroResidualForBothCategories() {
        for(int max:List.of(45,55)) {
            int[] frequency=new int[max+1]; int[][] pairs=new int[max+1][max+1];
            Arrays.fill(frequency,6*(max-1));
            for(int i=1;i<=max;i++) for(int j=1;j<=max;j++) if(i!=j) pairs[i][j]=30;
            var result=PairRelationshipModel.score(max,max*(max-1),frequency,pairs,List.of(1,2,3,4,5,6),8,3);
            for(double score:result.scores()) assertEquals(0,score,1e-12);
        }
    }

    @Test void aContextNumberPresentInEveryDrawCannotCreatePairEvidence() {
        int[] frequency=new int[46]; int[][] pairs=new int[46][46];
        frequency[1]=20; frequency[7]=4; pairs[7][1]=pairs[1][7]=4;
        var result=PairRelationshipModel.score(45,20,frequency,pairs,List.of(1),8,3);
        assertEquals(0,result.scores()[7]);
    }

    @Test void insufficientSupportAndNoHistoryStayNeutralAndSmoothingReducesMagnitude() {
        int[] frequency=new int[46]; int[][] pairs=new int[46][46];
        frequency[1]=frequency[7]=2; pairs[7][1]=pairs[1][7]=2;
        assertEquals(0,PairRelationshipModel.score(45,10,frequency,pairs,List.of(1),8,3).scores()[7]);
        frequency[1]=frequency[7]=4; pairs[7][1]=pairs[1][7]=4;
        double weak=PairRelationshipModel.score(45,20,frequency,pairs,List.of(1),1,3).scores()[7];
        double strong=PairRelationshipModel.score(45,20,frequency,pairs,List.of(1),100,3).scores()[7];
        assertTrue(strong>0 && strong<weak);
        assertTrue(Arrays.stream(PairRelationshipModel.score(45,0,frequency,pairs,List.of(1),8,3).scores()).allMatch(s->s==0));
        assertEquals(0,PairRelationshipModel.score(45,20,frequency,pairs,List.of(1),8,5).scores()[7]);
        assertEquals(0,PairRelationshipModel.score(45,20,frequency,pairs,List.of(),8,3).scores()[7]);
        assertEquals(0,PairRelationshipModel.score(45,20,frequency,pairs,List.of(7),8,3).scores()[7]);
        var defaults=PairRelationshipModel.score(45,20,frequency,pairs,List.of(1),8,3);
        assertArrayEquals(defaults.scores(),PairRelationshipModel.score(45,20,frequency,pairs,List.of(1),Double.NaN,-1).scores());
    }
}
