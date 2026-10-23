package dev.ytmlyrics.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class QueueItemDecoderTest {
    public static class QueueItem {
        public String t(){return "video-id";}
        public String h(){return "Song";}
        public String g(){return "Artist";}
    }
    @Test public void readsFixedHostContractWithoutInventingDurationOrUsingEphemeralQueueId() throws Exception {
        var song=QueueItemDecoder.read(new QueueItem());
        assertEquals("video-id",song.id());assertEquals("Song",song.title());assertEquals("Artist",song.artist());
        assertEquals(0,song.durationMs());assertEquals("",song.album());
    }
    @Test public void incompleteMetadataIsSkipped() throws Exception {
        assertNull(QueueItemDecoder.read(new QueueItem(){@Override public String g(){return null;}}));
    }
}
