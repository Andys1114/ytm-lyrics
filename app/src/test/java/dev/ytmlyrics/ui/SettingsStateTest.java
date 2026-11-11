package dev.ytmlyrics.ui;

import dev.ytmlyrics.core.Song;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SettingsStateTest {
    @Test public void snapshotPreservesSourceIdsOrderAndSongText() throws Exception {
        var sources = new ArrayList<>(List.of(new SettingsState.Source("qqmusic", "QQ 音乐", true),
                new SettingsState.Source("netease", "网易云音乐", false)));
        String title = "歌名\"\n</script> & 自写测试";
        var state = new SettingsState(new Song("id", title, "歌手", "专辑", 180_000), "有候选", "QQ 音乐", "逐字同步",
                "", true, false, "预载已关闭", -500, sources);
        sources.clear();
        JSONObject value = new JSONObject(state.json());
        assertEquals(title, value.getString("title"));
        assertTrue(value.getBoolean("hasSong"));
        assertEquals(-500, value.getInt("offsetMs"));
        assertEquals("qqmusic", value.getJSONArray("sources").getJSONObject(0).getString("id"));
        assertFalse(value.getJSONArray("sources").getJSONObject(1).getBoolean("enabled"));
        assertTrue(value.getBoolean("translation"));
        assertFalse(value.getBoolean("preload"));
    }
    @Test public void noSongIsExplicitAndOffsetsAreClamped() throws Exception {
        JSONObject value = new JSONObject(new SettingsState(null, "等待", "", "", "", true, true,
                "等待队列", 12_000, List.of()).json());
        assertFalse(value.getBoolean("hasSong"));
        assertEquals(10_000, value.getInt("offsetMs"));
        assertFalse(value.getString("title").isBlank());
    }
    @Test public void candidateSnapshotIncludesExactIdentityAndBindingState() throws Exception {
        var song=new Song("id","Current","Artist","Album",180000);
        var choices=new ArrayList<>(List.of(new SettingsState.Choice("netease","123","歌名 </script>","歌手","专辑",181000,"网易云音乐",true,true,true)));
        var state=new SettingsState(song,"完成","网易云音乐","逐字","",true,true,"3/3",100,List.of(),"choose","关键词",false,true,choices);
        choices.clear();var json=new JSONObject(state.json());var row=json.getJSONArray("candidates").getJSONObject(0);
        assertEquals(song.key(),json.getString("songKey"));assertEquals("choose",json.getString("route"));assertTrue(json.getBoolean("bound"));
        assertEquals("123",row.getString("id"));assertEquals("netease",row.getString("sourceId"));
        assertEquals("歌名 </script>",row.getString("title"));assertTrue(row.getBoolean("selected"));
        assertTrue(row.getBoolean("words"));assertTrue(row.getBoolean("translation"));
    }
}
