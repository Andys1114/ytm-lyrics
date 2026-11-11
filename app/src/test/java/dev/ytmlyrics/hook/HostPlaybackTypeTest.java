package dev.ytmlyrics.hook;

import dev.ytmlyrics.core.Song;
import java.lang.reflect.Method;
import org.junit.Test;
import static org.junit.Assert.*;

public class HostPlaybackTypeTest {
    public enum HostType {
        UNKNOWN(0), ATV(1), OMV(2), UGC(3);
        private final int number;
        HostType(int number) { this.number = number; }
        public int getNumber() { return number; }
    }
    public static class Descriptor {
        public HostType b;
        Descriptor(HostType endpoint) { b = endpoint; }
    }
    public static class QueueItem {
        private Descriptor current;
        QueueItem(Descriptor descriptor) { current = descriptor; }
        public Descriptor n() { return current; }
        // The host's j() is an uploaded-track predicate, not a video flag.
        public boolean j() { throw new AssertionError("Must read the selected endpoint"); }
    }
    public static HostType endpointType(HostType endpoint) { return endpoint; }
    private static Method reader() throws Exception {
        return HostPlaybackTypeTest.class.getMethod("endpointType", HostType.class);
    }

    @Test public void mapsOnlyConfirmedHostCategories() {
        assertEquals(Song.PlaybackType.AUDIO, HostPlaybackType.decode(1));
        assertEquals(Song.PlaybackType.AUDIO, HostPlaybackType.decode(6));
        assertEquals(Song.PlaybackType.VIDEO, HostPlaybackType.decode(2));
        assertEquals(Song.PlaybackType.VIDEO, HostPlaybackType.decode(3));
        for (long value : new long[]{0,4,5,7,8,9,-1,10,Long.MAX_VALUE})
            assertEquals("raw type " + value, Song.PlaybackType.UNKNOWN, HostPlaybackType.decode(value));
    }

    @Test public void followsTheCurrentlySelectedEndpointWithoutReadingThePublisher() throws Exception {
        var item = new QueueItem(new Descriptor(HostType.ATV));
        assertEquals(1, HostPlaybackType.wireType(item, reader()));
        item.current = new Descriptor(HostType.OMV);
        assertEquals(2, HostPlaybackType.wireType(item, reader()));
        item.current = new Descriptor(HostType.UGC);
        assertEquals(3, HostPlaybackType.wireType(item, reader()));
    }

    @Test public void missingPlaybackInformationStaysUnknown() throws Exception {
        assertEquals(0, HostPlaybackType.wireType(null));
        assertEquals(0, HostPlaybackType.wireType(new QueueItem(null), reader()));
        assertEquals(0, HostPlaybackType.wireType(new QueueItem(new Descriptor(null)), reader()));
    }
}
