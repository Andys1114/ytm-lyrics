package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Song;
import org.junit.Test;
import java.io.IOException;
import java.util.ArrayList;
import static org.junit.Assert.*;

public class LrclibSourceTest {
    private static final Song SONG = new Song("video", "Song & Other", "Artist", "Album", 180500);
    private static final String JSON = "{\"id\":7,\"trackName\":\"Song & Other\",\"artistName\":\"Artist\",\"albumName\":\"Album\",\"duration\":180.5,\"plainLyrics\":\"Hello\",\"syncedLyrics\":\"[00:01.00]Hello\",\"instrumental\":false}";
    @Test public void exactUsesEncodedMetadataAndSeconds() throws Exception {
        var urls = new ArrayList<String>();
        var source = new LrclibSource((url, missing) -> { urls.add(url); assertTrue(missing); return JSON; });
        var result = source.search(SONG, "");
        assertTrue(urls.get(0).contains("track_name=Song+%26+Other"));
        assertTrue(urls.get(0).endsWith("duration=180.5"));
        assertEquals(180500, result.get(0).durationMs());
        assertEquals("7", result.get(0).id());
        assertEquals(1000, result.get(0).lines().get(0).timeMs());
    }
    @Test public void notFoundFallsThroughToSearchWithoutDuration() throws Exception {
        var urls = new ArrayList<String>();
        var source = new LrclibSource((url, missing) -> { urls.add(url); return missing ? null : "[" + JSON + "]"; });
        assertEquals(1, source.search(SONG, "").size());
        assertEquals(2, urls.size());
        assertTrue(urls.get(1).contains("/search?"));
        assertFalse(urls.get(1).contains("duration="));
    }
    @Test public void manualQueryDoesNotPerformAutomaticExactLookup() throws Exception {
        var source = new LrclibSource((url, missing) -> {
            assertFalse(missing); assertTrue(url.endsWith("search?q=Other+Live")); return "[" + JSON + "]";
        });
        assertEquals(1, source.search(SONG, "Other Live").size());
    }
    @Test public void rateLimitIsNotRetriedAsSearch() throws Exception {
        int[] requests = {0};
        var source = new LrclibSource((url, missing) -> { requests[0]++; throw new IOException("429"); });
        try { source.search(SONG, ""); fail("must report failure"); }
        catch (IOException expected) { assertEquals("429", expected.getMessage()); }
        assertEquals(1, requests[0]);
    }
    @Test public void nullLyricsAndInstrumentalAreNotFabricated() throws Exception {
        var source = new LrclibSource((url, missing) -> "{\"id\":8,\"duration\":20,\"plainLyrics\":null,\"syncedLyrics\":null,\"instrumental\":true}");
        var candidate = source.search(SONG, "").get(0);
        assertTrue(candidate.instrumental()); assertFalse(candidate.hasLyrics());
        assertNull(candidate.plain()); assertNull(candidate.synced());
    }
    @Test public void outOfRangeDurationIsOmitted() throws Exception {
        var source = new LrclibSource((url, missing) -> { assertFalse(url.contains("duration=")); return JSON; });
        source.search(new Song("x", "Song", "Artist", "", 0), "");
        source.search(new Song("x", "Song", "Artist", "", 500), "");
        source.search(new Song("x", "Song", "Artist", "", 4000000), "");
    }
}
