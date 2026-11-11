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

    @Test public void settingsDocumentCannotServeLyricsOrAnyExternalResource() throws Exception {
        String url="https://ytm-lyrics.invalid/settings.html";
        String html=new String(Files.readAllBytes(Path.of("src/main/assets/settings/settings.html")),StandardCharsets.UTF_8);
        var document=new LocalLyricsDocument(url,html);
        assertEquals(200,document.request(url,"GET",true).status());
        assertEquals(403,document.request(LocalLyricsDocument.URL,"GET",true).status());
        assertEquals(403,document.request(url,"GET",false).status());
        assertEquals(403,document.request(url,"POST",true).status());
        assertEquals(403,document.request("https://www.mdui.org/","GET",true).status());
        assertTrue(html.contains("YtmSettingsBridge"));
        assertTrue(html.contains("connect-src 'none'"));
        assertTrue(html.contains("font-src 'none'"));
        assertFalse(html.contains("preview-mode"));
        assertFalse(html.contains("settingsCommands"));
    }
}
