package dev.ytmlyrics.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsCommandTest {
    @Test public void supportedActionsKeepExplicitTypesAndStableSourceIds() {
        assertEquals("choose", SettingsCommand.parse("{\"action\":\"choose\"}").action());
        assertTrue(SettingsCommand.parse("{\"action\":\"translation\",\"value\":true}").enabled());
        var source = SettingsCommand.parse("{\"action\":\"source\",\"sourceId\":\"netease\",\"value\":false}");
        assertEquals("netease", source.sourceId()); assertFalse(source.enabled());
        assertEquals("qqmusic", SettingsCommand.parse("{\"action\":\"moveUp\",\"sourceId\":\"qqmusic\"}").sourceId());
        assertEquals(-100, SettingsCommand.parse("{\"action\":\"step\",\"value\":-100}").offsetMs());
        assertEquals(10_000, SettingsCommand.parse("{\"action\":\"offset\",\"value\":99900}").offsetMs());
    }
    @Test public void halfSecondMessagesCoexistWithEarlierFineOffsetCommands(){
        for(int value:new int[]{-500,500,-100,100})
            assertEquals(value,SettingsCommand.parse("{\"action\":\"step\",\"value\":"+value+"}").offsetMs());
        assertEquals(100,SettingsCommand.parse("{\"action\":\"offset\",\"value\":100}").offsetMs());
        for(int value:new int[]{0,200,1000})
            assertThrows(IllegalArgumentException.class,()->SettingsCommand.parse("{\"action\":\"step\",\"value\":"+value+"}"));
    }
    @Test public void invalidMessagesCannotBecomeNativeOperations() {
        for (String encoded : new String[]{"null", "{", "{}", "{\"action\":\"openUrl\"}",
                "{\"action\":\"translation\",\"value\":\"true\"}",
                "{\"action\":\"offset\",\"value\":0.1}", "{\"action\":\"offset\",\"value\":101}",
                "{\"action\":\"offset\",\"value\":\"100\"}", "{\"action\":\"step\",\"value\":200}",
                "{\"action\":\"source\",\"sourceId\":\"../bad\",\"value\":true}"})
            assertThrows(encoded, IllegalArgumentException.class, () -> SettingsCommand.parse(encoded));
        assertThrows(IllegalArgumentException.class, () -> SettingsCommand.parse(null));
        assertThrows(IllegalArgumentException.class, () -> SettingsCommand.parse(" ".repeat(4097)));
    }
    @Test public void selectionUsesSourceAndSongIdNotListPosition() {
        var command=SettingsCommand.parse("{\"action\":\"select\",\"sourceId\":\"qqmusic\",\"value\":\"same-id\",\"songKey\":\"current\"}");
        assertEquals("qqmusic",command.sourceId());assertEquals("same-id",command.text());
        assertEquals("current",command.songKey());
        assertEquals("ユリイカ ロクデナシ",SettingsCommand.parse("{\"action\":\"search\",\"value\":\"  ユリイカ ロクデナシ  \"}").text());
        for(String invalid:new String[]{"{\"action\":\"select\",\"sourceId\":\"qqmusic\",\"value\":1}",
                "{\"action\":\"search\",\"value\":true}","{\"action\":\"search\",\"value\":\"  \"}"})
            assertThrows(IllegalArgumentException.class,()->SettingsCommand.parse(invalid));
    }
    @Test public void delayedSongCommandsCannotEditAnotherSong() throws Exception {
        var song=new dev.ytmlyrics.core.Song("id","Song","Artist","Album",180000);
        for(String action:new String[]{"offset","step","search","select","clearBinding","confirmClear"}) {
            var json=new org.json.JSONObject().put("action",action).put("songKey",song.key())
                    .put("sourceId","netease").put("value",action.equals("offset")||action.equals("step")?100:"query-or-id");
            var command=SettingsCommand.parse(json.toString());assertTrue(command.appliesTo(song));
            assertFalse(command.appliesTo(null));
            assertFalse(command.appliesTo(new dev.ytmlyrics.core.Song("other","Other song","Artist","Album",180000)));
        }
        assertFalse(SettingsCommand.parse("{\"action\":\"offset\",\"value\":100}").appliesTo(song));
        assertTrue(SettingsCommand.parse("{\"action\":\"settings\"}").appliesTo(null));
    }
}
