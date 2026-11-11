package dev.ytmlyrics.core;

import java.util.Comparator;
import java.util.List;

/** Result eligibility is separate from decoding and preserving legacy binding data. */
public final class LyricsResults {
    private LyricsResults() { }

    public static boolean displayable(Candidate candidate) {
        return candidate != null && candidate.hasSynced();
    }

    /** Keep real line/word timelines in source order; never synthesize timestamps. */
    public static List<Candidate> timed(List<Candidate> candidates) {
        return candidates.stream().filter(LyricsResults::displayable).toList();
    }

    /** One ranked list for the chooser. Playback selection uses its own confidence thresholds. */
    public static List<Candidate> ranked(Song song, String manualQuery, List<Candidate> candidates) {
        if (song == null) return timed(candidates);
        String title = key(song.title());
        String query = manualQuery.isBlank() || manualQuery.equals(SearchQuery.of(song, "").keywords())
                ? "" : Matching.normalize(manualQuery);
        return candidates.stream().filter(LyricsResults::displayable).map(candidate -> {
            String otherTitle = key(candidate.title());
            String artist = key(candidate.artist());
            int queryMatch = query.isEmpty() ? 0 : query.equals(otherTitle) || query.equals(artist) ? 2
                    : (otherTitle + artist).contains(query) || (artist + otherTitle).contains(query) ? 1 : 0;
            int titleMatch = Matching.sameTitle(song.title(), candidate.title()) ? 2
                    : !title.isEmpty() && !otherTitle.isEmpty() && (title.contains(otherTitle) || otherTitle.contains(title)) ? 1 : 0;
            long gap = song.durationMs() > 0 && candidate.durationMs() > 0
                    ? Math.abs(song.durationMs() - candidate.durationMs()) : Long.MAX_VALUE;
            int timing = gap <= 1000 ? 3 : gap <= 2000 ? 2 : gap == Long.MAX_VALUE ? 1 : 0;
            return new Ranked(candidate, queryMatch, titleMatch, Matching.sameArtists(song.artist(), candidate.artist()),
                    Matching.albumMatches(song, candidate), timing, candidate.hasWords(), candidate.hasTranslation(), gap);
        }).sorted(ORDER).map(Ranked::candidate).toList();
    }

    private static String key(String value) { return Matching.normalize(MetadataText.withoutCollaborators(value)); }

    private record Ranked(Candidate candidate, int query, int title, boolean artist, boolean album,
                          int timing, boolean words, boolean translation, long gap) {}
    private static final Comparator<Ranked> ORDER = Comparator.comparingInt(Ranked::query)
            .thenComparingInt(Ranked::title).thenComparing(Ranked::artist).thenComparing(Ranked::album)
            .thenComparingInt(Ranked::timing).thenComparing(Ranked::words).thenComparing(Ranked::translation)
            .reversed().thenComparingLong(Ranked::gap);
}
