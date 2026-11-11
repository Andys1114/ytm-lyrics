package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;

/** Use independent evidence only after all sources fail to produce a direct match. */
final class RecordingFallback {
    private RecordingFallback() {}

    static Candidate select(Song song, List<Candidate> candidates) {
        boolean artistMissing = Matching.normalize(MetadataText.withoutCollaborators(song.artist())).isEmpty();
        boolean unknownPerformer = song.isVideo() || artistMissing;
        String album = Matching.albumKey(song.album());
        boolean distinctAlbum = !album.isEmpty() && !Matching.sameTitle(song.title(), song.album());
        var byAlbum = new ArrayList<Candidate>();
        var byTiming = new ArrayList<Candidate>();
        for (var candidate : candidates) {
            if (!Matching.sameTitle(song.title(), candidate.title()) || !LyricsResults.displayable(candidate)
                    || Matching.normalize(MetadataText.withoutCollaborators(candidate.artist())).isEmpty() || candidate.instrumental()) continue;
            boolean sameArtists = Matching.sameArtists(song.artist(), candidate.artist());
            if (!sameArtists && !unknownPerformer) continue;
            boolean durationMissing = song.durationMs() <= 0 || candidate.durationMs() <= 0;
            boolean close = Matching.near(song.durationMs(), candidate.durationMs(), 1000);
            if (Matching.albumMatches(song, candidate)
                    && ((sameArtists && durationMissing && (song.durationMs() > 0 || candidate.durationMs() > 0))
                    || (artistMissing && distinctAlbum && close))) byAlbum.add(candidate);
            if (unknownPerformer && close && (!distinctAlbum || Matching.albumMatches(song, candidate)
                    || Matching.albumKey(candidate.album()).isEmpty())) byTiming.add(candidate);
        }
        // If a stronger class exists but conflicts, don't fall through to a weaker one.
        if (!byAlbum.isEmpty()) return unambiguousBest(song, byAlbum, false);
        if (byTiming.size() < 2) return null;
        var agreed = new ArrayList<Candidate>();
        for (var candidate : byTiming) {
            for (var other : byTiming) {
                if (!candidate.sourceId().equals(other.sourceId()) && sameRecording(candidate, other)) {
                    agreed.add(candidate);
                    break;
                }
            }
        }
        return unambiguousBest(song, agreed, true);
    }

    private static Candidate unambiguousBest(Song song, List<Candidate> candidates, boolean corroborated) {
        Candidate best = null;
        for (var candidate : candidates)
            if (best == null || Matching.compare(song, candidate, best) > 0) best = candidate;
        if (best == null) return null;
        for (var candidate : candidates) {
            if (candidate == best) continue;
            if (corroborated ? !sameRecording(best, candidate)
                    : !Matching.sameArtists(best.artist(), candidate.artist())) return null;
        }
        // An unknown-duration winner must not hide two incompatible known durations.
        if (!corroborated) {
            long min = Long.MAX_VALUE, max = 0;
            for (var candidate : candidates) if (candidate.durationMs() > 0) {
                min = Math.min(min, candidate.durationMs());
                max = Math.max(max, candidate.durationMs());
            }
            if (max > 0 && max - min > 2000) return null;
        }
        return best;
    }

    private static boolean sameRecording(Candidate first, Candidate second) {
        String firstAlbum = Matching.albumKey(first.album()), secondAlbum = Matching.albumKey(second.album());
        return Matching.sameArtists(first.artist(), second.artist())
                && Matching.near(first.durationMs(), second.durationMs(), 1000)
                && (firstAlbum.isEmpty() || secondAlbum.isEmpty() || firstAlbum.equals(secondAlbum))
                && LyricAlignment.compare(first.timeline(), second.timeline()).corroboratesRecording();
    }
}
