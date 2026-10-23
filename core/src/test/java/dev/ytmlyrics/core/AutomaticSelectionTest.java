package dev.ytmlyrics.core;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class AutomaticSelectionTest {
    private static final Song SONG=new Song("yt","Song","Artist","Album",180000);
    private static Candidate lyric(String provider,String id,String title,String artist,String album,long duration,boolean words,String translations) {
        var lines=words ? TimedLyrics.yrc("[1000,1000](1000,500,0)自(1500,500,0)写\n[3000,1000](3000,1000,0)歌词")
                : TimedLyrics.fromLrc("[00:01]自写\n[00:03]歌词",duration);
        return new Candidate(provider,id,title,artist,album,duration,null,null,false,TimedLyrics.translate(lines,translations),"");
    }
    private static LyricsSource source(String id,Candidate... values) {
        return new LyricsSource(){public String id(){return id;}public String name(){return id;}
            public List<Candidate> search(Song song,String query){return List.of(values);}};
    }
    private static Candidate select(Candidate... candidates) {
        return new Resolver().resolve(SONG,List.of(source("test",candidates)),()->false).selected();
    }
    @Test public void laterSourceTranslationWinsAfterAnEarlierWordResult() {
        var first=lyric("qq","1","Song","Artist","Album",180000,true,"");
        var translated=lyric("netease","2","Song","Artist","Album",180000,true,"[00:01]Original\n[00:03]Lyrics");
        var result=new Resolver().resolve(SONG,List.of(source("qq",first),source("netease",translated)),()->false);
        assertSame(translated,result.selected());assertEquals(2,result.candidates().size());
    }
    @Test public void matchingAlbumAndDurationBandBeatUnrelatedEnhancedRelease() {
        var album=lyric("a","1","Song","Artist","Album",180000,false,"");
        var reissue=lyric("a","2","Song","Artist","Other album",180000,true,"[00:01]Original");
        assertSame(album,select(reissue,album));
        var distant=lyric("a","3","Song","Artist","Album",181500,true,"");
        assertSame(album,select(distant,album));
    }
    @Test public void subsecondRoundingDoesNotBeatWordsAndWordsBeatTranslatedLines() {
        var line=lyric("a","1","Song","Artist","Album",180000,false,"[00:01]Original\n[00:03]Lyrics");
        var word=lyric("a","2","Song","Artist","Album",180900,true,"");
        assertSame(word,select(line,word));
    }
    @Test public void fullerTranslationWinsButExactTiesKeepProviderAndSearchOrder() {
        var partial=lyric("a","1","Song","Artist","Album",180000,true,"[00:01]Original");
        var full=lyric("b","2","Song","Artist","Album",180000,true,"[00:01]Original\n[00:03]Lyrics");
        var tie=lyric("c","3","Song","Artist","Album",180000,true,"[00:01]Original\n[00:03]Lyrics");
        assertSame(full,select(partial,full,tie));
        var result=new Resolver().resolve(SONG,List.of(source("b",full),source("c",tie)),()->false);
        assertSame(full,result.selected());
    }
    @Test public void betterLyricsNeverOverrideWrongSingerVersionOrShortVideoDuration() {
        var right=lyric("a","1","Song","Artist","Album",180000,false,"");
        assertSame(right,select(lyric("a","2","Song (Live)","Artist","Album",180000,true,"[00:01]Original"),
                lyric("a","3","Song","Other artist","Album",180000,true,""),right));
        assertNull(select(lyric("a","4","Song","Artist","Album",280000,true,"")));
        assertFalse(Matching.reliable(new Song("yt","Song","Artist","Album",0),right));
    }
    @Test public void youtubePresentationLabelsAreIgnoredButRecordingLabelsAreNot() {
        var right=lyric("a","1","Song","Artist","Album",180000,true,"");
        assertTrue(Matching.reliable(new Song("yt","Song (Official Music Video)","Artist - Topic","Album",180000),right));
        for(String suffix:List.of("Live","Remix","Acoustic","Remastered","Instrumental"))
            assertFalse(Matching.reliable(new Song("yt","Song ("+suffix+")","Artist","Album",180000),right));
    }
    @Test public void searchOrderDoesNotHideABetterDurationMatch() {
        var first=lyric("a","1","Song","Artist","Album",180900,true,"");
        var closer=lyric("a","2","Song","Artist","Album",180100,true,"");
        assertSame(closer,select(first,closer));
    }
}
