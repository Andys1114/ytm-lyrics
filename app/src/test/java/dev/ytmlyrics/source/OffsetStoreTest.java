package dev.ytmlyrics.source;

import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.LyricsOffset;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class OffsetStoreTest {
    private static Song song(String id){return new Song(id,"Song","Artist","Album",180000);}
    @Test public void persistsPerPlaybackSongWithoutChangingBindingsOrSources(){
        var prefs=new MemoryPreferences();var store=new OffsetStore(prefs);
        prefs.edit().putString("binding:1","keep").putBoolean("enabled:qqmusic",false).apply();
        store.put(song("1"),500);
        assertEquals(500,new OffsetStore(prefs).get(song("1")));
        assertEquals(0,store.get(song("2")));assertEquals(0,store.get(null));
        assertEquals("keep",prefs.getString("binding:1",""));assertFalse(prefs.getBoolean("enabled:qqmusic",true));
        store.put(song("1"),0);assertFalse(prefs.contains("offset:1"));assertEquals(0,store.get(song("1")));
    }
    @Test public void missingMediaIdUsesOriginalSongIdentityAndBoundsStoredValues(){
        var prefs=new MemoryPreferences();var store=new OffsetStore(prefs);
        store.put(song(""),-500);
        assertEquals(-500,store.get(new Song("","Song","Artist","Different album",180000)));
        assertEquals(0,store.get(new Song("","Song","Artist","Album",157000)));
        store.put(song("2"),Integer.MAX_VALUE);assertEquals(10000,store.get(song("2")));
        prefs.edit().putInt("offset:2",-100000).apply();assertEquals(-10000,store.get(song("2")));
    }
    @Test public void bridgeKeepsRealMediaPositionAndAddsIndependentOffset() throws Exception {
        var legacy=new JSONObject(LyricsJson.playback(7,9500,false,1,4));
        assertEquals(0,legacy.getInt("offsetMs"));assertEquals(9500,legacy.getLong("positionMs"));
        var data=new JSONObject(LyricsJson.playback(7,9500,false,1,5,500));
        assertEquals(9500,data.getLong("positionMs"));assertEquals(500,data.getInt("offsetMs"));
        assertEquals(10000,LyricsOffset.at(data.getLong("positionMs"),data.getInt("offsetMs")));
        assertFalse(data.getBoolean("playing"));assertEquals(5,data.getLong("epoch"));
        assertEquals(-10000,new JSONObject(LyricsJson.playback(7,0,true,2,6,-20000)).getInt("offsetMs"));
    }
}
