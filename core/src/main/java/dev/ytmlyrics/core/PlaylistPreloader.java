package dev.ytmlyrics.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** One bounded worker; queue/source changes invalidate in-flight results without touching playback. */
public final class PlaylistPreloader {
    public interface Cache {
        boolean contains(Song song,String policy);
        void put(Song song,String policy,List<Candidate> candidates,boolean complete);
    }
    public interface Loader {
        Resolver.Result load(Song song,List<LyricsSource> sources,BooleanSupplier cancelled) throws Exception;
    }
    public record Progress(int cached,int total,boolean working) {}
    private record Key(Song song,String policy) {}
    private static final long RETRY_MS=5*60*1000;
    private final Executor executor;
    private final Cache cache;
    private final Loader loader;
    private final LongSupplier clock;
    private final Consumer<Progress> listener;
    private final BiConsumer<Song,Exception> failure;
    private final LinkedHashMap<Key,Long> attempted=new LinkedHashMap<>();
    private List<Song> songs=List.of();
    private List<LyricsSource> sources=List.of();
    private String policy="";
    private long revision;
    private boolean paused,running;

    public PlaylistPreloader(Executor executor,Cache cache,Loader loader,LongSupplier clock,
                             Consumer<Progress> listener,BiConsumer<Song,Exception> failure) {
        this.executor=executor;this.cache=cache;this.loader=loader;this.clock=clock;this.listener=listener;this.failure=failure;
    }
    public static String policy(List<LyricsSource> sources) {
        return "automatic-v2/"+String.join(",",sources.stream().map(LyricsSource::id).toList());
    }
    public synchronized void update(List<Song> next,List<LyricsSource> enabled,boolean foregroundBusy) {
        var limited=next.stream().distinct().limit(3).toList();
        String nextPolicy=policy(enabled);
        if(songs.equals(limited) && policy.equals(nextPolicy) && paused==foregroundBusy)return;
        revision++;
        songs=List.copyOf(limited);sources=List.copyOf(enabled);policy=nextPolicy;paused=foregroundBusy;
        if(!running && !paused && !songs.isEmpty() && !sources.isEmpty()) {
            running=true;executor.execute(this::drain);
        }
    }
    private synchronized boolean stale(long token){return token!=revision;}
    private synchronized Progress progress(boolean working) {
        int ready=(int)songs.stream().filter(song->cache.contains(song,policy)).count();
        return new Progress(ready,songs.size(),working);
    }
    private void drain() {
        while(true) {
            Song target=null;List<LyricsSource> providers;String signature;long token;
            synchronized(this) {
                providers=sources;signature=policy;token=revision;
                if(!paused && !providers.isEmpty())for(Song song:songs) {
                    Key key=new Key(song,signature);
                    if(!cache.contains(song,signature) && attempted.getOrDefault(key,0L)<=clock.getAsLong()) {
                        target=song;attempted.put(key,clock.getAsLong()+RETRY_MS);break;
                    }
                }
                while(attempted.size()>64)attempted.remove(attempted.keySet().iterator().next());
                if(target==null){running=false;listener.accept(progress(false));return;}
            }
            listener.accept(progress(true));
            try {
                var result=loader.load(target,providers,()->stale(token));
                synchronized(this) {
                    if(!stale(token) && !result.cancelled() && !result.candidates().isEmpty())
                        cache.put(target,signature,result.candidates(),result.errors().isEmpty());
                    else if(stale(token))attempted.remove(new Key(target,signature));
                }
            }catch(Exception error){if(!stale(token))failure.accept(target,error);}
            finally{synchronized(this){if(stale(token))attempted.remove(new Key(target,signature));}}
        }
    }
}
