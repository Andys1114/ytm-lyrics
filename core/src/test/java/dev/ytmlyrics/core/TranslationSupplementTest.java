package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TranslationSupplementTest {
    private static final Song SONG = new Song("yt", "Song", "Artist", "Album", 180000);
    private static final List<String> ORIGINAL = List.of("晚風輕輕吹", "燈亮了", "等天亮", "一起回家");

    private static Candidate candidate(String source, String title, String album, long duration, boolean words,
                                       long delay, List<String> originals, List<String> translations) {
        List<TimedLyrics.Line> lines = new ArrayList<>();
        for (int i = 0; i < originals.size(); i++) {
            long start = 1000 + i * 2500L + delay;
            String text = originals.get(i);
            lines.add(new TimedLyrics.Line(start, start + 800, text, translations.get(i),
                    words ? List.of(new TimedLyrics.Word(start + 100, start + 600, text)) : List.of()));
        }
        return new Candidate(source, "track", title, "Artist", album, duration, null, null, false, lines, "");
    }

    private static Candidate primary() {
        return candidate("qq", "Song", "Album", 180000, true, 0, ORIGINAL, List.of("已有译文", "", "", ""));
    }

    private static Candidate donor(String source, long delay, List<String> text, List<String> translations) {
        return candidate(source, "Song", "Album", 180000, false, delay, text, translations);
    }

    private static LyricsSource source(Candidate candidate) {
        return new LyricsSource() {
            public String id() { return candidate.sourceId(); }
            public String name() { return id(); }
            public List<Candidate> search(Song song, String query) { return List.of(candidate); }
        };
    }

    @Test public void liveResolutionKeepsOriginalTimelineAndUnmodifiedCandidatePool() {
        var primary = primary();
        var donor = donor("netease", 350, List.of("晚风轻轻吹！", "灯 亮了", "等天亮", "一起回家"),
                List.of("不要覆盖", "The lights came on", "Wait for morning", "Go home together"));
        List<Candidate> provisional = new ArrayList<>();
        var result = new Resolver().resolve(SONG, List.of(source(primary), source(donor)), () -> false, provisional::add);
        var mixed = result.selected();
        assertEquals(List.of(primary), provisional);
        assertEquals(List.of(primary, donor), result.candidates());
        assertEquals("qq", mixed.sourceId());
        assertEquals("track", mixed.id());
        assertEquals("netease", mixed.translationSourceId());
        assertEquals(List.of("已有译文", "The lights came on", "Wait for morning", "Go home together"),
                mixed.timeline().stream().map(TimedLyrics.Line::translation).toList());
        for (int i = 0; i < mixed.timeline().size(); i++) {
            var line = mixed.timeline().get(i);
            var original = primary.timeline().get(i);
            assertEquals(original.startMs(), line.startMs());
            assertEquals(original.endMs(), line.endMs());
            assertEquals(original.text(), line.text());
            assertEquals(original.words(), line.words());
        }
        assertEquals(mixed, Resolver.select(SONG, result.candidates()));
    }

    @Test public void differentVersionAlbumOrTimingDoesNotSupplyTranslation() {
        var primary = primary();
        var translated = List.of("One", "Two", "Three", "Four");
        var live = candidate("live", "Song (Live)", "Album", 180000, false, 0, ORIGINAL, translated);
        var reissue = candidate("reissue", "Song", "Different Album", 180000, false, 0, ORIGINAL, translated);
        var drifting = donor("drifting", 1100, ORIGINAL, translated);
        var partialChorus = donor("other", 0, List.of("晚風輕輕吹", "燈亮了", "不同原文", "另一个结尾"), translated);
        assertSame(primary, Resolver.select(SONG, List.of(primary, live, reissue, drifting, partialChorus)));
    }

    @Test public void repeatedLinesRequireUniqueMatchesAndUnmatchedRowsStayEmpty() {
        var primary = primary();
        List<TimedLyrics.Line> donorLines = new ArrayList<>(
                donor("netease", 0, ORIGINAL, List.of("One", "Two", "Three", "Four")).timeline());
        // The nearby repeat disqualifies "燈亮了"; six other pairs meet the overall floor.
        var extra = new TimedLyrics.Line(11500, 12000, "多等一会", "", List.of());
        var tail = new TimedLyrics.Line(14000, 14500, "那就出发", "", List.of());
        var end = new TimedLyrics.Line(17000, 17500, "走到海边", "", List.of());
        var primaryLines = new ArrayList<>(primary.timeline());
        primaryLines.add(extra);
        primaryLines.add(tail);
        primaryLines.add(end);
        var main = new Candidate("qq", "track", "Song", "Artist", "Album", 180000, null, null, false, primaryLines, "");
        donorLines.add(new TimedLyrics.Line(3700, 4200, "燈亮了", "Wrong repeat", List.of()));
        donorLines.add(extra);
        donorLines.add(tail);
        donorLines.add(end);
        var donor = new Candidate("netease", "track", "Song", "Artist", "Album", 180000, null, null, false, donorLines, "");
        var mixed = Resolver.select(SONG, List.of(main, donor));
        assertEquals("netease", mixed.translationSourceId());
        assertEquals("", mixed.timeline().get(1).translation());
        assertEquals("Three", mixed.timeline().get(2).translation());
    }

    @Test public void selectsOneDonorByMissingLinesThenSourceOrder() {
        var primary = primary();
        var first = donor("first", 0, ORIGINAL, List.of("One", "Two", "", ""));
        var more = donor("more", 0, ORIGINAL, List.of("", "", "Three", "Four"));
        var tie = donor("tie", 0, ORIGINAL, List.of("", "B", "C", ""));
        var mixed = Resolver.select(SONG, List.of(primary, first, more, tie));
        assertEquals("more", mixed.translationSourceId());
        assertEquals(List.of("已有译文", "", "Three", "Four"), mixed.timeline().stream().map(TimedLyrics.Line::translation).toList());
    }
}
