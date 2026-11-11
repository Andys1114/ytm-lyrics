package dev.ytmlyrics.core;

/** Positive values advance lyrics; playback and source timestamps remain unchanged. */
public final class LyricsOffset {
    public static final int LIMIT_MS=10_000;
    public static final int STEP_MS=500;
    private LyricsOffset() {}
    public static int clamp(int value){return Math.max(-LIMIT_MS,Math.min(LIMIT_MS,value));}
    /** UI grid only: existing stored offsets retain their exact playback meaning. */
    public static int sliderProgress(int value){return Math.round((clamp(value)+LIMIT_MS)/(float)STEP_MS);}
    public static int sliderValue(int progress){return (Math.max(0,Math.min(2*LIMIT_MS/STEP_MS,progress))*STEP_MS)-LIMIT_MS;}
    /** Move to the next half-second in the requested direction, including old off-grid values. */
    public static int nudge(int value,int direction){
        int bounded=clamp(value);
        if(direction==0)return bounded;
        int next=direction>0 ? (Math.floorDiv(bounded,STEP_MS)+1)*STEP_MS
                : ((int)Math.ceil(bounded/(double)STEP_MS)-1)*STEP_MS;
        return clamp(next);
    }
    /** Rotate by a signed number of half-second detents without rounding a resting legacy value. */
    public static int stepBy(int value,int detents){
        if(detents==0)return clamp(value);
        int direction=Integer.signum(detents);
        long target=nudge(value,direction)+((long)detents-direction)*STEP_MS;
        return (int)Math.max(-LIMIT_MS,Math.min(LIMIT_MS,target));
    }
    public static long at(long mediaMs,int advanceMs){return mediaMs+clamp(advanceMs);}
    public static long seek(long lyricMs,int advanceMs,long durationMs){
        long media=Math.max(0,lyricMs-clamp(advanceMs));
        return durationMs>0 ? Math.min(durationMs,media):media;
    }
    public static String label(int advanceMs){
        int value=clamp(advanceMs);
        return value==0 ? "不偏移":String.format(java.util.Locale.ROOT,"%s %.1f 秒",value>0?"提前":"延后",Math.abs(value)/1000.0);
    }
    public static String compactLabel(int advanceMs){
        int value=clamp(advanceMs);
        if(value==0)return "0s";
        String seconds=value%1000==0 ? Integer.toString(Math.abs(value)/1000)
                : String.format(java.util.Locale.ROOT,"%.1f",Math.abs(value)/1000.0);
        return (value>0?"+":"−")+seconds+"s";
    }
}
