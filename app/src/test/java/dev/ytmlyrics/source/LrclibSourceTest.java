package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Song;
import org.junit.Test;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    @Test public void audioAndUnknownExactQueriesSimplifyFieldsWithoutRewritingCandidates() throws Exception {
        String response=JSON.replace("Song & Other","繁體測試").replace("Artist","樂隊").replace("Album","專輯").replace("Hello","自寫原詞");
        for(var type:new Song.PlaybackType[]{Song.PlaybackType.AUDIO,Song.PlaybackType.UNKNOWN}) {
            var song=new Song("yt","繁體測試","樂隊","專輯",180500,type);
            var source=new LrclibSource((url,missing)->{
                assertTrue(missing);
                assertEquals("https://lrclib.net/api/get?track_name="+encode("繁体测试")+"&artist_name="+encode("乐队")
                        +"&album_name="+encode("专辑")+"&duration=180.5",url);
                return response;
            });
            var candidate=source.search(song,"").get(0);
            assertEquals("繁體測試",candidate.title());assertEquals("樂隊",candidate.artist());assertEquals("專輯",candidate.album());
            assertEquals("自寫原詞",candidate.lines().get(0).text());assertEquals("7",candidate.id());
        }
    }
    @Test public void knownVideoSkipsExactLookupAndDoesNotSendPublisherAsArtist() throws Exception {
        var song=new Song("yt","繁體測試","影片發佈者","專輯",180500,Song.PlaybackType.VIDEO);
        int[] requests={0};
        var source=new LrclibSource((url,missing)->{
            requests[0]++;assertFalse(missing);
            assertEquals("https://lrclib.net/api/search?track_name="+encode("繁体测试"),url);
            return "["+JSON+"]";
        });
        assertEquals(1,source.search(song,"").size());assertEquals(1,requests[0]);
    }
    @Test public void videoManualQueryKeepsExplicitPublisherAndOnlyConvertsScript() throws Exception {
        var song=new Song("yt","別的歌","影片發佈者","",180500,Song.PlaybackType.VIDEO);
        var source=new LrclibSource((url,missing)->{
            assertFalse(missing);assertEquals("https://lrclib.net/api/search?q="+encode("繁体测试 影片发布者 Live"),url);
            return "["+JSON+"]";
        });
        assertEquals(1,source.search(song,"繁體測試 影片發佈者 Live").size());
    }
    @Test public void missingArtistStillSearchesByTitleWithoutAnEmptyArtistFilter() throws Exception {
        var song = new Song("yt", "Song & Other", "", "Album", 180500, Song.PlaybackType.AUDIO);
        var source = new LrclibSource((url, missing) -> {
            assertFalse(missing);
            assertEquals("https://lrclib.net/api/search?track_name=" + encode(song.title()), url);
            return "[" + JSON + "]";
        });
        assertEquals(1, source.search(song, "").size());
    }
    @Test public void simplifiedExactMissStillFallsBackToSimplifiedTitleAndArtistSearch() throws Exception {
        var song=new Song("yt","繁體測試","樂隊","專輯",180500);
        var source=new LrclibSource((url,missing)->{
            if(missing)return null;
            assertEquals("https://lrclib.net/api/search?track_name="+encode("繁体测试")+"&artist_name="+encode("乐队"),url);
            return "[]";
        });
        assertTrue(source.search(song,"").isEmpty());
    }
    @Test public void automaticExactAndFallbackUseSpaceSeparatedMetadata() throws Exception {
        var song=new Song("yt","繁體、現場","樂隊、歌手和樂團","合輯 和 現場",180500);
        var urls=new ArrayList<String>();
        var source=new LrclibSource((url,missing)->{urls.add(url);return missing?null:"[]";});
        assertTrue(source.search(song,"").isEmpty());
        String fields="track_name="+encode("繁体 现场")+"&artist_name="+encode("乐队 歌手 乐团");
        assertEquals(java.util.List.of("https://lrclib.net/api/get?"+fields+"&album_name="+encode("合辑 现场")+"&duration=180.5",
                "https://lrclib.net/api/search?"+fields),urls);
    }
    @Test public void automaticRequestsDoNotSendYtmCollaboratorAnnotations() throws Exception {
        var song = new Song("yt", "日不落（合作音乐人：甲） (Live)", "蔡依林 (合作音樂人: 乙)", "专辑（合作音乐人：丙）", 180500);
        var urls = new ArrayList<String>();
        var source = new LrclibSource((url, missing) -> { urls.add(url); return missing ? null : "[]"; });
        assertTrue(source.search(song, "").isEmpty());
        String fields = "track_name=" + encode("日不落 (Live)") + "&artist_name=" + encode("蔡依林");
        assertEquals(java.util.List.of("https://lrclib.net/api/get?" + fields + "&album_name=" + encode("专辑") + "&duration=180.5",
                "https://lrclib.net/api/search?" + fields), urls);
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
}
