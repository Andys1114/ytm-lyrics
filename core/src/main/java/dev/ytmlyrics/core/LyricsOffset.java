package dev.ytmlyrics.core;

/** Positive values advance lyrics; playback and source timestamps remain unchanged. */
public final class LyricsOffset {
    public static final int LIMIT_MS=10_000;
    public static final int STEP_MS=100;
    private LyricsOffset() {}
    public static int clamp(int value){return Math.max(-LIMIT_MS,Math.min(LIMIT_MS,value));}
    public static long at(long mediaMs,int advanceMs){return mediaMs+clamp(advanceMs);}
    public static long seek(long lyricMs,int advanceMs,long durationMs){
        long media=Math.max(0,lyricMs-clamp(advanceMs));
        return durationMs>0 ? Math.min(durationMs,media):media;
    }
    public static String label(int advanceMs){
        int value=clamp(advanceMs);
        return value==0 ? "不偏移":String.format(java.util.Locale.ROOT,"%s %.1f 秒",value>0?"提前":"延后",Math.abs(value)/1000.0);
    }
}
