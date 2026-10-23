package dev.ytmlyrics.source;

import android.content.SharedPreferences;
import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.TimedLyrics;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Explicit user selections survive restarts in YTM's private app storage. */
public final class BindingStore {
    private final SharedPreferences preferences;
    public BindingStore(SharedPreferences preferences) { this.preferences = preferences; }
    public Candidate get(Song song) {
        String json = preferences.getString("binding:" + song.key(), null);
        if (json == null) return null;
        try {
            return decode(new JSONObject(json));
        } catch (JSONException error) {
            preferences.edit().remove("binding:" + song.key()).apply();
            return null;
        }
    }
    static Candidate decode(JSONObject value) throws JSONException {
            List<TimedLyrics.Line> enhanced;
            String warning = "";
            try { enhanced = LyricsJson.lines(value.optJSONArray("enhanced")); }
            catch (JSONException | IllegalArgumentException error) { enhanced = List.of(); warning = "增强缓存损坏，等待重新取词"; }
            return new Candidate(value.getString("source"), value.getString("id"), value.getString("title"),
                    value.getString("artist"), value.optString("album"), value.getLong("duration"),
                    value.isNull("plain") ? null : value.optString("plain"),
                    value.isNull("synced") ? null : value.optString("synced"), value.optBoolean("instrumental"), enhanced, warning);
    }
    public void put(Song song, Candidate candidate) {
        try {
            preferences.edit().putString("binding:" + song.key(),encode(candidate).toString()).apply();
        } catch (JSONException error) { throw new IllegalArgumentException("歌词绑定无法保存", error); }
    }
    static JSONObject encode(Candidate candidate) throws JSONException {
            String legacySynced=candidate.synced();
            if((legacySynced==null || legacySynced.isBlank()) && candidate.hasSynced()) {
                StringBuilder lrc=new StringBuilder();
                for(var line:candidate.timeline()) {
                    long time=line.startMs();
                    lrc.append(String.format(java.util.Locale.ROOT,"[%02d:%02d.%03d]%s\n",time/60000,(time/1000)%60,time%1000,line.text()));
                }
                legacySynced=lrc.toString();
            }
            JSONObject value = new JSONObject().put("source", candidate.sourceId()).put("id", candidate.id())
                    .put("title", candidate.title()).put("artist", candidate.artist()).put("album", candidate.album())
                    .put("duration", candidate.durationMs()).put("plain", candidate.plain() == null ? JSONObject.NULL : candidate.plain())
                    .put("synced", legacySynced == null ? JSONObject.NULL : legacySynced)
                    .put("schema", 3).put("instrumental",candidate.instrumental()).put("enhanced",LyricsJson.lines(candidate.enhanced()));
            return value;
    }
    public void remove(Song song) { preferences.edit().remove("binding:" + song.key()).apply(); }
}
