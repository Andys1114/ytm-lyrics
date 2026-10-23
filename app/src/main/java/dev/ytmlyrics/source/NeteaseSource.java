package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.LyricsSource;
import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.TimedLyrics;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.json.JSONObject;

/** Apache-2.0 protocol adaptation of absswds/Spotify_lyric NeteaseLyricsSource.kt,
 * revision 827a0f483e7808b4b749adbffdd9227ae3035b89; see third-party notices.
 * Modified: Java, ordered/limited requests, explicit failures, stable-ID refresh and YRC translation. */
public final class NeteaseSource implements LyricsSource {
    private static final String BASE = "https://music.163.com/api/";
    interface Transport { String get(String url) throws IOException; }
    private final Transport transport;
    public NeteaseSource() { this(url -> SourceHttp.request(url, null, "https://music.163.com/")); }
    NeteaseSource(Transport transport) { this.transport = transport; }
    @Override public String id() { return "netease"; }
    @Override public String name() { return "网易云音乐"; }
    @Override public boolean supportsEnhancement() { return true; }
    @Override public List<Candidate> search(Song song, String query) throws Exception {
        String keyword = query.isBlank() ? (song.title() + " " + song.artist()).strip() : query;
        JSONObject response = QqMusicSource.object(transport.get(BASE + "cloudsearch/pc?csrf_token=&s="
                + URLEncoder.encode(keyword, StandardCharsets.UTF_8) + "&type=1&offset=0&total=true&limit=3"));
        requireCode(response);
        JSONObject result = response.optJSONObject("result");
        if (result == null) throw new IOException("搜索响应缺少结果");
        var songs = result.optJSONArray("songs");
        if (songs == null && result.optInt("songCount", -1) == 0) return List.of();
        if (songs == null) throw new IOException("搜索响应缺少歌曲列表");
        List<Candidate> found = new ArrayList<>(); HashSet<String> seen = new HashSet<>();
        Exception failure = null;
        for (int i = 0; i < Math.min(3, songs.length()); i++) {
            JSONObject track = songs.getJSONObject(i);
            long numericId = track.optLong("id");
            if (numericId <= 0) throw new IOException("搜索响应缺少数字歌曲 ID");
            String id = Long.toString(numericId);
            if (!seen.add(id)) continue;
            var artists = track.optJSONArray("ar");
            if (artists == null || artists.length() == 0) artists = track.optJSONArray("artists");
            List<String> names = new ArrayList<>();
            if (artists != null) for (int j = 0; j < artists.length(); j++) {
                String artist=artists.getJSONObject(j).optString("name");if(!artist.isBlank())names.add(artist);
            }
            var album = track.optJSONObject("al"); if (album == null) album = track.optJSONObject("album");
            Candidate metadata = new Candidate(id(), id, track.optString("name"), String.join(", ", names),
                    album == null ? "" : album.optString("name"), track.optLong("dt",0)>0 ? track.optLong("dt") : track.optLong("duration",0), null, null, false);
            try { found.add(fetch(metadata)); } catch (Exception error) { failure = error; }
        }
        if (found.stream().noneMatch(Candidate::hasLyrics) && failure != null) throw failure;
        return List.copyOf(found);
    }
    @Override public Candidate refresh(Candidate cached) throws Exception {
        if (!id().equals(cached.sourceId()) || !cached.id().matches("[1-9]\\d*")) throw new IOException("网易云绑定 ID 无效");
        return fetch(cached).preserveCache(cached);
    }
    private Candidate fetch(Candidate metadata) throws Exception {
        JSONObject response = QqMusicSource.object(transport.get(BASE + "song/lyric/v1?id=" + metadata.id() + "&lv=-1&kv=-1&tv=-1&rv=-1&yv=-1&ytv=-1"));
        requireCode(response);
        if(!response.has("lrc") && !response.has("yrc") && !response.optBoolean("nolyric"))throw new IOException("歌词响应缺少正文");
        String lrc = lyric(response, "lrc");
        String yrc = lyric(response, "yrc");
        String translated = lyric(response, "ytlrc");
        if (translated == null || translated.isBlank()) translated = lyric(response, "tlyric");
        List<TimedLyrics.Line> lines = TimedLyrics.fromLrc(lrc, metadata.durationMs());
        String warning = "";
        try { var words = TimedLyrics.yrc(yrc); if (!words.isEmpty()) lines = words; }
        catch (IllegalArgumentException | ArithmeticException error) { warning = "逐字数据无效，已保留逐行歌词"; }
        lines = TimedLyrics.translate(lines, translated);
        boolean sync = !TimedLyrics.fromLrc(lrc, metadata.durationMs()).isEmpty();
        return new Candidate(id(), metadata.id(), metadata.title(), metadata.artist(), metadata.album(), metadata.durationMs(),
                sync ? null : lrc, sync ? lrc : null, response.optBoolean("nolyric"), lines, warning);
    }
    private static String lyric(JSONObject response, String key) {
        JSONObject value = response.optJSONObject(key); return value == null ? null : value.optString("lyric", null);
    }
    private static void requireCode(JSONObject response) throws IOException {
        if (response.optInt("code", -1) != 200) throw new IOException("网易云接口 code=" + response.optInt("code", -1));
    }
}
