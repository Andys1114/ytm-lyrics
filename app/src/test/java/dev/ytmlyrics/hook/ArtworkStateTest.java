package dev.ytmlyrics.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArtworkStateTest {
    @Test public void repeatedMetadataDoesNotReencodeTheSameBitmap() {
        var state=new ArtworkState<Object>();Object image=new Object();
        var first=state.update("song-a",image);
        assertTrue(first.ownerChanged());assertTrue(state.current(first));
        assertNull(state.update("song-a",image));
        assertTrue(state.current(first));
    }
    @Test public void lateOldCoverCannotOverwriteAnotherSongEvenWhenTheBitmapIsShared() {
        var state=new ArtworkState<Object>();Object image=new Object();
        var old=state.update("song-a",image);var current=state.update("song-b",image);
        assertFalse(state.current(old));assertTrue(state.current(current));assertTrue(current.ownerChanged());
    }
    @Test public void lateArtworkAndRemovalWithinOneSongInvalidatePreviousRequests() {
        var state=new ArtworkState<Object>();
        var missing=state.update("song-a",null);assertNotNull(missing);
        var loaded=state.update("song-a",new Object());assertFalse(loaded.ownerChanged());
        var cleared=state.update("song-a",null);
        assertFalse(state.current(missing));assertFalse(state.current(loaded));assertTrue(state.current(cleared));
        assertNull(state.update("song-a",null));
    }
    @Test public void stoppingPlaybackInvalidatesPendingWorkAndAllowsTheSameSongAgain() {
        var state=new ArtworkState<Object>();Object image=new Object();
        var first=state.update("song-a",image);var stop=state.update(null,null);
        assertFalse(state.current(first));assertTrue(stop.ownerChanged());
        var next=state.update("song-a",image);assertTrue(next.ownerChanged());assertTrue(next.revision()>stop.revision());
    }
}
