package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.LyricsResults;
import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.TimedLyrics;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class TimedResultsPersistenceTest {
    private static final Song SONG=new Song("yt","Song","Artist","Album",180000);
    private static Candidate plain(){return new Candidate("qqmusic","plain","Song","Artist","Album",180000,"自写纯文本",null,false);}
    private static Candidate line(){return new Candidate("qqmusic","line","Song","Artist","Album",180000,"原始纯文本","[00:01]自写歌词",false);}
    private static void legacyPool(MemoryPreferences prefs,Candidate... values) throws Exception {
        var encoded=new JSONArray();for(var value:values)encoded.put(BindingStore.encode(value));
        prefs.edit().putString("pool:yt",new JSONObject().put("title","Song").put("artist","Artist")
                .put("policy","p").put("time",1000).put("complete",true).put("candidates",encoded).toString()).apply();
    }
    @Test public void legacyMixedPoolsCannotReintroducePlainTextOrSelectItsBetterAlbum() throws Exception {
        var prefs=new MemoryPreferences();legacyPool(prefs,plain(),line());
        var pool=new PreloadCache(prefs,()->2000).get(SONG,"p");
        assertNotNull(pool);assertEquals(List.of("line"),pool.candidates().stream().map(Candidate::id).toList());
        assertEquals("line",pool.best(SONG).id());
        assertEquals(List.of(line()),new PreloadCache.Pool(List.of(plain(),line()),true).candidates());
    }
    @Test public void plainOnlyLegacyPoolIsACacheMissAndTimedWritesExcludePlainText() throws Exception {
        var prefs=new MemoryPreferences();legacyPool(prefs,plain());
        var cache=new PreloadCache(prefs,()->2000);
        assertNull(cache.get(SONG,"p"));
        cache.put(SONG,"p",List.of(plain(),line()),true);
        assertEquals(1,new JSONObject(prefs.getString("pool:yt","")).getJSONArray("candidates").length());
        cache.put(SONG,"p",List.of(plain()),false);
        assertEquals("line",cache.get(SONG,"p").best(SONG).id());
    }
    @Test public void plainBindingSurvivesFilteringAndSameIdEnhancementCanMakeItEligible() {
        var prefs=new MemoryPreferences();var store=new BindingStore(prefs);store.put(SONG,plain());
        Candidate cached=store.get(SONG);
        assertTrue(LyricsResults.timed(List.of(cached)).isEmpty());
        assertEquals(cached,new BindingStore(prefs).get(SONG));
        var enhanced=new Candidate("qqmusic","plain","Returned title","Returned artist","",180000,null,null,false,
                TimedLyrics.yrc("[1000,1000](1000,1000,0)自写歌词"),"").preserveCache(cached);
        assertTrue(LyricsResults.displayable(enhanced));
        assertEquals(cached.id(),enhanced.id());assertEquals(cached.sourceId(),enhanced.sourceId());
        assertEquals(cached.title(),enhanced.title());
        var failed=new Candidate("qqmusic","plain","Song","Artist","Album",180000,null,null,false,List.of(),"取词失败")
                .preserveCache(cached);
        assertFalse(LyricsResults.displayable(failed));assertEquals(cached,new BindingStore(prefs).get(SONG));
    }
}
