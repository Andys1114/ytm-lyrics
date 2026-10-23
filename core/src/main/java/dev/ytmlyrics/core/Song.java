package dev.ytmlyrics.core;

/** A particular YTM playback item, not just a title shared by several recordings. */
public record Song(String id, String title, String artist, String album, long durationMs) {
    public Song {
        id = safe(id); title = safe(title); artist = safe(artist); album = safe(album);
    }
    private static String safe(String value) { return value == null ? "" : value; }
    public String key() {
        return id.isBlank() ? title + "\u001f" + artist + "\u001f" + durationMs : id;
    }
}
