package dev.ytmlyrics.source;

import android.content.SharedPreferences;
import dev.ytmlyrics.core.LyricsOffset;
import dev.ytmlyrics.core.Song;

/** Per-playback-song preference, independent of lyric providers, bindings and preload pools. */
public final class OffsetStore {
    private final SharedPreferences preferences;
    public OffsetStore(SharedPreferences preferences){this.preferences=preferences;}
    public int get(Song song){return song==null?0:LyricsOffset.clamp(preferences.getInt("offset:"+song.key(),0));}
    public void put(Song song,int value){
        if(song==null)return;
        var editor=preferences.edit();String key="offset:"+song.key();int bounded=LyricsOffset.clamp(value);
        if(bounded==0)editor.remove(key);else editor.putInt(key,bounded);
        editor.apply();
    }
}
