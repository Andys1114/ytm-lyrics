package dev.ytmlyrics.core;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackTypeTest {
    @Test public void legacyConstructorAndBindingKeysKeepTheirOriginalMeaning() {
        var legacy=new Song("","自寫歌曲","發布者","",180000);
        var video=legacy.withPlaybackType(Song.PlaybackType.VIDEO);
        assertEquals(Song.PlaybackType.UNKNOWN,legacy.playbackType());assertFalse(legacy.isVideo());
        assertTrue(video.isVideo());assertEquals(legacy.key(),video.key());
        assertEquals(legacy.title(),video.title());assertEquals(legacy.artist(),video.artist());
        assertEquals("media-id",new Song("media-id","Song","Artist","",180000,Song.PlaybackType.VIDEO).key());
    }
    @Test public void verifiedQueueTypeDoesNotInventOrChangeTheCurrentMediaIdentity() {
        var current=new Song("","Song","Publisher","Album",180000);
        var queued=new Song("video-id","Song","Publisher","",0,Song.PlaybackType.VIDEO);
        var queue=List.of(new QueuePlan.Item(7,queued));
        var typed=QueuePlan.withPlaybackType(queue,7,current);
        assertTrue(typed.isVideo());assertEquals(current.key(),typed.key());assertEquals(current.id(),typed.id());
        var cached=QueuePlan.cacheIdentity(queue,7,typed);
        assertEquals("video-id",cached.id());assertEquals(Song.PlaybackType.VIDEO,cached.playbackType());
    }
    @Test public void unrelatedOrUnknownQueueItemsDoNotOverwriteThePlaybackType() {
        var current=new Song("current","Song","Publisher","",180000,Song.PlaybackType.AUDIO);
        var other=new Song("other","Song","Publisher","",0,Song.PlaybackType.VIDEO);
        assertSame(current,QueuePlan.withPlaybackType(List.of(new QueuePlan.Item(1,other)),1,current));
        var unknown=new Song("current","Song","Publisher","",0);
        assertSame(current,QueuePlan.withPlaybackType(List.of(new QueuePlan.Item(1,unknown)),1,current));
    }
    @Test public void changedActiveHostTypeIsAppliedWithoutChangingTheBindingKey() {
        var current=new Song("current","Song","Publisher","",180000,Song.PlaybackType.AUDIO);
        var queue=List.of(new QueuePlan.Item(1,current),new QueuePlan.Item(2,current.withPlaybackType(Song.PlaybackType.VIDEO)));
        var typed=QueuePlan.withPlaybackType(queue,2,current);
        assertTrue(typed.isVideo());assertEquals(current.key(),typed.key());
    }
    @Test public void videoQueueSearchDoesNotRequirePublisherMetadata() {
        var current=new Song("current","Current","Artist","",180000);
        var video=new Song("video","自寫歌曲","","",0,Song.PlaybackType.VIDEO);
        var incompleteAudio=new Song("audio","Audio song","","",0,Song.PlaybackType.AUDIO);
        var queue=List.of(new QueuePlan.Item(1,current),new QueuePlan.Item(2,video),new QueuePlan.Item(3,incompleteAudio));
        assertEquals(List.of(video),QueuePlan.following(queue,1,current,3));
        assertEquals("自写歌曲",SearchQuery.of(video,"").keywords());
    }
}
