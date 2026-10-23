package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.TimedLyrics;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** One explicit millisecond schema for binding persistence and the local renderer. */
public final class LyricsJson {
    private LyricsJson() {}
    public static JSONArray lines(List<TimedLyrics.Line> lines) throws JSONException {
        JSONArray array = new JSONArray();
        for (var line : lines) {
            JSONArray words = new JSONArray();
            for (var word : line.words()) words.put(new JSONObject().put("startTime",word.startMs()).put("endTime",word.endMs()).put("word",word.text()));
            array.put(new JSONObject().put("startTime",line.startMs()).put("endTime",line.endMs()).put("text",line.text())
                    .put("translatedLyric",line.translation()).put("words",words));
        }
        return array;
    }
    public static List<TimedLyrics.Line> lines(JSONArray array) throws JSONException {
        List<TimedLyrics.Line> lines = new ArrayList<>();
        if (array == null) return List.of();
        for (int i = 0; i < array.length(); i++) {
            JSONObject line = array.getJSONObject(i); JSONArray encoded = line.getJSONArray("words");
            List<TimedLyrics.Word> words = new ArrayList<>();
            for (int j = 0; j < encoded.length(); j++) {
                JSONObject word = encoded.getJSONObject(j);
                words.add(new TimedLyrics.Word(word.getLong("startTime"),word.getLong("endTime"),word.getString("word")));
            }
            lines.add(new TimedLyrics.Line(line.getLong("startTime"),line.getLong("endTime"),line.getString("text"),line.optString("translatedLyric"),words));
        }
        return List.copyOf(lines);
    }
    public static String payload(Candidate candidate, long serial, boolean translation) {
        try {
            return new JSONObject().put("serial",serial).put("lines",lines(candidate.timeline())).put("wordTimed",candidate.hasWords())
                    .put("durationMs",candidate.durationMs()).put("translation",translation).toString();
        } catch (JSONException error) { throw new IllegalArgumentException("歌词显示数据无法编码",error); }
    }
    public static String playback(long serial,long positionMs,boolean playing,float speed,long epoch) {
        return playback(serial,positionMs,playing,speed,epoch,0);
    }
    public static String playback(long serial,long positionMs,boolean playing,float speed,long epoch,int advanceMs) {
        try {
            return new JSONObject().put("serial",serial).put("positionMs",Math.max(0,positionMs)).put("playing",playing)
                    .put("speed",Float.isFinite(speed) ? Math.max(0,speed) : 0).put("epoch",epoch)
                    .put("offsetMs",dev.ytmlyrics.core.LyricsOffset.clamp(advanceMs)).toString();
        } catch (JSONException error) { throw new IllegalArgumentException("播放状态无法编码",error); }
    }
}
