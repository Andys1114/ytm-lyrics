package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Lrc;
import dev.ytmlyrics.core.TimedLyrics;
import dev.ytmlyrics.core.LyricsSource;
import dev.ytmlyrics.core.Song;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Portions Copyright 2026 absswds, licensed under Apache-2.0.
 * QQ search -> numeric musicid -> line lyrics. Protocol flow adapted from
 * absswds/Spotify_lyric, revision 827a0f483e7808b4b749adbffdd9227ae3035b89,
 * lyrics/qqmusic/QQMusicLyricsSource.kt (Apache-2.0; see third-party notices).
 * Modified here: Java, sequential requests, stable IDs, explicit failures, QRC and translations.
 */
public final class QqMusicSource implements LyricsSource {
    private static final int MAX_SONGS = 3;
    private static final String SEARCH = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String LYRIC = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    interface Transport { String request(String url, String form) throws IOException; }
    private final Transport transport;
    public QqMusicSource() { this((url, form) -> SourceHttp.request(url, form, "https://y.qq.com/")); }
    QqMusicSource(Transport transport) { this.transport = transport; }
    @Override public String id() { return "qqmusic"; }
    @Override public String name() { return "QQ 音乐"; }
    @Override public boolean supportsEnhancement() { return true; }

    @Override public List<Candidate> search(Song song, String query) throws Exception {
        String keyword = query.isBlank() ? (song.title() + " " + song.artist()).strip() : query;
        JSONObject result = object(transport.request(SEARCH + "?w=" + URLEncoder.encode(keyword, StandardCharsets.UTF_8)
                + "&format=json&p=1&n=" + MAX_SONGS + "&cr=1&aggr=1", null));
        requireCode(result, "搜索");
        JSONObject data = result.optJSONObject("data");
        JSONObject songs = data == null ? null : data.optJSONObject("song");
        JSONArray list = songs == null ? null : songs.optJSONArray("list");
        if (list == null) throw new IOException("搜索响应缺少歌曲列表");
        List<Candidate> candidates = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        IOException failure = null;
        for (int i = 0; i < Math.min(MAX_SONGS, list.length()); i++) {
            JSONObject track = list.getJSONObject(i);
            long id = track.optLong("songid", 0);
            if (id <= 0) throw new IOException("搜索响应缺少数字歌曲 ID");
            if (!seen.add(id)) continue;
            try {
                JSONArray singers = track.optJSONArray("singer");
                List<String> artists = new ArrayList<>();
                if (singers != null) for (int j = 0; j < singers.length(); j++) {
                    String artist = singers.getJSONObject(j).optString("name", "");
                    if (!artist.isBlank()) artists.add(artist);
                }
                Candidate metadata = new Candidate(id(), Long.toString(id), track.getString("songname"),
                        String.join(", ", artists), track.optString("albumname", ""),
                        Math.max(0, track.optLong("interval", 0)) * 1000, null, null, false);
                candidates.add(fetch(metadata));
            } catch (IOException error) { failure = error; }
        }
        // One failed candidate must not erase another successfully retrieved recording.
        // If all lyric requests failed, report the cause instead of claiming "no lyrics".
        if (candidates.stream().noneMatch(Candidate::hasLyrics) && failure != null) throw failure;
        return List.copyOf(candidates);
    }

    private static void requireCode(JSONObject response, String operation) throws IOException {
        int code = response.optInt("code", -1);
        if (code != 0) throw new IOException(operation + "接口 code=" + code);
    }
    static JSONObject object(String body) throws IOException {
        String json = body.replace("\uFEFF", "").strip();
        if (!json.startsWith("{")) {
            int open = json.indexOf('(');
            if (json.endsWith(";")) json = json.substring(0, json.length() - 1).stripTrailing();
            if (open < 1 || !json.substring(0, open).strip().matches("[A-Za-z_$][A-Za-z0-9_$]*") || !json.endsWith(")"))
                throw new IOException("接口未返回 JSON 数据");
            json = json.substring(open + 1, json.length() - 1);
        }
        try { return new JSONObject(json); }
        catch (org.json.JSONException error) { throw new IOException("接口 JSON 数据无效", error); }
    }
    @Override public Candidate refresh(Candidate cached) throws Exception {
        if (!id().equals(cached.sourceId()) || !cached.id().matches("[1-9]\\d*")) throw new IOException("QQ 绑定数字 ID 无效");
        return fetch(cached).preserveCache(cached);
    }
    private Candidate fetch(Candidate metadata) throws IOException {
        String lrc = null, translated = null; String warning = "";
        IOException failure = null;
        try {
            JSONObject response = object(transport.request(LYRIC + "?musicid=" + metadata.id() + "&g_tk=5381&format=json", null));
            requireCode(response,"歌词");
            if (response.optInt("retcode",-1) != 0) throw new IOException("歌词接口 retcode=" + response.optInt("retcode",-1));
            lrc = base64(response.optString("lyric",""));
            translated = base64(response.optString("trans",""));
        } catch (IOException error) { failure = error; warning = error.getMessage(); }
        List<TimedLyrics.Line> lines = TimedLyrics.fromLrc(lrc,metadata.durationMs());
        try {
            String response = transport.request("https://c.y.qq.com/qqmusic/fcgi-bin/lyric_download.fcg",
                    "version=15&miniversion=82&lrctype=4&musicid=" + metadata.id());
            var result = Pattern.compile("<result>(-?\\d+)</result>").matcher(response);
            if (!result.find() || !result.group(1).equals("0")) throw new IOException("QRC 接口响应无效或拒绝请求");
            String original = encrypted(response,"content");
            if (original != null) {
                var words = TimedLyrics.qrc(lyricContent(QrcCrypto.decrypt(original)));
                if (!words.isEmpty()) lines = words;
            }
            String trans = encrypted(response,"contentts");
            if (trans != null) {
                String decoded = lyricContent(QrcCrypto.decrypt(trans));
                if (!Lrc.parse(decoded).isEmpty()) translated = decoded;
            }
        } catch (IOException | IllegalArgumentException | ArithmeticException error) {
            warning = (warning.isBlank() ? "" : warning + "；") + "逐字请求失败：" + error.getMessage();
            if (failure == null) failure = new IOException(warning,error);
        }
        lines = TimedLyrics.translate(lines,translated);
        boolean sync = !Lrc.parse(lrc).isEmpty();
        Candidate candidate = new Candidate(id(),metadata.id(),metadata.title(),metadata.artist(),metadata.album(),metadata.durationMs(),
                sync ? null : lrc,sync ? lrc : null,false,lines,warning);
        if (!candidate.hasLyrics() && failure != null) throw failure;
        return candidate;
    }
    private static String base64(String value) throws IOException {
        try { return new String(Base64.getDecoder().decode(value),StandardCharsets.UTF_8); }
        catch (IllegalArgumentException error) { throw new IOException("歌词 Base64 数据无效",error); }
    }
    static String encrypted(String xml,String tag) {
        var matcher = Pattern.compile("<" + tag + "\\b[^>]*><!\\[CDATA\\[(.*?)]]>",Pattern.DOTALL).matcher(xml);
        if(!matcher.find() || matcher.group(1).isBlank())return null;
        String hex=matcher.group(1).strip();
        if(!hex.matches("[0-9A-Fa-f]+"))throw new IllegalArgumentException("QRC 加密编码无效");
        return hex;
    }
    static String lyricContent(String text) {
        var matcher = Pattern.compile("LyricContent=\"([^\"]*)\"",Pattern.DOTALL).matcher(text);
        String content = matcher.find() ? matcher.group(1) : text;
        return content.replace("&lt;","<").replace("&gt;",">").replace("&quot;","\"")
                .replace("&apos;","'").replace("&amp;","&");
    }
}
