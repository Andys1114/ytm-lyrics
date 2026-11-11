package dev.ytmlyrics.ui;

/** A user-opened wheel belongs only to the song and lyric page that opened it. */
public final class SyncExpansion {
    private String owner="";

    /** True when a shown wheel needs to close. */
    public boolean update(boolean showing,String songKey) {
        if(owner.isEmpty() || (showing && owner.equals(songKey)))return false;
        collapse();return true;
    }

    public boolean toggle(String songKey) {
        if(!hasSong(songKey) || expandedFor(songKey)){collapse();return false;}
        return reveal(songKey);
    }
    public boolean reveal(String songKey) {
        if(!hasSong(songKey) || expandedFor(songKey))return false;
        owner=songKey;return true;
    }
    public boolean expandedFor(String songKey) {return !owner.isEmpty() && owner.equals(songKey);}
    public void collapse() {owner="";}
    private static boolean hasSong(String key){return key!=null && !key.isBlank();}
}
