package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/** Source timestamps in milliseconds; an empty words list means line sync, never fabricated karaoke. */
public final class TimedLyrics {
    public record Word(long startMs, long endMs, String text) {
        public Word {
            if (startMs < 0 || endMs < startMs || text == null) throw new IllegalArgumentException("字词时间无效");
        }
    }
    public record Line(long startMs, long endMs, String text, String translation, List<Word> words) {
        public Line {
            if (startMs < 0 || endMs < startMs || text == null) throw new IllegalArgumentException("歌词行时间无效");
            translation = translation == null ? "" : translation;
            words = List.copyOf(words);
            long previous = startMs;
            for (Word word : words) {
                if (word.startMs < previous || word.endMs > endMs) throw new IllegalArgumentException("字词超出行时间或次序无效");
                previous = word.startMs;
            }
            if (!words.isEmpty() && !text.equals(words.stream().map(Word::text).reduce("", String::concat)))
                throw new IllegalArgumentException("字词与原文不一致");
        }
    }
    private static final Pattern ROW = Pattern.compile("^\\[(\\d+),(\\d+)](.*)$");
    private static final Pattern YRC_WORD = Pattern.compile("\\((\\d+),(\\d+),-?\\d+\\)(.*?)(?=\\(\\d+,\\d+,-?\\d+\\)|$)");
    private static final Pattern QRC_WORD = Pattern.compile("(.*?)\\((\\d+),(\\d+)\\)");
    private TimedLyrics() {}

    public static List<Line> fromLrc(String lrc, long duration) {
        List<Lrc.Line> parsed = Lrc.parse(lrc);
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < parsed.size(); i++) {
            Lrc.Line row = parsed.get(i);
            long end = i + 1 < parsed.size() ? parsed.get(i + 1).timeMs() : Math.max(row.timeMs(), duration);
            result.add(new Line(row.timeMs(), end, row.text(), "", List.of()));
        }
        return List.copyOf(result);
    }
    public static List<Line> yrc(String text) { return words(text, true); }
    public static List<Line> qrc(String text) { return words(text, false); }
    private static List<Line> words(String text, boolean yrc) {
        if (text == null || text.isBlank()) return List.of();
        List<Line> result = new ArrayList<>();
        for (String row : text.replace("\uFEFF", "").split("\\R")) {
            var line = ROW.matcher(row.strip());
            if (!line.matches()) continue; // Provider metadata is not a lyric line.
            long start = Long.parseLong(line.group(1)), end = Math.addExact(start, Long.parseLong(line.group(2)));
            String content = line.group(3);
            var matcher = (yrc ? YRC_WORD : QRC_WORD).matcher(content);
            List<Word> words = new ArrayList<>();
            int consumed = 0;
            while (matcher.find()) {
                if (matcher.start() != consumed) throw new IllegalArgumentException("逐字歌词存在无法识别的片段");
                long from = Long.parseLong(matcher.group(yrc ? 1 : 2));
                long to = Math.addExact(from, Long.parseLong(matcher.group(yrc ? 2 : 3)));
                words.add(new Word(from, to, matcher.group(yrc ? 3 : 1)));
                end = Math.max(end, to);
                consumed = matcher.end();
            }
            if (words.isEmpty()) continue;
            if (consumed != content.length()) throw new IllegalArgumentException("逐字歌词末尾缺少时间");
            String original = words.stream().map(Word::text).reduce("", String::concat);
            result.add(new Line(start, end, original, "", words));
        }
        result.sort(Comparator.comparingLong(Line::startMs));
        return List.copyOf(result);
    }

    /** Provider translations must have a nearby timestamp; never attach by array index. */
    public static List<Line> translate(List<Line> lines, String lrc) {
        List<Lrc.Line> translations = Lrc.parse(lrc);
        if (translations.isEmpty()) return lines;
        boolean[] used = new boolean[translations.size()];
        List<Line> result = new ArrayList<>();
        for (Line line : lines) {
            int nearest = -1; long distance = 1001;
            for (int i = 0; i < translations.size(); i++) {
                long delta = Math.abs(translations.get(i).timeMs() - line.startMs);
                if (!used[i] && delta < distance && !translations.get(i).text().isBlank()) { nearest = i; distance = delta; }
            }
            String translated = line.translation;
            if (nearest >= 0) { used[nearest] = true; translated = translations.get(nearest).text(); }
            result.add(new Line(line.startMs, line.endMs, line.text, translated, line.words));
        }
        return List.copyOf(result);
    }
}
