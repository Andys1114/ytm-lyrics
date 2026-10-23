package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.TimedLyrics;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreloadCacheTest {
    private static Song queue(String id){return new Song(id,"Song","Artist","",0);}
    private static Song playing(String id,long duration){return new Song(id,"Song","Artist","Album",duration);}
    private static Candidate candidate(String id,long duration){return new Candidate("qqmusic",id,"Song","Artist","Album",duration,null,null,false,
            TimedLyrics.translate(TimedLyrics.yrc("[1000,1000](1000,500,0)自(1500,500,0)写"),"[00:01]Original"),"");}
    @Test public void unknownQueueDurationCachesCandidatesAndRealPlaybackSelectsCorrectVersion() {
        var prefs=new MemoryPreferences();var store=new PreloadCache(prefs,()->1000);
        store.put(queue("video"),"policy",List.of(candidate("long",280000),candidate("right",180000)),true);
        assertNull(store.get(queue("video"),"policy").best(queue("video")));
        var restored=new PreloadCache(prefs,()->2000).get(playing("video",180000),"policy");
        assertEquals("right",restored.best(playing("video",180000)).id());
        assertTrue(restored.best(playing("video",180000)).hasWords());assertTrue(restored.best(playing("video",180000)).hasTranslation());
        assertNull(restored.best(playing("video",157000)));
    }
    @Test public void changedSourcePolicyMetadataOrIdCannotUseOldPool() {
        var store=new PreloadCache(new MemoryPreferences(),()->1000);
        store.put(queue("video"),"qq,netease",List.of(candidate("1",180000)),true);
        assertNull(store.get(queue("video"),"netease,qq"));assertNull(store.get(queue("other"),"qq,netease"));
        assertNull(store.get(new Song("video","Song","Different Artist","",0),"qq,netease"));
    }
    @Test public void expirationAndCorruptionAreLocalToAutomaticCache() {
        var prefs=new MemoryPreferences();long[] now={1000};var store=new PreloadCache(prefs,()->now[0]);
        prefs.edit().putString("binding:manual","keep").putBoolean("enabled:qqmusic",false).apply();
        store.put(queue("1"),"p",List.of(candidate("1",180000)),false);assertFalse(store.get(queue("1"),"p").complete());
        now[0]+=24*60*60*1000L;assertNull(store.get(queue("1"),"p"));
        prefs.edit().putString("pool:2","broken").apply();assertNull(store.get(queue("2"),"p"));
        assertEquals("keep",prefs.getString("binding:manual",""));assertFalse(prefs.getBoolean("enabled:qqmusic",true));
    }
    @Test public void boundsEvictOldestAutomaticEntriesAndEmptyResultsDoNotEraseValidLyrics() {
        var prefs=new MemoryPreferences();long[] now={1000};var store=new PreloadCache(prefs,()->now[0]);
        for(int i=0;i<31;i++){now[0]++;store.put(queue(""+i),"p",List.of(candidate("1",180000)),true);}
        assertEquals(30,prefs.getAll().size());assertNull(store.get(queue("0"),"p"));assertNotNull(store.get(queue("30"),"p"));
        store.put(queue("30"),"p",List.of(),false);assertNotNull(store.get(queue("30"),"p"));
    }
    @Test public void manualBindingsRemainAuthoritativeAndSeparateFromAutomaticPools() {
        var prefs=new MemoryPreferences();var bindings=new BindingStore(prefs);var cache=new PreloadCache(prefs,()->1000);
        bindings.put(queue("1"),candidate("manual",180000));cache.put(queue("1"),"p",List.of(candidate("auto",180000)),true);
        assertEquals("manual",bindings.get(queue("1")).id());cache.remove(queue("1"));assertEquals("manual",bindings.get(queue("1")).id());
        var registry=new SourceRegistry(prefs);assertTrue(registry.preloadEnabled());registry.setPreloadEnabled(false);
        assertFalse(new SourceRegistry(prefs).preloadEnabled());assertTrue(registry.isEnabled("qqmusic"));
    }
}
