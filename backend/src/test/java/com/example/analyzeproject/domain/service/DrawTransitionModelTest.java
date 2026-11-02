package com.example.analyzeproject.domain.service;

import com.example.analyzeproject.domain.model.LotteryNumber;
import com.example.analyzeproject.application.service.TestDraws;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DrawTransitionModelTest {
    private final List<Integer> a=List.of(1,2,3,4,5,6), b=List.of(20,21,22,23,24,25);
    private LotteryNumber draw(int day,List<Integer> numbers) {
        return TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,day),"POWER",numbers,55,null));
    }

    @Test void neverLearnsAcrossMissingScheduledDraws() {
        var result=DrawTransitionModel.fit(List.of(draw(1,a),draw(6,b)),"POWER");
        assertEquals(0,result.learnedTransitions()); assertEquals(1,result.skippedGapTransitions());
        assertTrue(Arrays.stream(result.scores()).allMatch(s->s==0));
        assertTrue(Arrays.stream(DrawTransitionModel.fit(List.of(),"POWER").scores()).allMatch(s->s==0));
    }

    @Test void usesActualSourceExposuresAndDoesNotUseOlderContextAcrossAGap() {
        var history=new ArrayList<>(List.of(draw(1,a),draw(3,b),draw(8,a)));
        var result=DrawTransitionModel.fit(history,"POWER");
        assertEquals(1,result.learnedTransitions()); assertEquals(1,result.skippedGapTransitions());
        for(int n:b) assertEquals(4.2,result.scores()[n],1e-12);
        for(int n:a) assertEquals(0,result.scores()[n]);
        Collections.reverse(history);
        history.add(history.get(0));
        assertArrayEquals(result.scores(),DrawTransitionModel.fit(history,"POWER").scores());
    }

    @Test void includesOlderContextOnlyForConsecutiveDrawsAndFiltersOtherCategories() {
        var first=draw(1,a); var second=draw(3,b);
        var other=TestDraws.verified(new LotteryNumber(LocalDate.of(2026,10,2),"MEGA",a,null,null));
        var result=DrawTransitionModel.fit(List.of(first,other,second),"POWER");
        for(int n:b) assertEquals(1.8,result.scores()[n],1e-12);
        assertEquals(1,result.learnedTransitions()); assertEquals(0,result.skippedGapTransitions());
    }
}
