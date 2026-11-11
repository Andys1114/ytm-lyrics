package dev.ytmlyrics.ui;

import dev.ytmlyrics.core.LyricsOffset;
import dev.ytmlyrics.core.Song;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Snapshot only: the runtime still owns source order, persistence and song identity. */
public record SettingsState(Song song, String message, String selectedSource, String quality,
        String rendererError, boolean translation, boolean preload, String preloadLabel,
        int offsetMs, List<Source> sources, String route, String query, boolean busy,
        boolean bound, List<Choice> candidates) {
    public record Source(String id, String name, boolean enabled) { }
    public record Choice(String sourceId, String id, String title, String artist, String album,
            long durationMs, String source, boolean words, boolean translation, boolean selected) { }
    public SettingsState { sources = List.copyOf(sources); candidates = List.copyOf(candidates); }
    public SettingsState(Song song, String message, String selectedSource, String quality,
            String rendererError, boolean translation, boolean preload, String preloadLabel,
            int offsetMs, List<Source> sources) {
        this(song,message,selectedSource,quality,rendererError,translation,preload,preloadLabel,
                offsetMs,sources,"settings","",false,false,List.of());
    }

    public String json() {
        try {
            JSONArray rows = new JSONArray();
            for (Source source : sources) rows.put(new JSONObject().put("id", source.id())
                    .put("name", source.name()).put("enabled", source.enabled()));
            JSONArray choices = new JSONArray();
            for (Choice choice : candidates) choices.put(new JSONObject().put("sourceId",choice.sourceId())
                    .put("id",choice.id()).put("title",choice.title()).put("artist",choice.artist())
                    .put("album",choice.album()==null?"":choice.album()).put("durationMs",choice.durationMs())
                    .put("source",choice.source()).put("words",choice.words()).put("translation",choice.translation())
                    .put("selected",choice.selected()));
            return new JSONObject().put("hasSong", song != null)
                    .put("songKey",song==null?"":song.key()).put("route",route).put("query",query)
                    .put("busy",busy).put("bound",bound).put("candidates",choices)
                    .put("title", song == null ? "还没有播放歌曲" : song.title())
                    .put("artist", song == null ? "播放后可选择歌词、调整同步" : song.artist())
                    .put("message", message).put("selectedSource", selectedSource).put("quality", quality)
                    .put("rendererError", rendererError).put("translation", translation)
                    .put("preload", preload).put("preloadLabel", preloadLabel)
                    .put("offsetMs", LyricsOffset.clamp(offsetMs)).put("sources", rows).toString();
        } catch (JSONException error) { throw new IllegalArgumentException("Cannot encode settings", error); }
    }
}
