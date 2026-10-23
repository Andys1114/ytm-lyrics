package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Song;
import org.junit.Test;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import static org.junit.Assert.*;

public class QqMusicSourceTest {
    private interface LineTransport { String get(String url) throws IOException; }
    private static QqMusicSource lineSource(LineTransport transport) {
        return new QqMusicSource((url,form) -> form == null ? transport.get(url) : "<result>0</result>");
    }
    private static final Song SONG = new Song("video", "自写测试 & 歌", "甲", "专辑", 180000);
    private static String track(long id) {
        return "{\"songid\":" + id + ",\"songmid\":\"alphabeticMid\",\"songname\":\"自写测试 & 歌\","
                + "\"interval\":180,\"albumname\":\"专辑\",\"singer\":[{\"name\":\"甲\"},{\"name\":\"乙\"}]}";
    }
    private static String search(String tracks) { return "{\"code\":0,\"data\":{\"song\":{\"list\":[" + tracks + "]}}}"; }
    private static String lyric(String text) {
        return "{\"code\":0,\"retcode\":0,\"lyric\":\"" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "\"}";
    }
    @Test public void automaticSearchUsesUtf8MetadataAndNumericMusicId() throws Exception {
        var urls = new ArrayList<String>();
        var source = lineSource(url -> {
            urls.add(url);
            return url.contains("client_search_cp") ? search(track(123)) : lyric("[00:01.50]自写一行 (测试)\n[00:03]下一行");
        });
        var result = source.search(SONG, "");
        assertTrue(urls.get(0).contains("%26"));
        assertTrue(urls.get(0).contains("%E7%94%B2"));
        assertTrue(urls.get(1).contains("musicid=123&"));
        assertFalse(urls.get(1).contains("songmid"));
        var candidate = result.get(0);
        assertEquals("qqmusic", candidate.sourceId()); assertEquals("123", candidate.id());
        assertEquals("甲, 乙", candidate.artist()); assertEquals("专辑", candidate.album());
        assertEquals(180000, candidate.durationMs()); assertEquals(1500, candidate.lines().get(0).timeMs());
        assertEquals("自写一行 (测试)", candidate.lines().get(0).text());
    }
    @Test public void manualQueryOverridesMetadataAndFetchesAtMostThreeSongsSequentially() throws Exception {
        var urls = new ArrayList<String>();
        var source = lineSource(url -> {
            urls.add(url);
            return url.contains("client_search_cp") ? search(track(1) + "," + track(2) + "," + track(3) + "," + track(4)) : lyric("[00:01]自写测试");
        });
        assertEquals(3, source.search(SONG, "Other Live").size());
        assertTrue(urls.get(0).contains("w=Other+Live&")); assertTrue(urls.get(0).contains("n=3&"));
        assertEquals(4, urls.size());
        for (int i = 1; i <= 3; i++) assertTrue(urls.get(i).contains("musicid=" + i + "&"));
    }
    @Test public void emptySearchDoesNotFetchAnyLyrics() throws Exception {
        var source = lineSource(url -> { assertTrue(url.contains("client_search_cp")); return search(""); });
        assertTrue(source.search(SONG, "").isEmpty());
    }
    @Test public void jsonAndJsonpDoNotStripParenthesesInsideLyrics() throws Exception {
        assertEquals("x(y)", QqMusicSource.object("{\"text\":\"x(y)\"}").getString("text"));
        assertEquals("x(y)", QqMusicSource.object("\uFEFF MusicJsonCallback_lrc({\"text\":\"x(y)\"}); ").getString("text"));
        try { QqMusicSource.object("<html>blocked</html>"); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("JSON")); }
    }
    @Test public void searchCodeAndLyricRetcodeAreReported() throws Exception {
        var failedSearch = lineSource(url -> "{\"code\":403}");
        try { failedSearch.search(SONG, ""); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("code=403")); }
        var failedLyric = lineSource(url -> url.contains("client_search_cp") ? search(track(1)) : "{\"code\":0,\"retcode\":1101}");
        try { failedLyric.search(SONG, ""); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("retcode=1101")); }
    }
    @Test public void oneFailedCandidateDoesNotEraseAnotherRecording() throws Exception {
        var source = lineSource(url -> {
            if (url.contains("client_search_cp")) return search(track(1) + "," + track(2));
            if (url.contains("musicid=1&")) throw new IOException("HTTP 503");
            return lyric("[00:01]自写测试");
        });
        var result = source.search(SONG, "");
        assertEquals(1, result.size()); assertEquals("2", result.get(0).id());
    }
    @Test public void errorsAreVisibleWhenNoCandidateHasUsableLyrics() throws Exception {
        var source = lineSource(url -> {
            if (url.contains("client_search_cp")) return search(track(1) + "," + track(2));
            if (url.contains("musicid=1&")) return lyric("");
            throw new IOException("HTTP 429");
        });
        try { source.search(SONG, ""); fail(); }
        catch (IOException expected) { assertEquals("HTTP 429", expected.getMessage()); }
    }
    @Test public void invalidBase64FailsButPlainTextAndEmptyLyricsArePreserved() throws Exception {
        var invalid = lineSource(url -> url.contains("client_search_cp") ? search(track(1)) : "{\"code\":0,\"retcode\":0,\"lyric\":\"%%%\"}");
        try { invalid.search(SONG, ""); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("Base64")); }
        var plain = lineSource(url -> url.contains("client_search_cp") ? search(track(1)) : lyric("自写纯文本"));
        var candidate = plain.search(SONG, "").get(0);
        assertEquals("自写纯文本", candidate.plain()); assertFalse(candidate.hasSynced());
        var empty = lineSource(url -> url.contains("client_search_cp") ? search(track(1)) : lyric(""));
        assertFalse(empty.search(SONG, "").get(0).hasLyrics());
    }
    @Test public void duplicateNumericIdIsNotFetchedTwice() throws Exception {
        int[] requests = {0};
        var source = lineSource(url -> { requests[0]++; return url.contains("client_search_cp") ? search(track(1) + "," + track(1)) : lyric("[00:01]自写测试"); });
        assertEquals(1, source.search(SONG, "").size()); assertEquals(2, requests[0]);
    }
    @Test public void malformedSuccessfulResponseDoesNotMasqueradeAsNoMatches() throws Exception {
        var noList = lineSource(url -> "{\"code\":0}");
        try { noList.search(SONG, ""); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("列表")); }
        var noId = lineSource(url -> search("{\"songmid\":\"alphabeticMid\"}"));
        try { noId.search(SONG, ""); fail(); }
        catch (IOException expected) { assertTrue(expected.getMessage().contains("数字歌曲 ID")); }
    }
}
