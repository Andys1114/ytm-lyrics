package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.Arrays;
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

    private record Translation(long timeMs, String text, String original) {}
    private record TranslationMatch(int translation, long startMs, long distance) {}
    /** Provider translations belong to their nearest line, not the first line within tolerance. */
    public static List<Line> translate(List<Line> lines, String lrc) {
        var translations = Lrc.parse(lrc).stream().map(line -> new Translation(line.timeMs(),line.text(),null)).toList();
        return translated(lines,translations,false);
    }
    /** Same-recording enrichment may fill gaps only when the original text also matches. */
    static List<Line> fillTranslations(List<Line> lines, List<Line> supplement) {
        var translations = supplement.stream().map(line -> new Translation(line.startMs,line.translation,line.text)).toList();
        return translated(lines,translations,true);
    }
    private static List<Line> translated(List<Line> lines, List<Translation> translations, boolean keepExisting) {
        if (translations.isEmpty()) return lines;
        int[] matches = new int[lines.size()];
        Arrays.fill(matches,-1);
        List<TranslationMatch> pending = new ArrayList<>();
        for (int i = 0; i < translations.size(); i++) {
            Translation translation = translations.get(i);
            // A supplement's untranslated original still reserves its place among simultaneous repeats.
            if (translation.text.isBlank() && translation.original == null) continue;
            int nearest = -1; long distance = 1001;
            for (int j = 0; j < lines.size(); j++) {
                Line line = lines.get(j);
                if (translation.original != null && !translation.original.equals(line.text)) continue;
                long delta = Math.abs(translation.timeMs-line.startMs);
                if (delta < distance) { nearest = j; distance = delta; }
            }
            if (nearest >= 0) pending.add(new TranslationMatch(i,lines.get(nearest).startMs,distance));
        }
        // Exact/closer matches win; equal-distance translations retain their source order.
        pending.sort(Comparator.comparingLong(TranslationMatch::distance));
        for (TranslationMatch match : pending) {
            Translation translation = translations.get(match.translation);
            for (int j = 0; j < lines.size(); j++) {
                Line line = lines.get(j);
                // Equal-time siblings may share the nearest slot, never a farther timestamp.
                if (matches[j] < 0 && line.startMs == match.startMs
                        && (translation.original == null || translation.original.equals(line.text))) {
                    matches[j] = match.translation; break;
                }
            }
        }
        List<Line> result = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            Line line = lines.get(i);
            String translated = line.translation;
            if (matches[i] >= 0 && (!keepExisting || translated.isBlank())) translated = translations.get(matches[i]).text;
            result.add(new Line(line.startMs, line.endMs, line.text, translated, line.words));
        }
        return List.copyOf(result);
    }
}
