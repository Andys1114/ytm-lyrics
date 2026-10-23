package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.TimedLyrics;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class EnhancedSourcesTest {
    // Encrypted, zlib-compressed self-written fixture; no third-party lyric content.
    static final String QRC="ee7befb746f93831d42dcdf2e21f0d5d924943e250c9b32f6d731ce7c8a4cd014e64c40abdc08c734961f693c8f695ef5ccf4d46d7ddfaa21789068553ede644bbdb86ea7a8490d9f62eeb41abbd23b3985938be65d11f29cc995806eecbb071";
    private static Candidate cached(){return new Candidate("qqmusic","123","Song","Artist","",10000,null,"[00:01]自写\n[00:03]歌词",false);}
    private static String base(){return "{\"code\":0,\"retcode\":0,\"lyric\":\""+Base64.getEncoder().encodeToString(cached().synced().getBytes(StandardCharsets.UTF_8))+"\",\"trans\":\""+Base64.getEncoder().encodeToString("[00:01]Original\n[00:03]Lyrics".getBytes(StandardCharsets.UTF_8))+"\"}";}
    @Test public void qrcVariantDecryptsKnownSelfWrittenFixture() throws Exception {
        String decoded=QrcCrypto.decrypt(QRC);
        var lines=TimedLyrics.qrc(QqMusicSource.lyricContent(decoded));
        assertEquals(2,lines.size());assertEquals("自写",lines.get(0).text());assertEquals(1400,lines.get(0).words().get(1).startMs());
    }
    @Test public void invalidHexBlocksOrCompressedDataFailExplicitly() {
        for(String hex:List.of("xyz","1234","0000000000000000")){
            try{QrcCrypto.decrypt(hex);fail();}catch(IOException expected){assertNotNull(expected.getMessage());}
        }
    }
    @Test public void qqRefreshUsesNumericIdPostAndMergesSourceTranslation() throws Exception {
        int[] calls={0};
        var source=new QqMusicSource((url,form)->{
            calls[0]++;assertFalse(url.contains("client_search_cp"));
            if(form==null){assertTrue(url.contains("musicid=123&"));return base();}
            assertTrue(form.endsWith("musicid=123"));assertTrue(form.contains("lrctype=4"));
            return "<result>0</result><content type=\"file\"><![CDATA["+QRC+"]]></content>";
        });
        var candidate=source.refresh(cached());
        assertEquals(2,calls[0]);assertTrue(candidate.hasWords());assertTrue(candidate.hasTranslation());
        assertEquals("Original",candidate.timeline().get(0).translation());assertEquals("123",candidate.id());assertTrue(candidate.enhancementError().isBlank());
    }
    @Test public void qrcFailurePreservesLrcAndShowsEnhancementReason() throws Exception {
        var candidate=new QqMusicSource((url,form)->{if(form!=null)throw new IOException("HTTP 503");return base();}).refresh(cached());
        assertTrue(candidate.hasSynced());assertFalse(candidate.hasWords());assertTrue(candidate.enhancementError().contains("503"));
    }
    @Test public void wordLyricsRemainAvailableIfLineEndpointFails() throws Exception {
        var candidate=new QqMusicSource((url,form)->{if(form==null)throw new IOException("HTTP 503");return "<result>0</result><content><![CDATA["+QRC+"]]></content>";}).refresh(cached());
        assertTrue(candidate.hasWords());assertTrue(candidate.enhancementError().contains("503"));
    }
    @Test public void oldJsonBindingIsReadAndEnhancedWithoutChangingKeyOrSourceSettings() throws Exception {
        var prefs=new MemoryPreferences();
        prefs.edit().putString("binding:yt","{\"source\":\"qqmusic\",\"id\":\"123\",\"title\":\"Song\",\"artist\":\"Artist\",\"duration\":10000,\"synced\":\"[00:01]自写\"}")
                .putString("source-order","qqmusic,lrclib").putBoolean("enabled:lrclib",false).apply();
        var song=new Song("yt","Song","Artist","",10000);var store=new BindingStore(prefs);var old=store.get(song);
        assertEquals("123",old.id());assertTrue(old.hasSynced());assertFalse(old.hasWords());
        var rich=new Candidate(old.sourceId(),old.id(),old.title(),old.artist(),old.album(),old.durationMs(),old.plain(),old.synced(),false,
                TimedLyrics.translate(TimedLyrics.qrc(QqMusicSource.lyricContent(QrcCrypto.decrypt(QRC))),"[00:01]Original"),"");
        store.put(song,rich);var restored=store.get(song);
        assertEquals(rich,restored);assertTrue(restored.hasWords());assertTrue(restored.hasTranslation());
        assertEquals("qqmusic,lrclib",prefs.getString("source-order",""));assertFalse(prefs.getBoolean("enabled:lrclib",true));
    }
    @Test public void corruptEnhancementDoesNotEraseOldWorkingLineCache() throws Exception {
        var prefs=new MemoryPreferences();var song=new Song("yt","Song","Artist","",10000);var store=new BindingStore(prefs);store.put(song,cached());
        var data=new JSONObject(prefs.getString("binding:yt",""));data.put("enhanced",new org.json.JSONArray("[{\"startTime\":-1}]"));
        prefs.edit().putString("binding:yt",data.toString()).apply();
        var restored=store.get(song);assertNotNull(restored);assertTrue(restored.hasSynced());assertFalse(restored.enhancementError().isBlank());
        assertTrue(prefs.contains("binding:yt"));
    }
    @Test public void wordOnlyBindingsKeepALegacyLineProjectionForOldReaders() {
        var prefs=new MemoryPreferences();var song=new Song("yt","Song","Artist","",10000);var store=new BindingStore(prefs);
        var rich=new Candidate("qqmusic","123","Song","Artist","",10000,null,null,false,
                TimedLyrics.yrc("[1000,1000](1000,400,0)自(1400,600,0)写"),"");
        store.put(song,rich);var loaded=store.get(song);
        assertTrue(loaded.hasWords());assertEquals("自写",dev.ytmlyrics.core.Lrc.parse(loaded.synced()).get(0).text());
        assertEquals(1000,dev.ytmlyrics.core.Lrc.parse(loaded.synced()).get(0).timeMs());
    }
    @Test public void rendererPayloadPreservesTextAndEmptyLineWordsWithoutScriptInjection() throws Exception {
        var c=new Candidate("x","1","Song","Artist","",10000,null,"[00:01]</script><img onerror=alert(1)>",false);
        var payload=new JSONObject(LyricsJson.payload(c,42,true));
        assertEquals(42,payload.getLong("serial"));assertFalse(payload.getBoolean("wordTimed"));
        var row=payload.getJSONArray("lines").getJSONObject(0);assertEquals(0,row.getJSONArray("words").length());
        assertEquals("</script><img onerror=alert(1)>",row.getString("text"));
        var playback=new JSONObject(LyricsJson.playback(42,1234,false,Float.NaN,7));assertEquals(0,playback.getDouble("speed"),0);
    }
}
