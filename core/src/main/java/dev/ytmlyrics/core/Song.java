package dev.ytmlyrics.core;

/** A particular YTM playback item, not just a title shared by several recordings. */
public record Song(String id, String title, String artist, String album, long durationMs, PlaybackType playbackType) {
    public enum PlaybackType { UNKNOWN, AUDIO, VIDEO }
    public Song {
        id = safe(id); title = safe(title); artist = safe(artist); album = safe(album);
        playbackType = playbackType == null ? PlaybackType.UNKNOWN : playbackType;
    }
    public Song(String id, String title, String artist, String album, long durationMs) {
        this(id,title,artist,album,durationMs,PlaybackType.UNKNOWN);
    }
    public boolean isVideo() { return playbackType == PlaybackType.VIDEO; }
    public Song withPlaybackType(PlaybackType type) {
        return playbackType == type ? this:new Song(id,title,artist,album,durationMs,type);
    }
    private static String safe(String value) { return value == null ? "" : value; }
    public String key() {
        return id.isBlank() ? title + "\u001f" + artist + "\u001f" + durationMs : id;
    }
}
