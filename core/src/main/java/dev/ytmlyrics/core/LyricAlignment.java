package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** One-to-one matches with nearby real timestamps and broad agreement on both timelines. */
final class LyricAlignment {
    private static final long TOLERANCE_MS = 1000;
    private record Row(int index, long startMs, String original) {}
    record Pair(int first, int second) {}
    record Agreement(List<Pair> pairs, int distinct) {
        boolean corroboratesRecording() { return pairs.size() >= 3 && distinct >= 3; }
    }
    private static final Agreement NO_MATCH = new Agreement(List.of(), 0);

    private LyricAlignment() {}

    static Agreement compare(List<TimedLyrics.Line> first, List<TimedLyrics.Line> second) {
        var mainRows = rows(first);
        var otherRows = rows(second);
        int[] pairing = new int[mainRows.size()];
        Arrays.fill(pairing, -1);
        int[] reverseCount = new int[otherRows.size()];
        int lower = 0;
        for (int i = 0; i < mainRows.size(); i++) {
            Row main = mainRows.get(i);
            while (lower < otherRows.size() && otherRows.get(lower).startMs() - main.startMs() < -TOLERANCE_MS) lower++;
            for (int j = lower; j < otherRows.size(); j++) {
                Row other = otherRows.get(j);
                if (other.startMs() - main.startMs() > TOLERANCE_MS) break;
                if (!main.original().equals(other.original())) continue;
                pairing[i] = pairing[i] == -1 ? j : -2;
                reverseCount[j]++;
            }
        }
        var pairs = new ArrayList<Pair>();
        var originals = new HashSet<String>();
        int previous = -1;
        for (int i = 0; i < pairing.length; i++) {
            int j = pairing[i];
            if (j < 0 || reverseCount[j] != 1) continue;
            if (j <= previous) return NO_MATCH;
            previous = j;
            var row = mainRows.get(i);
            pairs.add(new Pair(row.index(), otherRows.get(j).index()));
            originals.add(row.original());
        }
        int longer = Math.max(mainRows.size(), otherRows.size());
        return pairs.size() * 4L >= longer * 3L ? new Agreement(List.copyOf(pairs), originals.size()) : NO_MATCH;
    }

    private static List<Row> rows(List<TimedLyrics.Line> timeline) {
        List<Row> result = new ArrayList<>();
        for (int i = 0; i < timeline.size(); i++) {
            var line = timeline.get(i);
            String original = Matching.normalize(line.text());
            if (!original.isEmpty()) result.add(new Row(i, line.startMs(), original));
        }
        result.sort(Comparator.comparingLong(Row::startMs));
        return result;
    }
}
