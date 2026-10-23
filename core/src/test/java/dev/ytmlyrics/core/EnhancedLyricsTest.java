package dev.ytmlyrics.core;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnhancedLyricsTest {
    private static Candidate candidate(String provider,String id,String lrc,List<TimedLyrics.Line> enhanced) {
        return new Candidate(provider,id,"Song","Artist","",10000,null,lrc,false,enhanced,"");
    }
    private static LyricsSource source(String id,Candidate... values) {
        return new LyricsSource(){public String id(){return id;}public String name(){return id;}
            public List<Candidate> search(Song song,String query){return List.of(values);}};
    }
    @Test public void yrcAndQrcUseAbsoluteMillisecondWordsWithoutSplittingEnglish() {
        var y=TimedLyrics.yrc("{\"metadata\":1}\n[1000,1000](1000,400,0)Hello, (1400,600,0)world!");
        var q=TimedLyrics.qrc("[1000,1000]Hello, (1000,400)world!(1400,600)");
        assertEquals(y,q);assertEquals("Hello, world!",y.get(0).text());
        assertEquals(1400,y.get(0).words().get(1).startMs());assertEquals(2000,y.get(0).endMs());
    }
    @Test public void noWordTimelineIsInventedForLrc() {
        var line=TimedLyrics.fromLrc("[00:01]自写\n[00:03]下一句",6000).get(0);
        assertTrue(line.words().isEmpty());assertEquals(3000,line.endMs());
        assertFalse(candidate("x","1","[00:01]自写",List.of()).hasWords());
    }
    @Test public void translationsMatchTimestampsAndAreNotReusedOrAttachedByIndex() {
        var lines=TimedLyrics.yrc("[1000,1000](1000,1000,0)自写\n[3000,1000](3000,1000,0)下一句");
        var translated=TimedLyrics.translate(lines,"[00:00.950]Original\n[00:03]Next\n[00:40]Unrelated");
        assertEquals("Original",translated.get(0).translation());assertEquals("Next",translated.get(1).translation());
        assertEquals("",TimedLyrics.translate(lines,"[00:40]Wrong").get(0).translation());
        assertEquals("",lines.get(0).translation());
    }
    @Test public void invalidWordOrderOrUnparsedTailIsReported() {
        for(String data:List.of("[1000,1000](900,400,0)bad","[1000,1000](1400,100,0)a(1200,100,0)b")) {
            try{TimedLyrics.yrc(data);fail();}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}
        }
        try{TimedLyrics.qrc("[1000,1000]a(1000,400)trailing");fail();}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}
    }
    @Test public void lowerPriorityWordsBeatLinesAndSameQualityKeepsSourceOrder() {
        var line=candidate("first","1","[00:01]自写",List.of());
        var word=candidate("second","2",null,TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"));
        Song song=new Song("yt","Song","Artist","",10000);
        assertSame(word,new Resolver().resolve(song,List.of(source("first",line),source("second",word)),()->false).selected());
        assertSame(line,new Resolver().resolve(song,List.of(source("first",line),source("third",candidate("third","3","[00:01]其他",List.of()))),()->false).selected());
    }
    @Test public void equivalentRecordingCandidatesAutomaticallyPreferWords() {
        var first=candidate("first","1","[00:01]自写",List.of());
        var second=candidate("first","2",null,TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"));
        assertSame(second,new Resolver().resolve(new Song("yt","Song","Artist","",10000),List.of(source("first",first,second)),()->false).selected());
    }
    @Test public void usableLinesCanDisplayWhileLaterWordSourcesAreStillLoading() {
        var line=candidate("first","1","[00:01]自写",List.of());
        var word=candidate("second","2",null,TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"));
        var updates=new java.util.ArrayList<Candidate>();
        var result=new Resolver().resolve(new Song("yt","Song","Artist","",10000),
                List.of(source("first",line),source("second",word)),()->false,updates::add);
        assertEquals(List.of(line,word),updates);assertSame(word,result.selected());
    }
    @Test public void failedEnrichmentKeepsWordsAndTranslationsButCannotChangeBinding() {
        var old=candidate("qqmusic","1",null,TimedLyrics.translate(TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"),"[00:01]Original"));
        var fetched=candidate("qqmusic","1","[00:01]自写",List.of());
        var kept=fetched.preserveCache(old);assertTrue(kept.hasWords());assertTrue(kept.hasTranslation());
        try{candidate("netease","2",null,List.of()).preserveCache(old);fail();}catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}
    }
    @Test public void addingWordsDoesNotDiscardCachedSourceTranslation() {
        var old=candidate("qqmusic","1","[00:01]自写",TimedLyrics.translate(TimedLyrics.fromLrc("[00:01]自写",10000),"[00:01]Original"));
        var fresh=candidate("qqmusic","1",null,TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"));
        var merged=fresh.preserveCache(old);assertTrue(merged.hasWords());assertEquals("Original",merged.timeline().get(0).translation());
    }
}
