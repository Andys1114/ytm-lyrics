package dev.ytmlyrics.ui;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkFrameTest {
    @Test public void payloadKeepsArtworkGenerationSeparateFromPlaybackSerial() throws Exception {
        var json=new JSONObject(new ArtworkFrame(7,"data:image/jpeg;base64,YQ==",0xff123456).json());
        assertEquals(7,json.getLong("revision"));assertEquals("#123456",json.getString("color"));
        assertEquals("data:image/jpeg;base64,YQ==",json.getString("image"));assertFalse(json.has("serial"));
    }
    @Test public void emptyFrameClearsOldArtWithoutAnyNetworkUrl() throws Exception {
        var json=new JSONObject(ArtworkFrame.empty(9).json());
        assertEquals("",json.getString("image"));assertEquals("#24242b",json.getString("color"));assertEquals(9,json.getLong("revision"));
    }
}
