package dev.ytmlyrics.core;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class Matching {
    private static final Pattern SUFFIX = Pattern.compile("\\s*[\\[(]([^()\\[\\]]+)[\\])]\\s*$");
    private static final Set<String> DISPLAY = Set.of("officialvideo", "officialmusicvideo", "officialaudio",
            "officiallyricvideo", "lyric", "lyrics", "lyricsvideo", "lyricvideo", "官方mv", "官方音乐视频");
    private static final Set<String> LIVE = Set.of("live", "liveversion", "现场", "现场版", "演唱会版", "ライブ", "ライブ版");
    private static final Pattern CREDIT_SEPARATOR = Pattern.compile("(?i)[、,;]|\\s+(?:&|and|x|/|feat\\.?|ft\\.?|featuring)\\s+|×");
    private Matching() {}
    public static String normalize(String value) {
        if (value == null) return "";
        return ChineseText.simplify(Normalizer.normalize(value, Normalizer.Form.NFKC)).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{Z}\\s]+", "");
    }
    /** Remove presentation labels only; Live, remix, acoustic and version names remain significant. */
    private static String title(String value) {
        if (value == null) return "";
        String text = Normalizer.normalize(MetadataText.withoutCollaborators(value), Normalizer.Form.NFKC);
        for (var suffix = SUFFIX.matcher(text); suffix.find(); suffix = SUFFIX.matcher(text)) {
            String label = normalize(suffix.group(1));
            if (DISPLAY.contains(label)) text = text.substring(0, suffix.start());
            else if (LIVE.contains(label) && !normalize(text.substring(0, suffix.start())).isEmpty())
                return normalize(text.substring(0, suffix.start())) + "\u001flive";
            else break;
        }
        return normalize(text);
    }
    private static String artist(String value) {
        return normalize(value == null ? "" : value.replaceAll("(?i)\\s+-\\s+Topic\\s*$", ""));
    }
    private static Set<String> credits(String value, boolean splitChineseAnd) {
        String text = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return Arrays.stream(CREDIT_SEPARATOR.split(text)).flatMap(credit ->
                        Arrays.stream(splitChineseAnd ? credit.split("和") : new String[]{credit}))
                .map(Matching::artist).filter(name -> !name.isEmpty()).collect(Collectors.toSet());
    }
    static boolean sameTitle(String left, String right) {
        String key = title(left);
        return !key.isEmpty() && key.equals(title(right));
    }
    static boolean sameArtists(String left, String right) {
        left = MetadataText.withoutCollaborators(left);
        right = MetadataText.withoutCollaborators(right);
        String key = artist(left), otherKey = artist(right);
        if (key.isEmpty() || otherKey.isEmpty()) return false;
        var leftCredits = credits(left, false);
        var rightCredits = credits(right, false);
        return (leftCredits.size() == 1 && rightCredits.size() == 1 && key.equals(otherKey))
                || (!leftCredits.isEmpty() && leftCredits.equals(rightCredits))
                // Treat 和 as a separator only if the other source names the same distinct performers.
                || (rightCredits.size() > 1 && left.indexOf('和') >= 0 && credits(left, true).equals(rightCredits))
                || (leftCredits.size() > 1 && right.indexOf('和') >= 0 && credits(right, true).equals(leftCredits));
    }
    static boolean near(long left, long right, long tolerance) {
        return left > 0 && right > 0 && Math.abs(left - right) <= tolerance;
    }
    /** Direct match; incomplete metadata is considered separately after the source queries. */
    public static boolean reliable(Song song, Candidate candidate) {
        return candidate.hasLyrics() && sameTitle(song.title(), candidate.title()) && sameArtists(song.artist(), candidate.artist())
                && near(song.durationMs(), candidate.durationMs(), 2000);
    }

    /** Positive means left is preferable among candidates of the same evidence tier. */
    public static int compare(Song song, Candidate left, Candidate right) {
        int order = Boolean.compare(albumMatches(song,left),albumMatches(song,right));
        if (order != 0) return order;
        // Source durations are often rounded to seconds. Treat <=1 s as equivalent,
        // then prefer lyrics quality instead of a meaningless sub-second advantage.
        long leftDelta = durationGap(song,left);
        long rightDelta = durationGap(song,right);
        order = Boolean.compare(leftDelta<=1000,rightDelta<=1000);
        if (order != 0) return order;
        order = Integer.compare(left.quality(),right.quality());
        if (order != 0) return order;
        order = Double.compare(translationCoverage(left),translationCoverage(right));
        if (order != 0) return order;
        return Long.compare(rightDelta,leftDelta);
    }
    private static long durationGap(Song song, Candidate candidate) {
        return song.durationMs() > 0 && candidate.durationMs() > 0
                ? Math.abs(song.durationMs() - candidate.durationMs()) : Long.MAX_VALUE;
    }
    static boolean albumMatches(Song song,Candidate candidate) {
        String album=albumKey(song.album());
        return !album.isEmpty() && album.equals(albumKey(candidate.album()));
    }
    static String albumKey(String value) { return normalize(MetadataText.withoutCollaborators(value)); }
    private static double translationCoverage(Candidate candidate) {
        var lines=candidate.timeline().stream().filter(line -> !line.text().isBlank()).toList();
        return lines.isEmpty() ? 0 : (double)lines.stream().filter(line -> !line.translation().isBlank()).count()/lines.size();
    }
}
