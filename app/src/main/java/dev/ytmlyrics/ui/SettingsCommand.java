package dev.ytmlyrics.ui;

import dev.ytmlyrics.core.LyricsOffset;
import org.json.JSONException;
import org.json.JSONObject;

/** The local settings document can request only these explicit operations. */
public record SettingsCommand(String action, String sourceId, int offsetMs, boolean enabled,
        String songKey, String text) {
    // Earlier local documents use 100ms messages; retain their meaning alongside the new 500ms UI.
    private static final int COMMAND_QUANTUM_MS=100;
    public boolean appliesTo(dev.ytmlyrics.core.Song song) {
        return !java.util.List.of("select","search","offset","step","clearBinding","confirmClear").contains(action)
                || (song!=null && song.key().equals(songKey));
    }
    public static SettingsCommand parse(String encoded) {
        if (encoded == null || encoded.length() > 4096) throw new IllegalArgumentException("Invalid settings command");
        try {
            JSONObject value = new JSONObject(encoded);
            String action = value.getString("action");
            String source = "";
            int offset = 0;
            boolean enabled = false;
            String songKey = value.optString("songKey", "");
            String text = "";
            if (songKey.length() > 1024) throw new IllegalArgumentException("Invalid song identity");
            switch (action) {
                case "close", "choose", "sync", "settings", "retry", "confirmClear", "clearBinding" -> { }
                case "search" -> {
                    Object raw = value.get("value");
                    if (!(raw instanceof String query) || query.isBlank() || query.length() > 256)
                        throw new IllegalArgumentException("Invalid query");
                    text = query.strip();
                }
                case "select" -> {
                    source = value.getString("sourceId");
                    Object raw = value.get("value");
                    if (!source.matches("[a-zA-Z0-9_-]{1,128}") || !(raw instanceof String id) || id.isBlank() || id.length() > 512)
                        throw new IllegalArgumentException("Invalid candidate identity");
                    text = id;
                }
                case "translation", "preload" -> enabled = booleanValue(value);
                case "source", "moveUp" -> {
                    source = value.getString("sourceId");
                    if (!source.matches("[a-zA-Z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid source ID");
                    if (action.equals("source")) enabled = booleanValue(value);
                }
                case "offset", "step" -> {
                    Object raw = value.get("value");
                    if (!(raw instanceof Number n) || !Double.isFinite(n.doubleValue())
                            || n.doubleValue() != n.intValue() || n.intValue() % COMMAND_QUANTUM_MS != 0)
                        throw new IllegalArgumentException("Invalid offset");
                    offset = LyricsOffset.clamp(n.intValue());
                    if (action.equals("step") && Math.abs(n.intValue()) != LyricsOffset.STEP_MS
                            && Math.abs(n.intValue()) != COMMAND_QUANTUM_MS)
                        throw new IllegalArgumentException("Invalid step");
                }
                default -> throw new IllegalArgumentException("Unknown settings action");
            }
            return new SettingsCommand(action, source, offset, enabled, songKey, text);
        } catch (JSONException error) {
            throw new IllegalArgumentException("Invalid settings command", error);
        }
    }

    private static boolean booleanValue(JSONObject value) throws JSONException {
        Object raw = value.get("value");
        if (!(raw instanceof Boolean flag)) throw new IllegalArgumentException("Invalid toggle");
        return flag;
    }
}
