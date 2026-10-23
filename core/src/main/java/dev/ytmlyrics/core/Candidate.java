package dev.ytmlyrics.core;

import java.util.List;

public record Candidate(String sourceId, String id, String title, String artist, String album,
                        long durationMs, String plain, String synced, boolean instrumental,
                        List<TimedLyrics.Line> enhanced, String enhancementError) {
    public Candidate {
        enhanced = enhanced == null ? List.of() : List.copyOf(enhanced);
        enhancementError = enhancementError == null ? "" : enhancementError;
    }
    public Candidate(String sourceId, String id, String title, String artist, String album,
                     long durationMs, String plain, String synced, boolean instrumental) {
        this(sourceId, id, title, artist, album, durationMs, plain, synced, instrumental, List.of(), "");
    }
    public List<TimedLyrics.Line> timeline() { return enhanced.isEmpty() ? TimedLyrics.fromLrc(synced, durationMs) : enhanced; }
    public List<Lrc.Line> lines() { return timeline().stream().map(line -> new Lrc.Line(line.startMs(), line.text())).toList(); }
    public boolean hasSynced() { return !timeline().isEmpty(); }
    public boolean hasWords() { return enhanced.stream().anyMatch(line -> !line.words().isEmpty()); }
    public boolean hasTranslation() { return enhanced.stream().anyMatch(line -> !line.translation().isBlank()); }
    public int quality() { return hasWords() ? 2 : hasSynced() ? 1 : hasLyrics() ? 0 : -1; }
    public boolean hasLyrics() { return hasSynced() || (plain != null && !plain.isBlank()); }
    public String label() { return title + " — " + artist + (album == null || album.isBlank() ? "" : " · " + album); }
    /** An enrichment outage must not erase an already cached word timeline or translation. */
    public Candidate preserveCache(Candidate cached) {
        if (!sourceId.equals(cached.sourceId) || !id.equals(cached.id)) throw new IllegalArgumentException("不能替换绑定来源或歌曲 ID");
        Candidate data = quality() < cached.quality() || (quality() == cached.quality() && !hasTranslation() && cached.hasTranslation()) ? cached : this;
        List<TimedLyrics.Line> retained = data.enhanced.stream().map(line -> {
            if (!line.translation().isBlank()) return line;
            var previous = cached.timeline().stream().filter(old -> old.text().equals(line.text())
                    && Math.abs(old.startMs()-line.startMs())<=1000 && !old.translation().isBlank()).findFirst();
            return previous.map(old -> new TimedLyrics.Line(line.startMs(),line.endMs(),line.text(),old.translation(),line.words())).orElse(line);
        }).toList();
        return new Candidate(cached.sourceId, cached.id, cached.title, cached.artist, cached.album, cached.durationMs,
                data.plain, data.synced, data.instrumental, retained, enhancementError);
    }
}
