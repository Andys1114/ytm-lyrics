package dev.ytmlyrics.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricsOffsetTest {
    @Test public void positiveAdvanceShowsALineEarlierAndClickMapsBackToMedia(){
        assertEquals(10000,LyricsOffset.at(9500,500));
        assertEquals(9500,LyricsOffset.seek(10000,500,30000));
        assertEquals(10000,LyricsOffset.at(10500,-500));
        assertEquals(10500,LyricsOffset.seek(10000,-500,30000));
    }
    @Test public void negativeClockBeforeSongStartIsRetainedAndSeekStaysInSong(){
        assertEquals(-500,LyricsOffset.at(0,-500));
        assertEquals(0,LyricsOffset.seek(100,500,30000));
        assertEquals(30000,LyricsOffset.seek(29900,-500,30000));
        assertEquals(30400,LyricsOffset.seek(29900,-500,0));
    }
    @Test public void boundariesAndDirectionLabelsAreExplicit(){
        assertEquals(10000,LyricsOffset.clamp(Integer.MAX_VALUE));
        assertEquals(-10000,LyricsOffset.clamp(Integer.MIN_VALUE));
        assertEquals("不偏移",LyricsOffset.label(0));
        assertEquals("提前 0.1 秒",LyricsOffset.label(100));
        assertEquals("延后 0.5 秒",LyricsOffset.label(-500));
        assertEquals("提前 10.0 秒",LyricsOffset.label(20000));
    }
    @Test public void sliderUsesFortyHalfSecondIntervalsAndBounds(){
        assertEquals(500,LyricsOffset.STEP_MS);
        for(int i=0;i<=40;i++){
            int value=-10000+i*500;
            assertEquals(value,LyricsOffset.sliderValue(i));
            assertEquals(i,LyricsOffset.sliderProgress(value));
        }
        assertEquals(-10000,LyricsOffset.sliderValue(Integer.MIN_VALUE));
        assertEquals(10000,LyricsOffset.sliderValue(Integer.MAX_VALUE));
        assertEquals(0,LyricsOffset.sliderProgress(Integer.MIN_VALUE));
        assertEquals(40,LyricsOffset.sliderProgress(Integer.MAX_VALUE));
    }
    @Test public void compactReadoutsUseSignedSecondsWithoutGrowingAtWholeSeconds(){
        assertEquals("0s",LyricsOffset.compactLabel(0));
        assertEquals("+3s",LyricsOffset.compactLabel(3000));
        assertEquals("−3s",LyricsOffset.compactLabel(-3000));
        assertEquals("+0.5s",LyricsOffset.compactLabel(500));
        assertEquals("−0.1s",LyricsOffset.compactLabel(-100));
        assertEquals("+10s",LyricsOffset.compactLabel(Integer.MAX_VALUE));
        assertEquals("−10s",LyricsOffset.compactLabel(Integer.MIN_VALUE));
    }
    @Test public void wheelTurnsFromItsGestureOriginAndStopsAtTheBounds(){
        assertEquals(100,LyricsOffset.stepBy(100,0));
        assertEquals(500,LyricsOffset.stepBy(100,1));
        assertEquals(1500,LyricsOffset.stepBy(100,3));
        assertEquals(0,LyricsOffset.stepBy(100,-1));
        assertEquals(-1000,LyricsOffset.stepBy(100,-3));
        assertEquals(1000,LyricsOffset.stepBy(-100,3));
        assertEquals(-1500,LyricsOffset.stepBy(-100,-3));
        assertEquals(10000,LyricsOffset.stepBy(9500,2));
        assertEquals(-10000,LyricsOffset.stepBy(-9500,-2));
        assertEquals(-10000,LyricsOffset.stepBy(0,Integer.MIN_VALUE));
        assertEquals(10000,LyricsOffset.stepBy(0,Integer.MAX_VALUE));
    }
    @Test public void nudgesStayOnTheGridWithoutReinterpretingStoredFineOffsets(){
        assertEquals(100,LyricsOffset.clamp(100));assertEquals(10100,LyricsOffset.at(10000,100));
        assertEquals("提前 0.1 秒",LyricsOffset.label(100));
        for(int value:new int[]{-10000,-9500,-500,0,500,9500,10000}){
            assertEquals(LyricsOffset.clamp(value+500),LyricsOffset.nudge(value,1));
            assertEquals(LyricsOffset.clamp(value-500),LyricsOffset.nudge(value,-1));
        }
        assertEquals(500,LyricsOffset.nudge(100,1));assertEquals(0,LyricsOffset.nudge(100,-1));
        assertEquals(0,LyricsOffset.nudge(-100,1));assertEquals(-500,LyricsOffset.nudge(-100,-1));
        assertEquals(100,LyricsOffset.nudge(100,0));
    }
}
