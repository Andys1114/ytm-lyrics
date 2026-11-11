package dev.ytmlyrics.core;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RecordingFallbackTest {
    private static final String LRC = "[00:01]先听风\n[00:04]灯已亮\n[00:07]等天晴\n[00:10]再回家";

    private static Candidate line(String source, String title, String artist, String album, long duration) {
        return new Candidate(source, artist, title, artist, album, duration, null, LRC, false);
    }

    private static LyricsSource source(Candidate candidate) {
        return new LyricsSource() {
            public String id() { return candidate.sourceId(); }
            public String name() { return id(); }
            public List<Candidate> search(Song song, String query) { return List.of(candidate); }
        };
    }

    @Test public void normalizesCreditOrderAndLocalizedLiveTagsWithoutLosingVersion() {
        var song = new Song("yt", "日不落（現場版）[Official Music Video]", "甲、乙和丙 - Topic", "", 180000);
        var live = line("qq", "日不落 (Live)", "丙, 甲, 乙", "", 181900);
        assertSame(live, Resolver.select(song, List.of(live)));
        assertNull(Resolver.select(song, List.of(line("qq", "日不落", "甲, 乙, 丙", "", 180000))));
        assertNull(Resolver.select(song, List.of(line("qq", "日不落 (Live)", "甲, 乙", "", 180000))));
        assertFalse(Matching.sameArtists("周和", "周"));
        assertFalse(Matching.sameArtists("AB", "A, B"));
    }

    @Test public void missingDurationUsesSameAlbumAndAllArtistsButRejectsIncompatibleCuts() {
        var song = new Song("yt", "Song", "Artist", "Original Album", 0);
        var original = line("qq", "Song", "Artist", "Original Album", 180000);
        var otherAlbum = line("netease", "Song", "Artist", "Other Album", 180000);
        var differentCut = line("netease", "Song", "Artist", "Original Album", 215000);
        assertSame(original, Resolver.select(song, List.of(original, otherAlbum)));
        assertNull(Resolver.select(song, List.of(original, differentCut)));
        assertNull(Resolver.select(new Song("yt", "Song", "Artist", "", 0), List.of(original)));
        var noCandidateDuration = line("qq", "Song", "Artist", "Original Album", 0);
        assertSame(noCandidateDuration, Resolver.select(new Song("yt", "Song", "Artist", "Original Album", 180000),
                List.of(noCandidateDuration)));
    }

    @Test public void missingArtistCanUseDistinctAlbumAndCloseDurationButNotKnownWrongSinger() {
        var albumMatch = line("qq", "Song", "Singer", "Original Album", 180500);
        var missingArtist = new Song("yt", "Song", "", "Original Album", 180000, Song.PlaybackType.AUDIO);
        assertSame(albumMatch, Resolver.select(missingArtist, List.of(albumMatch)));
        var cover = line("netease", "Song", "Cover singer", "Original Album", 180500);
        assertNull(Resolver.select(missingArtist, List.of(albumMatch, cover)));
        var knownArtist = new Song("yt", "Song", "Another performer", "Original Album", 180000, Song.PlaybackType.AUDIO);
        assertNull(Resolver.select(knownArtist, List.of(albumMatch)));
        var titleOnlyAlbum = line("qq", "Song", "Singer", "Song", 180500);
        assertNull(Resolver.select(new Song("yt", "Song", "", "Song", 180000), List.of(titleOnlyAlbum)));
    }

    @Test public void videoWithoutAlbumWaitsForIndependentTimelineAgreementAndStillSupportsSupplement() {
        var video = new Song("yt", "Song", "Video uploader", "", 180000, Song.PlaybackType.VIDEO);
        var words = new Candidate("qq", "words", "Song", "Singer", "", 180000, null, null, false,
                TimedLyrics.qrc("[1000,700]先听风(1000,700)\n[4000,700]灯已亮(4000,700)\n"
                        + "[7000,700]等天晴(7000,700)\n[10000,700]再回家(10000,700)"), "");
        var translated = new Candidate("netease", "lines", "Song", "Singer", "", 180500, null, LRC, false,
                TimedLyrics.translate(TimedLyrics.fromLrc(LRC, 180500), "[00:01]Listen to the wind"), "");
        var provisional = new ArrayList<Candidate>();
        var result = new Resolver().resolve(video, List.of(source(words), source(translated)), () -> false, provisional::add);
        assertTrue(provisional.isEmpty());
        assertEquals(List.of(words, translated), result.candidates());
        assertEquals("words", result.selected().id());
        assertEquals("netease", result.selected().translationSourceId());
        assertEquals("Listen to the wind", result.selected().timeline().get(0).translation());
        assertEquals(result.selected(), Resolver.select(video, result.candidates()));
        assertNull(Resolver.select(video, List.of(words)));
        var sameSource = line("qq", "Song", "Singer", "", 180000);
        assertNull(Resolver.select(video, List.of(words, sameSource)));
        var outOfSync = new Candidate("netease", "far", "Song", "Singer", "", 180000, null,
                LRC.replace("00:", "01:"), false);
        assertNull(Resolver.select(video, List.of(words, outOfSync)));
        var covers = List.of(words, translated, line("qq", "Song", "Cover", "", 180000),
                line("netease", "Song", "Cover", "", 180000));
        assertNull(Resolver.select(video, covers));
    }
}
