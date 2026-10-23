package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.LyricsSource;
import dev.ytmlyrics.core.Song;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class LrclibSource implements LyricsSource {
    private static final String BASE = "https://lrclib.net/api/";
    interface Transport { String get(String url, boolean allowMissing) throws IOException; }
    private final Transport transport;
    public LrclibSource() { this(LrclibSource::get); }
    LrclibSource(Transport transport) { this.transport = transport; }
    public String id() { return "lrclib"; }
    public String name() { return "LRCLIB"; }
    public List<Candidate> search(Song song, String query) throws Exception {
        if (query.isBlank()) {
            String url = BASE + "get?track_name=" + encode(song.title()) + "&artist_name=" + encode(song.artist());
            if (!song.album().isBlank()) url += "&album_name=" + encode(song.album());
            if (song.durationMs() >= 1000 && song.durationMs() <= 3600000) url += "&duration=" + song.durationMs() / 1000.0;
            String exact = transport.get(url, true);
            if (exact != null) return List.of(decode(new JSONObject(exact)));
        }
        String url = query.isBlank()
                ? BASE + "search?track_name=" + encode(song.title()) + "&artist_name=" + encode(song.artist())
                : BASE + "search?q=" + encode(query);
        JSONArray array = new JSONArray(transport.get(url, false));
        List<Candidate> found = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) found.add(decode(array.getJSONObject(i)));
        return List.copyOf(found);
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static String get(String url, boolean allowMissing) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
        connection.setConnectTimeout(8000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "YtmLyrics/0.1.0 (Android; personal Xposed module)");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (allowMissing && status == 404) return null;
            if (status == 429) throw new IOException("请求过于频繁，请稍后重试");
            if (status != 200) throw new IOException("服务返回 HTTP " + status);
            try (InputStream input = connection.getInputStream()) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally { connection.disconnect(); }
    }
    public static Candidate decode(JSONObject json) {
        return new Candidate("lrclib", json.optString("id"), json.optString("trackName"),
                json.optString("artistName"), json.optString("albumName"),
                Math.round(json.optDouble("duration", 0) * 1000), nullable(json, "plainLyrics"),
                nullable(json, "syncedLyrics"), json.optBoolean("instrumental"));
    }
    private static String nullable(JSONObject json, String key) { return json.isNull(key) ? null : json.optString(key, null); }
}
