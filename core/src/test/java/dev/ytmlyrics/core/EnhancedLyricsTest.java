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
    @Test public void sparseTranslationDoesNotGetStolenByAnEarlierLine() {
        var lines=TimedLyrics.yrc("[1000,400](1000,400,0)第一句\n[1600,400](1600,400,0)第二句");
        var translated=TimedLyrics.translate(lines,"[00:01.600]Second");
        assertEquals("",translated.get(0).translation());assertEquals("Second",translated.get(1).translation());
        for(int i=0;i<lines.size();i++) {
            assertEquals(lines.get(i).startMs(),translated.get(i).startMs());
            assertEquals(lines.get(i).endMs(),translated.get(i).endMs());
            assertEquals(lines.get(i).words(),translated.get(i).words());
        }
    }
    @Test public void exactAndNearbyTranslationsCompeteOnlyForTheirNearestLine() {
        var lines=TimedLyrics.fromLrc("[00:01]第一句\n[00:01.600]第二句\n[00:03]第三句",10000);
        var translated=TimedLyrics.translate(lines,"[00:01.500]Near second\n[00:01.600]Exact second\n[00:02.950]Third");
        assertEquals(List.of("","Exact second","Third"),translated.stream().map(TimedLyrics.Line::translation).toList());
        var nearby=TimedLyrics.translate(lines,"[00:01.500]Nearest second\n[00:01.900]Farther second");
        assertEquals(List.of("","Nearest second",""),nearby.stream().map(TimedLyrics.Line::translation).toList());
    }
    @Test public void translationToleranceIncludesOneSecondButNotMore() {
        var lines=TimedLyrics.fromLrc("[00:01]第一句\n[00:04]第二句\n[00:07]第三句",10000);
        var translated=TimedLyrics.translate(lines,"[00:00]First\n[00:05]Second\n[00:08.001]Too far");
        assertEquals(List.of("First","Second",""),translated.stream().map(TimedLyrics.Line::translation).toList());
    }
    @Test public void simultaneousLinesKeepStableOneToOneTranslationsWithoutUsingFartherLines() {
        var lines=TimedLyrics.fromLrc("[00:01]第一句\n[00:01]第二句\n[00:01.600]第三句",10000);
        var translated=TimedLyrics.translate(lines,"[00:01]First\n[00:01]Second\n[00:01]Excess");
        assertEquals(List.of("First","Second",""),translated.stream().map(TimedLyrics.Line::translation).toList());
    }
    @Test public void exactTranslationsKeepTheirOrderWhenSimultaneousLinesAlsoHaveNearbyTranslations() {
        var lines=TimedLyrics.fromLrc("[00:01]第一句\n[00:01]第二句",10000);
        var translated=TimedLyrics.translate(lines,"[00:00.950]Nearby\n[00:01]First exact\n[00:01]Second exact");
        assertEquals(List.of("First exact","Second exact"),translated.stream().map(TimedLyrics.Line::translation).toList());
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
    @Test public void lineOnlyRefreshFillsNewTranslationsWithoutDiscardingCachedWordsOrBinding() {
        var words=TimedLyrics.yrc("[1000,1000](1000,1000,0)自写\n[3000,1000](3000,1000,0)歌词");
        var old=new Candidate("qqmusic","1","Chosen song","Chosen artist","Chosen album",10000,null,null,false,
                TimedLyrics.translate(words,"[00:01]Cached original"),"");
        var fresh=new Candidate("qqmusic","1","Changed title","Changed artist","Changed album",11000,null,"[00:01]自写\n[00:03]歌词",false,
                TimedLyrics.translate(TimedLyrics.fromLrc("[00:01]自写\n[00:03]歌词",11000),"[00:01]Fresh original\n[00:03]New lyrics"),"QRC unavailable");
        var merged=fresh.preserveCache(old);
        assertTrue(merged.hasWords());assertEquals(List.of("Cached original","New lyrics"),merged.timeline().stream().map(TimedLyrics.Line::translation).toList());
        assertEquals(old.sourceId(),merged.sourceId());assertEquals(old.id(),merged.id());assertEquals(old.title(),merged.title());
        assertEquals(old.artist(),merged.artist());assertEquals(old.album(),merged.album());assertEquals(old.durationMs(),merged.durationMs());
        assertEquals(fresh.enhancementError(),merged.enhancementError());
        for(int i=0;i<words.size();i++) {
            assertEquals(words.get(i).startMs(),merged.timeline().get(i).startMs());
            assertEquals(words.get(i).endMs(),merged.timeline().get(i).endMs());
            assertEquals(words.get(i).words(),merged.timeline().get(i).words());
        }
    }
    @Test public void cachedTranslationsFillGapsInPartiallyTranslatedFreshWords() {
        var words=TimedLyrics.yrc("[1000,1000](1000,1000,0)自写\n[3000,1000](3000,1000,0)歌词");
        var old=candidate("qqmusic","1",null,TimedLyrics.translate(words,"[00:01]Cached original\n[00:03]Cached lyrics"));
        var fresh=candidate("qqmusic","1",null,TimedLyrics.translate(words,"[00:03]Fresh lyrics"));
        assertEquals(List.of("Cached original","Fresh lyrics"),fresh.preserveCache(old).timeline().stream().map(TimedLyrics.Line::translation).toList());
    }
    @Test public void refreshTranslationsRequireMatchingOriginalTextAndNearbyTime() {
        var old=candidate("qqmusic","1",null,TimedLyrics.yrc("[1000,1000](1000,1000,0)自写\n[3000,1000](3000,1000,0)歌词"));
        var fresh=candidate("qqmusic","1",null,TimedLyrics.translate(TimedLyrics.fromLrc("[00:01]不同原文\n[00:04.001]歌词",10000),
                "[00:01]Wrong text\n[00:04.001]Wrong time"));
        assertFalse(fresh.preserveCache(old).hasTranslation());
        try{candidate("qqmusic","other",null,List.of()).preserveCache(old);fail();}
        catch(IllegalArgumentException expected){assertNotNull(expected.getMessage());}
    }
    @Test public void refreshDoesNotReuseTranslationForRepeatedOriginalText() {
        var old=candidate("qqmusic","1",null,TimedLyrics.yrc("[1000,400](1000,400,0)重复\n[1600,400](1600,400,0)重复"));
        var fresh=candidate("qqmusic","1",null,TimedLyrics.translate(TimedLyrics.fromLrc("[00:01.600]重复",10000),"[00:01.600]Second"));
        assertEquals(List.of("","Second"),fresh.preserveCache(old).timeline().stream().map(TimedLyrics.Line::translation).toList());
    }
    @Test public void refreshFillsSimultaneousRepeatedLinesWithoutOverwritingExistingTranslation() {
        var words=TimedLyrics.yrc("[1000,400](1000,400,0)重复\n[1000,400](1000,400,0)重复");
        var old=candidate("qqmusic","1",null,TimedLyrics.translate(words,"[00:01]Cached first"));
        var supplement=List.of(new TimedLyrics.Line(1000,1000,"重复","Fresh first",List.of()),
                new TimedLyrics.Line(1000,10000,"重复","Fresh second",List.of()));
        var fresh=candidate("qqmusic","1",null,supplement);
        var merged=fresh.preserveCache(old);
        assertEquals(List.of("Cached first","Fresh second"),merged.timeline().stream().map(TimedLyrics.Line::translation).toList());
        assertEquals(words.get(0).words(),merged.timeline().get(0).words());assertEquals(words.get(1).words(),merged.timeline().get(1).words());
    }
    @Test public void blankSupplementLinesKeepTheirPlaceAmongSimultaneousRepeatedOriginals() {
        var words=TimedLyrics.yrc("[1000,400](1000,400,0)重复\n[1000,400](1000,400,0)重复");
        var old=candidate("qqmusic","1",null,TimedLyrics.translate(words,"[00:01]Cached first"));
        var supplement=List.of(new TimedLyrics.Line(1000,1000,"重复","",List.of()),
                new TimedLyrics.Line(1000,10000,"重复","Fresh second",List.of()));
        var fresh=candidate("qqmusic","1",null,supplement);
        assertEquals(List.of("Cached first","Fresh second"),fresh.preserveCache(old).timeline().stream().map(TimedLyrics.Line::translation).toList());
    }
}
