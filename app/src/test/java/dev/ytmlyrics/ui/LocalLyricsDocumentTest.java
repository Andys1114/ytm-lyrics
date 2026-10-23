package dev.ytmlyrics.ui;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

public class LocalLyricsDocumentTest {
    @Test public void mainDocumentReturnsBundledHtmlInsteadOfForbiddenResponse() {
        String html="<!doctype html><meta charset=\"UTF-8\"><p>自写歌词</p>";
        var response=new LocalLyricsDocument(html).request(LocalLyricsDocument.URL,"GET",true);
        assertEquals(200,response.status());
        assertEquals("text/html",response.mimeType());
        assertEquals("OK",response.reason());
        assertEquals(html,new String(response.body(),StandardCharsets.UTF_8));
        assertTrue(LocalLyricsDocument.URL.startsWith("https://"));
    }

    @Test public void allOtherLocationsMethodsAndSubframesStayBlocked() {
        var document=new LocalLyricsDocument("private page");
        for(String url:new String[]{"data:text/html;charset=utf-8;base64,", "https://example.org/", "file:///etc/hosts",
                "content://private", "https://ytm-lyrics.invalid/", LocalLyricsDocument.URL+"?other", LocalLyricsDocument.URL+"/extra"}) {
            var response=document.request(url,"GET",true);
            assertEquals(url,403,response.status());
            assertEquals(0,response.body().length);
        }
        assertEquals(403,document.request(LocalLyricsDocument.URL,"POST",true).status());
        assertEquals(403,document.request(LocalLyricsDocument.URL,"GET",false).status());
    }

    @Test public void productionDocumentIsServedIntactWithItsBridgeAndOfflinePolicy() throws Exception {
        String html=new String(Files.readAllBytes(Path.of("src/main/assets/amll/lyrics.html")),StandardCharsets.UTF_8);
        var response=new LocalLyricsDocument(html).request(LocalLyricsDocument.URL,"GET",true);
        String served=new String(response.body(),StandardCharsets.UTF_8);
        assertEquals(200,response.status());
        assertEquals(html,served);
        assertTrue(served.contains("YtmLyricsHost"));
        assertTrue(served.contains("YtmLyricsBridge"));
        assertTrue(served.contains("connect-src 'none'"));
    }
}
