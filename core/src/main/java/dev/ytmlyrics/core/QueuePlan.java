package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** The published playback order, not an inferred album order or a remote playlist. */
public final class QueuePlan {
    private QueuePlan() {}
    public record Item(long queueId, Song song) {}
    public static List<Song> following(List<Item> queue, long activeQueueId, Song current, int limit) {
        if (current == null || limit <= 0) return List.of();
        int index=-1;
        if (activeQueueId != -1) for(int i=0;i<queue.size();i++)
            if(queue.get(i).queueId()==activeQueueId){index=i;break;}
        if(index<0 && !current.id().isBlank()) for(int i=0;i<queue.size();i++)
            if(queue.get(i).song()!=null && current.id().equals(queue.get(i).song().id())){index=i;break;}
        if(index<0)index=uniqueNamedIndex(queue,current);
        if(index<0)return List.of(); // Never guess that the first item is current.
        var seen=new HashSet<String>();seen.add(current.key());
        if(queue.get(index).song()!=null)seen.add(queue.get(index).song().key());
        var result=new ArrayList<Song>();
        for(int i=index+1;i<queue.size() && result.size()<limit;i++) {
            Song song=queue.get(i).song();
            if(song!=null && !song.id().isBlank() && !song.title().isBlank() && !song.artist().isBlank()
                    && seen.add(song.key()))result.add(song);
        }
        return List.copyOf(result);
    }
    /** YTM may omit MEDIA_ID. Resolve a cache-only identity; never change the manual binding key. */
    public static Song cacheIdentity(List<Item> queue,long activeQueueId,Song current) {
        if(!current.id().isBlank())return current;
        Song named=null;
        if(activeQueueId!=-1)for(var item:queue)
            if(item.queueId()==activeQueueId && sameNames(current,item.song())){named=item.song();break;}
        if(named==null){int index=uniqueNamedIndex(queue,current);if(index>=0)named=queue.get(index).song();}
        return named==null ? current:new Song(named.id(),current.title(),current.artist(),current.album(),current.durationMs());
    }
    private static int uniqueNamedIndex(List<Item> queue,Song current) {
        int found=-1;String id=null;
        for(int i=0;i<queue.size();i++) {
            Song value=queue.get(i).song();
            if(!sameNames(current,value) || value.id().isBlank())continue;
            if(id!=null && !id.equals(value.id()))return -1;
            if(found<0){found=i;id=value.id();}
        }
        return found;
    }
    private static boolean sameNames(Song a,Song b) {
        return b!=null && !a.title().isBlank() && !a.artist().isBlank()
                && Matching.normalize(a.title()).equals(Matching.normalize(b.title()))
                && Matching.normalize(a.artist()).equals(Matching.normalize(b.artist()));
    }
}
