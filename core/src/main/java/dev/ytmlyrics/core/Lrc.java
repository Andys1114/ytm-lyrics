package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Lrc {
    public record Line(long timeMs, String text) {}
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("(?im)^\\[offset:([+-]?\\d+)]\\s*$");
    private Lrc() {}

    public static List<Line> parse(String text) {
        if (text == null || text.isBlank()) return List.of();
        text = text.replace("\uFEFF", "");
        long offset = 0;
        Matcher offsetMatch = OFFSET.matcher(text);
        while (offsetMatch.find()) {
            try { offset = Long.parseLong(offsetMatch.group(1)); } catch (NumberFormatException ignored) {}
        }
        List<Line> lines = new ArrayList<>();
        for (String row : text.split("\\R")) {
            Matcher match = TIME.matcher(row);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (match.find() && row.substring(end, match.start()).isBlank()) {
                try {
                    long minutes = Long.parseLong(match.group(1));
                    int seconds = Integer.parseInt(match.group(2));
                    if (minutes > 1440 || seconds >= 60) break;
                    String fraction = match.group(3);
                    long millis = fraction == null ? 0 : Long.parseLong((fraction + "000").substring(0, 3));
                    times.add(Math.max(0, minutes * 60000 + seconds * 1000L + millis + offset));
                    end = match.end();
                } catch (NumberFormatException ignored) { break; }
            }
            if (times.isEmpty()) continue;
            String lyric = row.substring(end).strip();
            for (long time : times) lines.add(new Line(time, lyric));
        }
        lines.sort(Comparator.comparingLong(Line::timeMs));
        return List.copyOf(lines);
    }

    /** Last line at or before playback time; -1 before the first lyric. */
    public static int activeLine(List<Line> lines, long positionMs) {
        int low = 0, high = lines.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMs() <= positionMs) low = middle + 1;
            else high = middle;
        }
        return low - 1;
    }
}
