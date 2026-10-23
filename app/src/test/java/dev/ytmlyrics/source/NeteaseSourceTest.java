package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Song;
import java.io.IOException;
import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class NeteaseSourceTest {
    private static final Song SONG=new Song("yt","Song","Artist","",10000);
    private static final String SEARCH="{\"code\":200,\"result\":{\"songs\":[{\"id\":123,\"name\":\"Song\",\"ar\":[{\"name\":\"Artist\"}],\"al\":{\"name\":\"Album\"},\"dt\":10000}]}}";
    private static final String LYRIC="{\"code\":200,\"lrc\":{\"lyric\":\"[00:01]自写\"},\"yrc\":{\"lyric\":\"[1000,1000](1000,400,0)自(1400,600,0)写\"},\"tlyric\":{\"lyric\":\"[00:01]Original\"}}";
    @Test public void searchMapsMetadataYrcAndSourceTranslation() throws Exception {
        var requests=new ArrayList<String>();
        var result=new NeteaseSource(url->{requests.add(url);return url.contains("cloudsearch")?SEARCH:LYRIC;}).search(SONG,"a & b").get(0);
        assertTrue(requests.get(0).contains("s=a+%26+b"));assertTrue(requests.get(1).contains("id=123&"));
        assertEquals("netease",result.sourceId());assertEquals("Album",result.album());assertEquals(10000,result.durationMs());
        assertTrue(result.hasWords());assertEquals("Original",result.timeline().get(0).translation());
    }
    @Test public void refreshUsesOriginalIdWithoutSearchingAndKeepsMetadata() throws Exception {
        var cached=new Candidate("netease","123","Chosen version","Artist","Album",10000,null,"[00:01]自写",false);
        var result=new NeteaseSource(url->{assertFalse(url.contains("cloudsearch"));assertTrue(url.contains("id=123&"));return LYRIC;}).refresh(cached);
        assertEquals(cached.id(),result.id());assertEquals(cached.title(),result.title());assertTrue(result.hasWords());
    }
    @Test public void legacyMetadataAndLrcWithoutYrcRemainUsable() throws Exception {
        String legacy=SEARCH.replace("\"ar\"","\"artists\"").replace("\"al\"","\"album\"").replace("\"dt\"","\"duration\"");
        var result=new NeteaseSource(url->url.contains("cloudsearch")?legacy:"{\"code\":200,\"lrc\":{\"lyric\":\"[00:01]自写\"}}").search(SONG,"").get(0);
        assertTrue(result.hasSynced());assertFalse(result.hasWords());assertEquals("Artist",result.artist());
    }
    @Test public void malformedYrcDoesNotEraseWorkingLrcAndReportsWhy() throws Exception {
        var result=new NeteaseSource(url->url.contains("cloudsearch")?SEARCH:LYRIC.replace("(1000,400,0)","(900,400,0)")).search(SONG,"").get(0);
        assertFalse(result.hasWords());assertTrue(result.hasSynced());assertFalse(result.enhancementError().isBlank());
    }
    @Test public void missingResponsesAndErrorsAreNotReportedAsNoMatches() throws Exception {
        for(String body:new String[]{"{\"code\":403}","{\"code\":200}","<html>blocked</html>"}) {
            try{new NeteaseSource(url->body).search(SONG,"");fail();}catch(IOException expected){assertNotNull(expected.getMessage());}
        }
        assertTrue(new NeteaseSource(url->"{\"code\":200,\"result\":{\"songCount\":0}}").search(SONG,"").isEmpty());
    }
}
