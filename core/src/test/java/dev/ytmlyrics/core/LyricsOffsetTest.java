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
}
