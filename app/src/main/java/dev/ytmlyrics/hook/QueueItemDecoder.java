package dev.ytmlyrics.hook;

import dev.ytmlyrics.core.Song;

/** Fixed 9.41.52 hxj/hxg contract, decoded before leaving the host queue thread. */
final class QueueItemDecoder {
    private QueueItemDecoder() {}
    static Song read(Object item) throws ReflectiveOperationException {
        String id=text(HostPanelFactory.call(item,"t"));
        String title=text(HostPanelFactory.call(item,"h"));
        String artist=text(HostPanelFactory.call(item,"g"));
        if(id.isBlank() || title.isBlank())return null;
        // The published queue has no reliable recording duration. Cache candidates;
        // automatic selection must wait for the real current MediaMetadata duration.
        Song.PlaybackType type=Song.PlaybackType.UNKNOWN;
        try { type=HostPlaybackType.decode(HostPlaybackType.wireType(item)); }
        catch(ReflectiveOperationException ignored) { /* Missing optional type must not discard a usable queue item. */ }
        if(artist.isBlank() && type!=Song.PlaybackType.VIDEO)return null;
        return new Song(id,title,artist,"",0,type);
    }
    private static String text(Object value){return value==null ? "":value.toString();}
}
