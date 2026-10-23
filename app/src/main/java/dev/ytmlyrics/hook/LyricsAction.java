package dev.ytmlyrics.hook;

/** Exact accessibility identity for the native player's lyric action, not arbitrary lyric text. */
final class LyricsAction {
    static boolean matches(String className,String label,String localizedTitle){
        return "com.facebook.litho.ComponentHost".equals(className)
                && ("歌词".equals(label) || (!label.isBlank() && label.equalsIgnoreCase(localizedTitle)));
    }
    static boolean panel(String id){return id!=null && id.toLowerCase(java.util.Locale.ROOT).contains("lyrics");}
}
