package dev.ytmlyrics.source;

import dev.ytmlyrics.core.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreloadIntegrationTest {
    @Test public void cachedCandidatesSupplementWordsAtPlaybackWithoutChangingBinding() throws Exception {
        var preferences = new MemoryPreferences();
        var cache = new PreloadCache(preferences, () -> 1000);
        var queued = new Song("next", "Song", "Artist", "", 0);
        var playback = new Song("next", "Song", "Artist", "Album", 180000);
        var words = new Candidate("qqmusic", "123", "Song", "Artist", "Album", 180000,
                null, "[00:01]一起走\n[00:03]看天亮", false,
                TimedLyrics.qrc("[1000,800]一起走(1000,800)\n[3000,700]看天亮(3000,700)"), "");
        var translated = new Candidate("netease", "456", "Song", "Artist", "Album", 180000,
                null, "[00:01]一起走\n[00:03]看天亮", false,
                TimedLyrics.translate(TimedLyrics.fromLrc(words.synced(), 180000),
                        "[00:01]Walk together\n[00:03]Watch the sunrise"), "");
        var bindings = new BindingStore(preferences);
        bindings.put(playback, words);
        String policy = new PlaylistPreloader.Policy(List.of("qqmusic", "netease")).cacheKey();
        cache.put(queued, policy, List.of(words, translated), true);

        var pool = new PreloadCache(preferences, () -> 2000).get(playback, policy);
        assertNotNull(pool);
        assertEquals(List.of(words, translated), pool.candidates());
        var selected = pool.best(playback);
        assertTrue(selected.hasWords());
        assertEquals("netease", selected.translationSourceId());
        var payload = new org.json.JSONObject(LyricsJson.payload(selected, 42, true));
        var line = payload.getJSONArray("lines").getJSONObject(0);
        assertEquals("Walk together", line.getString("translatedLyric"));
        assertEquals(1800, line.getJSONArray("words").getJSONObject(0).getLong("endTime"));
        assertEquals(words, bindings.get(playback));
        assertNull(cache.get(playback, new PlaylistPreloader.Policy(List.of("qqmusic")).cacheKey()));
    }
    @Test public void typedSourcePolicyReadsAnExistingPersistentPoolWithoutRefetching() {
        var preferences=new MemoryPreferences();var cache=new PreloadCache(preferences,()->1000);
        var upcoming=new Song("next","Next","Artist","Album",180000);
        var candidate=new Candidate("qqmusic","123","Next","Artist","Album",180000,null,"[00:01]自写歌词",false);
        cache.put(upcoming,"automatic-v3/qqmusic",List.of(candidate),true);
        LyricsSource provider=new LyricsSource(){
            public String id(){return "qqmusic";}public String name(){return "QQ";}
            public List<Candidate> search(Song song,String query){fail("Existing pools must remain usable");return List.of();}
        };
        var loader=new PlaylistPreloader(Runnable::run,new PlaylistPreloader.Cache(){
            public boolean contains(Song song,PlaylistPreloader.Policy policy){return cache.get(song,policy.cacheKey())!=null;}
            public void put(Song song,PlaylistPreloader.Policy policy,List<Candidate> found,boolean complete){fail("No refetch expected");}
        },(song,sources,cancelled)->new Resolver().resolve(song,sources,cancelled),()->1000,p->{},(s,e)->fail(e.toString()));
        loader.update(List.of(upcoming),List.of(provider),false);
        assertEquals("123",cache.get(upcoming,PlaylistPreloader.policy(List.of(provider))).best(upcoming).id());
    }
    @Test public void newSearchPolicyInvalidatesOnlyOldAutomaticPoolsNotManualBindings() {
        var preferences=new MemoryPreferences();var cache=new PreloadCache(preferences,()->1000);
        var song=new Song("next","Song","Artist","",180000);
        var candidate=new Candidate("qqmusic","123","Song","Artist","",180000,null,"[00:01]自写歌词",false);
        var bindings=new BindingStore(preferences);bindings.put(song,candidate);
        cache.put(song,"automatic-v2/qqmusic",List.of(candidate),true);
        assertNotNull(cache.get(song,"automatic-v2/qqmusic"));
        assertNull(cache.get(song,new PlaylistPreloader.Policy(List.of("qqmusic")).cacheKey()));
        assertEquals("123",bindings.get(song).id());
    }
    @Test public void nativeQueueToPersistentPoolToInstantPlaybackSelectionWithoutRefetch() {
        var preferences=new MemoryPreferences();var cache=new PreloadCache(preferences,()->1000);var calls=new AtomicInteger();
        LyricsSource provider=new LyricsSource(){
            public String id(){return "qqmusic";}public String name(){return "QQ";}
            public List<Candidate> search(Song song,String query) {
                calls.incrementAndGet();assertEquals(0,song.durationMs());
                return List.of(new Candidate(id(),"123",song.title(),song.artist(),"Album",180000,null,null,false,
                        TimedLyrics.translate(TimedLyrics.yrc("[1000,1000](1000,1000,0)自写歌词"),"[00:01]Original"),""));
            }
        };
        var providers=List.of(provider);String policy=PlaylistPreloader.policy(providers);
        var current=new Song("now","Current","Artist","",180000);
        var upcoming=new Song("next","Next","Artist","",0);
        var queue=List.of(new QueuePlan.Item(7,current),new QueuePlan.Item(8,upcoming));
        var loader=new PlaylistPreloader(Runnable::run,new PlaylistPreloader.Cache(){
            public boolean contains(Song s,PlaylistPreloader.Policy p){return cache.get(s,p.cacheKey())!=null;}
            public void put(Song s,PlaylistPreloader.Policy p,List<Candidate> candidates,boolean complete){cache.put(s,p.cacheKey(),candidates,complete);}
        },(song,sources,cancelled)->new Resolver().resolve(song,sources,cancelled),()->1000,p->{},(s,e)->fail(e.toString()));
        loader.update(QueuePlan.following(queue,7,current,3),providers,false);assertEquals(1,calls.get());
        var playback=new Song("","Next","Artist","Album",180000);
        var key=QueuePlan.cacheIdentity(queue,8,playback);
        var persisted=new PreloadCache(preferences,()->2000).get(key,policy);
        assertNotNull(persisted);assertTrue(persisted.complete());assertEquals("123",persisted.best(playback).id());
        assertTrue(persisted.best(playback).hasWords());assertTrue(persisted.best(playback).hasTranslation());assertEquals(1,calls.get());
    }
}
