package dev.ytmlyrics.source;

import android.content.SharedPreferences;
import dev.ytmlyrics.core.LyricsSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Register a provider here; persisted order/enabled state never depends on array positions. */
public final class SourceRegistry {
    private final LinkedHashMap<String, LyricsSource> providers = new LinkedHashMap<>();
    private final SharedPreferences preferences;
    public SourceRegistry(SharedPreferences preferences) {
        this(preferences, List.of(new LrclibSource(), new QqMusicSource(), new NeteaseSource()));
    }
    SourceRegistry(SharedPreferences preferences, List<LyricsSource> implementations) {
        this.preferences = preferences;
        implementations.forEach(this::register);
    }
    private void register(LyricsSource source) { providers.put(source.id(), source); }
    public List<LyricsSource> ordered() {
        List<LyricsSource> result = new ArrayList<>();
        for (String id : preferences.getString("source-order", "").split(",")) {
            LyricsSource source = providers.get(id);
            if (source != null && !result.contains(source)) result.add(source);
        }
        for (LyricsSource source : providers.values()) if (!result.contains(source)) result.add(source);
        return result;
    }
    public List<LyricsSource> enabled() { return ordered().stream().filter(s -> isEnabled(s.id())).toList(); }
    public String nameOf(String id) { return providers.containsKey(id) ? providers.get(id).name() : id; }
    public LyricsSource provider(String id) { return providers.get(id); }
    public boolean isEnabled(String id) { return preferences.getBoolean("enabled:" + id, true); }
    public void setEnabled(String id, boolean enabled) { preferences.edit().putBoolean("enabled:" + id, enabled).apply(); }
    public void setTranslation(boolean enabled) { preferences.edit().putBoolean("show-translation", enabled).apply(); }
    public boolean preloadEnabled(){return preferences.getBoolean("preload-enabled",true);}
    public void setPreloadEnabled(boolean enabled){preferences.edit().putBoolean("preload-enabled",enabled).apply();}
    public void moveUp(String id) {
        List<LyricsSource> list = ordered();
        for (int i = 1; i < list.size(); i++) {
            if (list.get(i).id().equals(id)) { var value = list.remove(i); list.add(i - 1, value); break; }
        }
        preferences.edit().putString("source-order", String.join(",", list.stream().map(LyricsSource::id).toList())).apply();
    }
}
