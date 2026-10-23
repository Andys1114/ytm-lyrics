package dev.ytmlyrics.source;

import dev.ytmlyrics.core.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreloadIntegrationTest {
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
            public boolean contains(Song s,String p){return cache.get(s,p)!=null;}
            public void put(Song s,String p,List<Candidate> candidates,boolean complete){cache.put(s,p,candidates,complete);}
        },(song,sources,cancelled)->new Resolver().resolve(song,sources,cancelled),()->1000,p->{},(s,e)->fail(e.toString()));
        loader.update(QueuePlan.following(queue,7,current,3),providers,false);assertEquals(1,calls.get());
        var playback=new Song("","Next","Artist","Album",180000);
        var key=QueuePlan.cacheIdentity(queue,8,playback);
        var persisted=new PreloadCache(preferences,()->2000).get(key,policy);
        assertNotNull(persisted);assertTrue(persisted.complete());assertEquals("123",persisted.best(playback).id());
        assertTrue(persisted.best(playback).hasWords());assertTrue(persisted.best(playback).hasTranslation());assertEquals(1,calls.get());
    }
}
