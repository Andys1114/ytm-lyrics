package dev.ytmlyrics.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaylistPreloaderTest {
    private static Song song(String id){return new Song(id,"Song "+id,"Artist","",0);}
    private static LyricsSource source(String id){return new LyricsSource(){public String id(){return id;}public String name(){return id;}
        public List<Candidate> search(Song song,String query){return List.of();}};}
    private static final List<LyricsSource> SOURCES=List.of(source("qqmusic"),source("netease"));
    private static Resolver.Result result(Song song) {
        var candidate=new Candidate("qqmusic",song.id(),song.title(),song.artist(),"",180000,null,"[00:01]自写",false);
        return new Resolver.Result(null,List.of(candidate),List.of(),false);
    }
    private static final class ManualExecutor implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task){tasks.add(task);}
        void run(){while(!tasks.isEmpty())tasks.remove().run();}
    }
    private static final class Cache implements PlaylistPreloader.Cache {
        final HashMap<String,List<Candidate>> values=new HashMap<>();
        public boolean contains(Song song,String policy){return values.containsKey(song.key()+policy);}
        public void put(Song song,String policy,List<Candidate> found,boolean complete){values.put(song.key()+policy,found);}
    }
    @Test public void preloadsOnlyThreeSeriallyAndRepeatedPublicationDoesNotRefetch() {
        var executor=new ManualExecutor();var cache=new Cache();var calls=new ArrayList<Song>();var progress=new ArrayList<PlaylistPreloader.Progress>();
        var loader=new PlaylistPreloader(executor,cache,(song,sources,cancelled)->{calls.add(song);return result(song);},()->1000,progress::add,(s,e)->fail());
        var songs=List.of(song("1"),song("2"),song("3"),song("4"));
        loader.update(songs,SOURCES,false);loader.update(songs,SOURCES,false);
        assertEquals(1,executor.tasks.size());executor.run();loader.update(songs,SOURCES,false);executor.run();
        assertEquals(songs.subList(0,3),calls);assertEquals(new PlaylistPreloader.Progress(3,3,false),progress.get(progress.size()-1));
    }
    @Test public void queueReorderDiscardsOldResultAndContinuesWithNewOrder() {
        var executor=new ManualExecutor();var cache=new Cache();var calls=new ArrayList<Song>();var owner=new PlaylistPreloader[1];
        owner[0]=new PlaylistPreloader(executor,cache,(song,sources,cancelled)->{
            calls.add(song);if(song.id().equals("old")){owner[0].update(List.of(song("new")),SOURCES,false);assertTrue(cancelled.getAsBoolean());}
            return result(song);
        },()->1000,p->{},(s,e)->fail());
        owner[0].update(List.of(song("old"),song("removed")),SOURCES,false);executor.run();
        assertEquals(List.of(song("old"),song("new")),calls);
        assertFalse(cache.contains(song("old"),PlaylistPreloader.policy(SOURCES)));
        assertTrue(cache.contains(song("new"),PlaylistPreloader.policy(SOURCES)));
    }
    @Test public void foregroundQueryPausesBackgroundAndInvalidatesItsResult() {
        var executor=new ManualExecutor();var cache=new Cache();var calls=new ArrayList<Song>();var owner=new PlaylistPreloader[1];
        owner[0]=new PlaylistPreloader(executor,cache,(song,sources,cancelled)->{
            calls.add(song);if(calls.size()==1)owner[0].update(List.of(song),SOURCES,true);return result(song);
        },()->1000,p->{},(s,e)->fail());
        owner[0].update(List.of(song("1")),SOURCES,true);assertTrue(executor.tasks.isEmpty());
        owner[0].update(List.of(song("1")),SOURCES,false);executor.run();
        assertEquals(1,calls.size());assertTrue(cache.values.isEmpty());
        owner[0].update(List.of(song("1")),SOURCES,false);executor.run();
        assertEquals(2,calls.size());assertEquals(1,cache.values.size());
    }
    @Test public void sourceChangeCancelsOldPolicyAndDisabledPreloadStopsAllWork() {
        var executor=new ManualExecutor();var cache=new Cache();var policies=new ArrayList<String>();var owner=new PlaylistPreloader[1];
        var changed=List.of(source("netease"));
        owner[0]=new PlaylistPreloader(executor,cache,(song,sources,cancelled)->{
            policies.add(PlaylistPreloader.policy(sources));
            if(policies.size()==1)owner[0].update(List.of(song),changed,false);
            return result(song);
        },()->1000,p->{},(s,e)->fail());
        owner[0].update(List.of(song("1")),SOURCES,false);executor.run();
        assertEquals(List.of(PlaylistPreloader.policy(SOURCES),PlaylistPreloader.policy(changed)),policies);
        assertFalse(cache.contains(song("1"),PlaylistPreloader.policy(SOURCES)));
        owner[0].update(List.of(),changed,false);executor.run();assertEquals(2,policies.size());
    }
    @Test public void failedRequestsHaveCooldownAndDoNotEraseOtherCachedSongs() {
        var executor=new ManualExecutor();var cache=new Cache();long[] now={1000};int[] calls={0};
        cache.put(song("cached"),PlaylistPreloader.policy(SOURCES),result(song("cached")).candidates(),true);
        var loader=new PlaylistPreloader(executor,cache,(song,sources,cancelled)->{calls[0]++;throw new java.io.IOException("503");},()->now[0],p->{},(s,e)->{});
        var plan=List.of(song("bad"),song("cached"));
        loader.update(plan,SOURCES,false);executor.run();
        loader.update(List.of(),SOURCES,false);loader.update(plan,SOURCES,false);executor.run();assertEquals(1,calls[0]);
        now[0]+=300001;loader.update(List.of(),SOURCES,false);loader.update(plan,SOURCES,false);executor.run();assertEquals(2,calls[0]);
        assertTrue(cache.contains(song("cached"),PlaylistPreloader.policy(SOURCES)));
    }
    @Test public void followsPublishedShuffleOrderAndSkipsDuplicateCurrentOrIncompleteEntries() {
        var current=song("current");
        var queue=List.of(new QueuePlan.Item(1,song("before")),new QueuePlan.Item(9,current),
                new QueuePlan.Item(2,song("b")),new QueuePlan.Item(3,current),new QueuePlan.Item(4,song("b")),
                new QueuePlan.Item(5,new Song("missing","","","",0)),new QueuePlan.Item(6,song("a")),new QueuePlan.Item(7,song("c")));
        assertEquals(List.of(song("b"),song("a"),song("c")),QueuePlan.following(queue,9,current,3));
        assertEquals(List.of(song("b"),song("a"),song("c")),QueuePlan.following(queue,-1,current,3));
        assertTrue(QueuePlan.following(queue,-1,song("unknown"),3).isEmpty());
        assertTrue(QueuePlan.following(queue,7,song("c"),3).isEmpty());
    }
    @Test public void missingMediaIdUsesVerifiedQueueIdentityWithoutChangingBindingKey() {
        var current=new Song("","Song current","Artist","Album",180000);String bindingKey=current.key();
        var queue=List.of(new QueuePlan.Item(7,song("current")),new QueuePlan.Item(8,song("next")));
        var cacheSong=QueuePlan.cacheIdentity(queue,7,current);
        assertEquals("current",cacheSong.id());assertEquals(180000,cacheSong.durationMs());assertEquals("Album",cacheSong.album());
        assertEquals(bindingKey,current.key());assertEquals(List.of(song("next")),QueuePlan.following(queue,-1,current,3));
    }
    @Test public void ambiguousNamesOrStaleActiveQueueIdDoNotInventCacheIdentity() {
        var current=new Song("","Song","Artist","",180000);
        var queue=List.of(new QueuePlan.Item(1,new Song("a","Song","Artist","",0)),
                new QueuePlan.Item(2,new Song("b","Song","Artist","",0)),new QueuePlan.Item(3,song("other")));
        assertSame(current,QueuePlan.cacheIdentity(queue,-1,current));
        assertSame(current,QueuePlan.cacheIdentity(queue,3,current));
        assertTrue(QueuePlan.following(queue,-1,current,3).isEmpty());
        assertEquals("b",QueuePlan.cacheIdentity(queue,2,current).id());
    }
}
