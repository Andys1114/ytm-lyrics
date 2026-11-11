package dev.ytmlyrics.hook;

import dev.ytmlyrics.core.Song;
import java.lang.reflect.Method;

/** Fixed YTM 9.41.52 music-video enum; its wire numbers are not Song enum ordinals. */
final class HostPlaybackType {
    static final String METADATA_KEY = "dev.ytmlyrics.PLAYBACK_TYPE";
    private HostPlaybackType() {}

    static Song.PlaybackType decode(long value) {
        // anux: ATV=1 and PRIVATELY_OWNED_TRACK=6 satisfy the host's ifa.a(anux) predicate.
        // OMV=2 and UGC=3 explicitly identify music videos. Other categories are
        // not assumed to be videos, including podcasts, livestreams and GATV.
        if (value == 1 || value == 6) return Song.PlaybackType.AUDIO;
        if (value == 2 || value == 3) return Song.PlaybackType.VIDEO;
        return Song.PlaybackType.UNKNOWN;
    }

    static Method endpointReader(ClassLoader loader) throws ReflectiveOperationException {
        Method reader = Class.forName("hlk", false, loader).getDeclaredMethod("H", Class.forName("ajrz", false, loader));
        reader.setAccessible(true);
        return reader;
    }

    /** Reads the item's selected endpoint, including hxq's current audio/video counterpart. */
    static int wireType(Object item) throws ReflectiveOperationException {
        return item == null ? 0 : wireType(item, endpointReader(item.getClass().getClassLoader()));
    }

    static int wireType(Object item, Method endpointReader) throws ReflectiveOperationException {
        if (item == null) return 0;
        // hxp.n()/hxq.n() yield abjj; abjj.b is the selected watch endpoint.
        // hlk.H handles the host's endpoint extensions and missing-type semantics.
        Object descriptor = HostPanelFactory.call(item, "n");
        if (descriptor == null) return 0;
        Object endpoint = HostPanelFactory.get(descriptor, "b");
        if (endpoint == null) return 0;
        Object type = endpointReader.invoke(null, endpoint);
        return type == null ? 0 : ((Number) HostPanelFactory.call(type, "getNumber")).intValue();
    }
}
