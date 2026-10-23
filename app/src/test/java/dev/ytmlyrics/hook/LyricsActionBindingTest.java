package dev.ytmlyrics.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class LyricsActionBindingTest {
    static class Node {boolean lyric,enabled,clickable;float alpha=.3f;}
    static class Harness implements LyricsActionBinding.Port<Node> {
        final LyricsActionBinding<Node> binding=new LyricsActionBinding<>(this);
        @Override public boolean matches(Node n){return n.lyric;}
        @Override public boolean enabled(Node n){return n.enabled;}
        @Override public boolean clickable(Node n){return n.clickable;}
        @Override public float alpha(Node n){return n.alpha;}
        @Override public void apply(Node n,boolean enabled,boolean clickable,float alpha){
            n.enabled=(Boolean)binding.requested(n,"setEnabled",enabled);
            n.clickable=(Boolean)binding.requested(n,"setClickable",clickable);
            n.alpha=(Float)binding.requested(n,"setAlpha",alpha);
        }
        void label(Node n,boolean lyric){binding.beforeLabel(n);n.lyric=lyric;binding.afterLabel(n);}
    }
    @Test public void matchesOnlyNativeLyricActionAndSupportedPanelIds(){
        String host="com.facebook.litho.ComponentHost";
        assertTrue(LyricsAction.matches(host,"歌词","Lyrics"));assertTrue(LyricsAction.matches(host,"lyrics","Lyrics"));
        assertFalse(LyricsAction.matches("android.widget.TextView","歌词","Lyrics"));
        assertFalse(LyricsAction.matches(host,"评论","Lyrics"));assertFalse(LyricsAction.matches(host,"", ""));
        assertTrue(LyricsAction.panel("music_offline_lyrics_panel"));assertFalse(LyricsAction.panel("comments"));
    }
    @Test public void activatesAtLabelBindingAndKeepsNativeRebindRequestsForRestoration(){
        var h=new Harness();var n=new Node();h.label(n,true);
        assertTrue(n.enabled);assertTrue(n.clickable);assertEquals(1,n.alpha,0);
        h.apply(n,false,false,.2f);assertTrue(n.enabled);assertTrue(n.clickable);assertEquals(1,n.alpha,0);
        h.label(n,true);assertEquals(1,n.alpha,0); // Rebinding the same lyric control remains active.
        h.label(n,false);assertFalse(n.enabled);assertFalse(n.clickable);assertEquals(.2f,n.alpha,0);
    }
    @Test public void unrelatedAndRecycledActionsKeepTheirNativeProperties(){
        var h=new Harness();var n=new Node();h.apply(n,false,false,.4f);assertFalse(n.enabled);assertEquals(.4f,n.alpha,0);
        h.label(n,true);assertTrue(n.enabled);
        h.apply(n,true,false,.6f);h.label(n,false);
        assertTrue(n.enabled);assertFalse(n.clickable);assertEquals(.6f,n.alpha,0);
        h.apply(n,false,true,.5f);assertFalse(n.enabled);assertTrue(n.clickable);assertEquals(.5f,n.alpha,0);
    }
}
