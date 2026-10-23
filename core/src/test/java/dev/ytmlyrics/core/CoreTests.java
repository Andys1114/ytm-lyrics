package dev.ytmlyrics.core;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CoreTests {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static Candidate lyric(String source, String id, String title, String artist, long duration, boolean sync) {
        return new Candidate(source, id, title, artist, "Album", duration, "Hello\nWorld", sync ? "[00:01.00]Hello\n[00:02]World" : null, false);
    }
    private static LyricsSource source(String id, Candidate... candidates) {
        return new LyricsSource() {
            public String id() { return id; }
            public String name() { return id; }
            public List<Candidate> search(Song song, String query) { return List.of(candidates); }
        };
    }
    public static void main(String[] args) {
        runContracts();
    }
    @org.junit.Test public void contracts() { runContracts(); }
    @org.junit.Test public void byteOrderMarkAndEmptyTimeline() {
        org.junit.Assert.assertEquals(1000, Lrc.parse("\uFEFF[00:01]Hello").get(0).timeMs());
        org.junit.Assert.assertEquals(-1, Lrc.activeLine(List.of(), 10000));
    }
    @org.junit.Test public void allSourcesAreComparedEvenAfterReliableWordResult() {
        Song song = new Song("id", "Song", "Artist", "", 180000);
        Candidate sync = new Candidate("one","1","Song","Artist","",180000,null,null,false,
                TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"),"");
        AtomicBoolean queried = new AtomicBoolean(false);
        LyricsSource later = new LyricsSource() {
            public String id() { return "two"; }
            public String name() { return "two"; }
            public List<Candidate> search(Song s, String q) { queried.set(true); return List.of(); }
        };
        var result = new Resolver().resolve(song, List.of(source("one", sync), later), () -> false);
        org.junit.Assert.assertTrue(queried.get());
        org.junit.Assert.assertSame(sync, result.selected());
    }
    @org.junit.Test public void cancellationDuringRequestDiscardsReturnedLyrics() {
        AtomicBoolean changedSong = new AtomicBoolean(false);
        LyricsSource delayed = new LyricsSource() {
            public String id() { return "one"; }
            public String name() { return "one"; }
            public List<Candidate> search(Song s, String q) {
                changedSong.set(true);
                return List.of(lyric("one", "1", "Song", "Artist", 180000, true));
            }
        };
        var result = new Resolver().resolve(new Song("id", "Song", "Artist", "", 180000), List.of(delayed), changedSong::get);
        org.junit.Assert.assertTrue(result.cancelled());
        org.junit.Assert.assertNull(result.selected());
        org.junit.Assert.assertTrue(result.candidates().isEmpty());
    }
    @org.junit.Test public void matchingCandidatesAreAutomaticallyRankedByLyricsQuality() {
        Song song = new Song("id", "Song", "Artist", "", 180000);
        var result = new Resolver().resolve(song, List.of(source("one",
                lyric("one", "1", "Song", "Artist", 180000, true),
                lyric("one", "2", "Song", "Artist", 180000, false))), () -> false);
        org.junit.Assert.assertEquals("1",result.selected().id());
    }
    @org.junit.Test public void plainFallbackPreservesSourcePriority() {
        Song song = new Song("id", "Song", "Artist", "", 180000);
        Candidate first = lyric("one", "1", "Song", "Artist", 180000, false);
        var result = new Resolver().resolve(song, List.of(source("one", first),
                source("two", lyric("two", "2", "Song", "Artist", 180000, false))), () -> false);
        org.junit.Assert.assertSame(first, result.selected());
    }
    private static void runContracts() {
        checks = 0;
        var lines = Lrc.parse("[ar:Artist]\n[offset:-100]\n[00:02.5][00:04.050]World\n[00:01.01]Hello");
        check(lines.size() == 3, "multiple timestamps");
        check(lines.get(0).timeMs() == 910 && lines.get(1).timeMs() == 2400 && lines.get(2).timeMs() == 3950, "fraction, sort, offset");
        check(Lrc.activeLine(lines, 0) == -1, "before first line");
        check(Lrc.activeLine(lines, 2400) == 1, "at boundary");
        check(Lrc.activeLine(lines, 950) == 0, "backwards seek");
        check(Lrc.activeLine(lines, 10000) == 2, "after last line");
        check(Lrc.parse("[00:99]bad\n[foo]\nplain").isEmpty(), "invalid tags");
        check(Lrc.parse("[00:00.01]lead").get(0).timeMs() == 10, "centiseconds");
        Song song = new Song("video", "Song", "Artist", "Album", 180000);
        Candidate text = lyric("one", "1", "Song", "Artist", 180000, false);
        Candidate sync = lyric("two", "2", "Song", "Artist", 181000, true);
        Candidate live = lyric("two", "3", "Song (Live)", "Artist", 180000, true);
        check(Matching.reliable(song, sync), "exact match duration tolerance");
        check(!Matching.reliable(song, live), "live is not studio");
        check(!Matching.reliable(new Song("x", "Song", "Artist", "", 0), sync), "unknown duration is manual");
        var result = new Resolver().resolve(song, List.of(source("one", text), source("two", sync)), () -> false);
        check(result.selected() == sync, "lower priority synced beats plain");
        result = new Resolver().resolve(song, List.of(source("one", text), source("two", live)), () -> false);
        check(result.selected() == text, "plain used after all synced sources");
        result = new Resolver().resolve(song, List.of(source("one", sync, lyric("one", "other", "Song", "Artist", 180000, true))), () -> false);
        check(result.selected().id().equals("other") && result.candidates().size() == 2, "equivalent lyrics prefer closer duration");
        LyricsSource broken = new LyricsSource() {
            public String id() { return "bad"; }
            public String name() { return "bad"; }
            public List<Candidate> search(Song s, String q) throws IOException { throw new IOException("429"); }
        };
        result = new Resolver().resolve(song, List.of(broken, source("two", sync)), () -> false);
        check(result.selected() == sync && result.errors().size() == 1, "source failure advances with reason");
        AtomicBoolean cancelled = new AtomicBoolean(true);
        result = new Resolver().resolve(song, List.of(source("one", sync)), cancelled::get);
        check(result.cancelled() && result.selected() == null, "stale request discarded");
        check(new Song("id", "different", "artist", "", 2).key().equals("id"), "binding uses video identity");
        System.out.println("PASS " + checks + " core checks");
    }
}
