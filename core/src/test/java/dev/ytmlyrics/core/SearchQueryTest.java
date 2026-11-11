package dev.ytmlyrics.core;

import org.junit.Test;
import static org.junit.Assert.*;

public class SearchQueryTest {
    @Test public void automaticAudioAndUnknownTypeSimplifyAllFieldsAndKeepArtist() {
        for(var type:new Song.PlaybackType[]{Song.PlaybackType.UNKNOWN,Song.PlaybackType.AUDIO}) {
            var song=new Song("id","繁體測試","樂隊","專輯",180000,type);
            var query=SearchQuery.of(song,"");
            assertEquals("繁体测试",query.title());assertEquals("乐队",query.artist());assertEquals("专辑",query.album());
            assertEquals("繁体测试 乐队",query.keywords());
            assertEquals("繁體測試",song.title());assertEquals("樂隊",song.artist());assertEquals("專輯",song.album());
        }
    }
    @Test public void knownVideoAutomaticSearchOmitsPublisherButKeepsPlaybackIdentity() {
        var song=new Song("","繁體測試","影片發佈者","專輯",180000,Song.PlaybackType.VIDEO);
        String key="繁體測試\u001f影片發佈者\u001f180000";
        var query=SearchQuery.of(song,"");
        assertEquals("",query.artist());assertEquals("繁体测试",query.keywords());
        assertEquals(key,song.key());assertEquals("影片發佈者",song.artist());
        assertEquals(new Song("id","繁體測試","樂隊","專輯",180000).key(),
                new Song("id","繁體測試","影片發佈者","專輯",180000,Song.PlaybackType.VIDEO).key());
    }
    @Test public void manualVideoQueryConvertsButKeepsEveryUserWordAndWhitespace() {
        var song=new Song("id","別的歌","影片發佈者","",180000,Song.PlaybackType.VIDEO);
        assertEquals("  繁体测试 影片发布者 Live  ",SearchQuery.of(song,"  繁體測試 影片發佈者 Live  ").keywords());
    }
    @Test public void metadataSeparatorsBecomeSingleSpacesWithoutChangingSongIdentity() {
        var song=new Song("","繁體、現場","甲、　乙和丙","合輯 和 現場",180000);
        var query=SearchQuery.of(song,"");
        assertEquals("繁体 现场",query.title());
        assertEquals("甲 乙 丙",query.artist());
        assertEquals("合辑 现场",query.album());
        assertEquals("繁体 现场 甲 乙 丙",query.keywords());
        assertEquals("繁體、現場\u001f甲、　乙和丙\u001f180000",song.key());
        assertEquals("合輯 和 現場",song.album());
    }
    @Test public void manualKeywordsKeepSeparatorsAndWhitespace() {
        var song=new Song("yt","原曲","甲、乙和丙","",180000);
        assertEquals("  合辑、甲和乙  ",SearchQuery.of(song,"  合輯、甲和乙  ").keywords());
        assertEquals("原曲 甲 乙 丙",SearchQuery.of(song,"  ").keywords());
    }
    @Test public void ytmCollaboratorTagsAreRemovedBeforeSearchNormalization() {
        var song = new Song("", "舊歌（合作音樂人：甲、乙） (Live) (合作音乐人: 丙)",
                "樂隊（合作音乐人：客席）", "專輯（ 合作音樂人 : 丁 ）", 180000);
        var query = SearchQuery.of(song, "");
        assertEquals("旧歌 (Live)", query.title());
        assertEquals("乐队", query.artist());
        assertEquals("专辑", query.album());
        assertEquals("旧歌 (Live) 乐队", query.keywords());
        assertEquals(song.title() + "\u001f" + song.artist() + "\u001f180000", song.key());
        assertEquals("旧歌 (Live)", SearchQuery.of(song.withPlaybackType(Song.PlaybackType.VIDEO), "").keywords());
    }

    @Test public void ordinaryVersionTagsAndManuallyEnteredCreditsStayInTheQuery() {
        var song = new Song("yt", "原曲 (合作音乐人访谈) (feat. 甲) (Remix)", "歌手", "", 180000);
        assertEquals("原曲 (合作音乐人访谈) (feat. 甲) (Remix) 歌手", SearchQuery.of(song, "").keywords());
        assertEquals("  原曲（合作音乐人：甲）  ", SearchQuery.of(song, "  原曲（合作音樂人：甲）  ").keywords());
    }
    @Test public void conversionPreservesMixedNonChineseTextAndSimplifiedInput() {
        var song=new Song("id","Love與夢 - Remix かな カナ 한국어 🎵","Artist","",10000);
        assertEquals("Love与梦 - Remix かな カナ 한국어 🎵 Artist",SearchQuery.of(song,"").keywords());
        assertEquals("简体中文 English 123 & かな カナ 한국어 🎵",SearchQuery.of(song,"简体中文 English 123 & かな カナ 한국어 🎵").keywords());
    }
    @Test public void comparisonRetainsNullAndUnicodeNormalizationContracts() {
        assertEquals("",ChineseText.simplify(null));assertEquals("",Matching.normalize(null));
        assertEquals("foo繁体",Matching.normalize("ＦＯＯ　繁體"));
    }
}
