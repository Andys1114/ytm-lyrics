package dev.ytmlyrics.core;

import java.text.Normalizer;
import java.util.Locale;

public final class Matching {
    private Matching() {}
    public static String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
    /** Remove presentation labels only; Live, remix, acoustic and version names remain significant. */
    private static String title(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return normalize(text.replaceAll("(?i)\\s*[\\(\\[]\\s*(?:official\\s+(?:music\\s+)?video|official\\s+audio|lyrics?(?:\\s+video)?)\\s*[\\)\\]]\\s*$", ""));
    }
    private static String artist(String value) {
        return normalize(value == null ? "" : value.replaceAll("(?i)\\s+-\\s+Topic\\s*$", ""));
    }
    /** Unknown duration or different recording labels still require explicit user confirmation. */
    public static boolean reliable(Song song, Candidate candidate) {
        return candidate.hasLyrics() && !song.title().isBlank() && !song.artist().isBlank()
                && !title(song.title()).isEmpty() && !artist(song.artist()).isEmpty()
                && title(song.title()).equals(title(candidate.title()))
                && artist(song.artist()).equals(artist(candidate.artist()))
                && song.durationMs() > 0 && candidate.durationMs() > 0
                && Math.abs(song.durationMs() - candidate.durationMs()) <= 2000;
    }

    /** Positive means left is preferable. Call only after the reliable-recording gate. */
    public static int compare(Song song, Candidate left, Candidate right) {
        int order = Boolean.compare(albumMatches(song,left),albumMatches(song,right));
        if (order != 0) return order;
        // Source durations are often rounded to seconds. Treat <=1 s as equivalent,
        // then prefer lyrics quality instead of a meaningless sub-second advantage.
        long leftDelta = Math.abs(song.durationMs()-left.durationMs());
        long rightDelta = Math.abs(song.durationMs()-right.durationMs());
        order = Boolean.compare(leftDelta<=1000,rightDelta<=1000);
        if (order != 0) return order;
        order = Integer.compare(left.quality(),right.quality());
        if (order != 0) return order;
        order = Double.compare(translationCoverage(left),translationCoverage(right));
        if (order != 0) return order;
        return Long.compare(rightDelta,leftDelta);
    }
    private static boolean albumMatches(Song song,Candidate candidate) {
        String album=normalize(song.album());
        return !album.isEmpty() && album.equals(normalize(candidate.album()));
    }
    private static double translationCoverage(Candidate candidate) {
        var lines=candidate.timeline().stream().filter(line -> !line.text().isBlank()).toList();
        return lines.isEmpty() ? 0 : (double)lines.stream().filter(line -> !line.translation().isBlank()).count()/lines.size();
    }
}
