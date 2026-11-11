package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class LyricsResultsTest {
    private static final Song SONG=new Song("yt","Song","Artist","Album",180000);
    private static Candidate lyric(String id,String plain,String synced) {
        return new Candidate("test",id,"Song","Artist","Album",180000,plain,synced,false);
    }
    private static LyricsSource source(Candidate... candidates) {
        return new LyricsSource() {
            public String id(){return "test";}
            public String name(){return "Test";}
            public List<Candidate> search(Song song,String query){return List.of(candidates);}
        };
    }
    @Test public void filterKeepsRealLineAndWordTimelinesInOriginalOrder() {
        var plain=lyric("plain","自写纯文本",null);
        var line=lyric("line","原始纯文本同时保留","[00:00]自写逐行歌词");
        var word=new Candidate("test","word","Song","Artist","Album",180000,null,null,false,
                TimedLyrics.yrc("[1000,1000](1000,1000,0)自写逐字歌词"),"");
        assertEquals(List.of(line,word),LyricsResults.timed(List.of(plain,line,word)));
        assertTrue(plain.hasLyrics()); // Legacy serialization/data meaning is unchanged.
        assertFalse(LyricsResults.displayable(plain));assertFalse(LyricsResults.displayable(null));
        assertTrue(LyricsResults.displayable(line));assertTrue(LyricsResults.displayable(word));
        assertThrows(UnsupportedOperationException.class,()->LyricsResults.timed(List.of(line)).clear());
    }
    @Test public void malformedTimestampsDoNotTurnPlainTextIntoASyncedResult() {
        var invalid=lyric("invalid","自写纯文本","[artist:测试]\n[not-a-time]自写歌词");
        assertTrue(invalid.hasLyrics());assertTrue(LyricsResults.timed(List.of(invalid)).isEmpty());
    }
    @Test public void resolverNeverSelectsPublishesOrReturnsPlainTextEvenWithPerfectAlbumMatch() {
        var plain=lyric("plain","自写纯文本",null);
        var line=new Candidate("test","line","Song","Artist","Other album",180000,null,"[00:01]自写逐行歌词",false);
        var published=new ArrayList<Candidate>();
        var result=new Resolver().resolve(SONG,List.of(source(plain,line)),()->false,published::add);
        assertSame(line,result.selected());assertEquals(List.of(line),result.candidates());assertEquals(List.of(line),published);
        assertTrue(result.errors().isEmpty());
    }
    @Test public void plainOnlyResultsLeaveNoSelectableOrProvisionalCandidate() {
        var result=new Resolver().resolve(SONG,List.of(source(lyric("plain","自写纯文本",null))),()->false,
                candidate->fail("Plain text cannot be provisionally displayed"));
        assertNull(result.selected());assertTrue(result.candidates().isEmpty());assertFalse(result.cancelled());
        assertTrue(result.errors().isEmpty());
    }

    private static Candidate match(String source, String id, String title, String artist, String album, long duration) {
        return new Candidate(source,id,title,artist,album,duration,null,"[00:01]自写歌词",false);
    }
    @Test public void chooserInterleavesSourcesByRecordingEvidence() {
        var cover=match("netease","cover","Song (Cover)","Other artist","Other album",180000);
        var durationMatch=match("netease","duration","Song","Artist","Album",181700);
        var albumMatch=match("qq","album","Song","Artist","Album",180500);
        var unrelated=match("qq","unrelated","Another track","Artist","Album",180000);
        var incoming=List.of(cover,durationMatch,unrelated,albumMatch,lyric("plain","自写纯文本",null));
        var expected=List.of(albumMatch,durationMatch,cover,unrelated);
        assertEquals(expected,LyricsResults.ranked(SONG,"",incoming));
        assertEquals(expected,LyricsResults.ranked(SONG,SearchQuery.of(SONG,"").keywords(),
                List.of(albumMatch,unrelated,durationMatch,cover)));
    }
    @Test public void editedSearchRanksItsMatchesWithoutChangingAutomaticSelection() {
        var direct=match("netease","song","Song","Artist","Album",180000);
        var searched=match("qq","track","Another track","Another performer","Another album",0);
        assertEquals(List.of(searched,direct),LyricsResults.ranked(SONG,"Another track Another performer",List.of(direct,searched)));
        assertSame(direct,Resolver.select(SONG,List.of(direct,searched)));
    }
}
