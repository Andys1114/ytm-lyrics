package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.LyricsSource;
import dev.ytmlyrics.core.Song;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PersistenceTest {
    @Test public void upgradeAppendsQqWithoutChangingOldDisabledSourceOrBinding() {
        var preferences = new MemoryPreferences();
        preferences.edit().putString("source-order", "lrclib").putBoolean("enabled:lrclib", false).apply();
        var song = new Song("video", "Song", "Artist", "", 180000);
        var lyric = new Candidate("lrclib", "1", "Song", "Artist", "", 180000, "自写测试", null, false);
        new BindingStore(preferences).put(song, lyric);
        var registry = new SourceRegistry(preferences);
        assertEquals(List.of("lrclib", "qqmusic", "netease"), registry.ordered().stream().map(LyricsSource::id).toList());
        assertEquals(List.of("qqmusic", "netease"), registry.enabled().stream().map(LyricsSource::id).toList());
        assertEquals("QQ 音乐", registry.nameOf("qqmusic"));
        assertEquals(lyric, new BindingStore(preferences).get(song));
        registry.moveUp("qqmusic"); registry.setEnabled("qqmusic", false);
        var restored = new SourceRegistry(preferences);
        assertEquals("qqmusic", restored.ordered().get(0).id()); assertEquals(List.of("netease"),restored.enabled().stream().map(LyricsSource::id).toList());
    }
    private static LyricsSource source(String id) {
        return new LyricsSource() {
            public String id() { return id; }
            public String name() { return id; }
            public List<Candidate> search(Song song, String query) { return List.of(); }
        };
    }
    @Test public void orderAndDisabledStateSurviveNewRegistry() {
        var preferences = new MemoryPreferences();
        var providers = List.of(source("a"), source("b"), source("c"));
        var registry = new SourceRegistry(preferences, providers);
        registry.setEnabled("b", false); registry.moveUp("c");
        var restored = new SourceRegistry(preferences, providers);
        assertEquals(List.of("a", "c", "b"), restored.ordered().stream().map(LyricsSource::id).toList());
        assertEquals(List.of("a", "c"), restored.enabled().stream().map(LyricsSource::id).toList());
    }
    @Test public void removedProviderIsIgnoredAndNewProviderAppended() {
        var preferences = new MemoryPreferences();
        preferences.edit().putString("source-order", "b,removed,a").apply();
        var registry = new SourceRegistry(preferences, List.of(source("a"), source("b"), source("new")));
        assertEquals(List.of("b", "a", "new"), registry.ordered().stream().map(LyricsSource::id).toList());
    }
    @Test public void bindingIsPerPlaybackItemAndCanBeReplacedOrCleared() {
        var preferences = new MemoryPreferences();
        var store = new BindingStore(preferences);
        var song = new Song("one", "同名歌", "歌手", "", 180000);
        var other = new Song("two", "同名歌", "歌手", "", 180000);
        var lyric = new Candidate("a", "7", "同名歌", "歌手", "", 180000, "自写测试", "[00:01]自写测试", false);
        store.put(song, lyric);
        assertEquals(lyric, new BindingStore(preferences).get(song));
        assertNull(store.get(other));
        var replacement = new Candidate("b", "8", "同名歌", "歌手", "", 180000, "另一份测试", null, false);
        store.put(song, replacement); assertEquals(replacement, store.get(song));
        store.remove(song); assertNull(store.get(song));
    }
    @Test public void brokenBindingIsRemovedWithoutTouchingOtherPreferences() {
        var preferences = new MemoryPreferences();
        var song = new Song("one", "Song", "Artist", "", 1000);
        preferences.edit().putString("binding:one", "broken json").putBoolean("enabled:a", false).apply();
        assertNull(new BindingStore(preferences).get(song));
        assertFalse(preferences.contains("binding:one"));
        assertFalse(preferences.getBoolean("enabled:a", true));
    }
}
