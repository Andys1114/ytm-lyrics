package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;

/** Supplements the winning word timeline without changing its original source or ranking. */
final class TranslationSupplement {
    private record Aligned(List<TimedLyrics.Line> lines, int added) {}

    private TranslationSupplement() {}

    static Candidate apply(Song song, Candidate primary, List<Candidate> candidates) {
        if (primary == null || !primary.hasWords()) return primary;
        var timeline = primary.timeline();
        if (timeline.stream().noneMatch(line -> !line.text().isBlank() && line.translation().isBlank())) return primary;
        String album = Matching.albumKey(primary.album());
        Aligned best = null;
        String donorId = "";
        for (Candidate donor : candidates) {
            if (primary.sourceId().equals(donor.sourceId()) || !donor.hasTranslation()
                    || !Matching.sameTitle(primary.title(), donor.title())
                    || !Matching.sameArtists(primary.artist(), donor.artist())
                    || (song.durationMs() > 0 && !Matching.near(song.durationMs(), donor.durationMs(), 2000))
                    || !Matching.near(primary.durationMs(), donor.durationMs(), 2000)) continue;
            String otherAlbum = Matching.albumKey(donor.album());
            if (!album.isEmpty() && !otherAlbum.isEmpty() && !album.equals(otherAlbum)) continue;
            Aligned aligned = align(timeline, donor.timeline());
            if (aligned.added() > (best == null ? 0 : best.added())) {
                best = aligned;
                donorId = donor.sourceId();
            }
        }
        if (best == null) return primary;
        return new Candidate(primary.sourceId(), primary.id(), primary.title(), primary.artist(), primary.album(),
                primary.durationMs(), primary.plain(), primary.synced(), primary.instrumental(), best.lines(),
                primary.enhancementError(), donorId);
    }

    private static Aligned align(List<TimedLyrics.Line> primary, List<TimedLyrics.Line> donor) {
        var pairs = LyricAlignment.compare(primary, donor).pairs();
        List<TimedLyrics.Line> combined = new ArrayList<>(primary);
        int added = 0;
        for (var pair : pairs) {
            int index = pair.first();
            var line = primary.get(index);
            String text = donor.get(pair.second()).translation();
            if (line.translation().isBlank() && !text.isBlank()) {
                combined.set(index, new TimedLyrics.Line(line.startMs(), line.endMs(), line.text(), text, line.words()));
                added++;
            }
        }
        return new Aligned(combined, added);
    }
}
