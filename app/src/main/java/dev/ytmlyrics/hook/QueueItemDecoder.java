package dev.ytmlyrics.hook;

import dev.ytmlyrics.core.Song;

/** Fixed 9.41.52 hxj/hxg contract, decoded before leaving the host queue thread. */
final class QueueItemDecoder {
    private QueueItemDecoder() {}
    static Song read(Object item) throws ReflectiveOperationException {
        String id=text(HostPanelFactory.call(item,"t"));
        String title=text(HostPanelFactory.call(item,"h"));
        String artist=text(HostPanelFactory.call(item,"g"));
        if(id.isBlank() || title.isBlank() || artist.isBlank())return null;
        // The published queue has no reliable recording duration. Cache candidates;
        // automatic selection must wait for the real current MediaMetadata duration.
        return new Song(id,title,artist,"",0);
    }
    private static String text(Object value){return value==null ? "":value.toString();}
}
