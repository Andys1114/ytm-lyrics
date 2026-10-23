package dev.ytmlyrics.ui;

import org.json.JSONObject;

/** Small in-memory image, never written to lyric bindings or sent with playback ticks. */
public record ArtworkFrame(long revision,String image,int color) {
    public static final int DEFAULT_COLOR=0xff24242b;
    public static ArtworkFrame empty(long revision) { return new ArtworkFrame(revision,"",DEFAULT_COLOR); }
    public String json() {
        return "{\"revision\":"+revision+",\"image\":"+JSONObject.quote(image)
                +",\"color\":"+JSONObject.quote(String.format(java.util.Locale.ROOT,"#%06x",color&0xffffff))+"}";
    }
}
