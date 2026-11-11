package dev.ytmlyrics.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class SyncExpansionTest {
    @Test public void syncTogglesWithoutAnIndependentRouteOrStoredPreference() {
        var state=new SyncExpansion();assertFalse(state.update(true,"song-a"));
        assertFalse(state.expandedFor("song-a"));
        assertTrue(state.toggle("song-a"));assertTrue(state.expandedFor("song-a"));
        assertFalse(state.toggle("song-a"));assertFalse(state.expandedFor("song-a"));
        assertFalse(state.update(true,"song-a"));assertFalse(state.expandedFor("song-a"));
        assertTrue(state.toggle("song-a"));state.collapse();assertFalse(state.expandedFor("song-a"));
        assertFalse(state.update(true,"song-a"));assertFalse(state.expandedFor("song-a"));
    }
    @Test public void backgroundUpdatesKeepExpansionButChangingSongClosesIt() {
        var state=new SyncExpansion();assertTrue(state.reveal("song-a"));
        assertFalse(state.reveal("song-a"));
        assertFalse(state.update(true,"song-a"));assertTrue(state.expandedFor("song-a"));
        assertFalse(state.expandedFor("song-b"));assertTrue(state.update(true,"song-b"));
        assertFalse(state.expandedFor("song-a"));assertFalse(state.expandedFor("song-b"));
        state.toggle("song-b");assertTrue(state.expandedFor("song-b"));
        state.update(true,null);assertFalse(state.expandedFor("song-b"));
    }
    @Test public void aNewVisitShowsOnlyTheChipEvenIfTheWheelWasOpen() {
        var state=new SyncExpansion();
        assertFalse(state.update(false,"song-a"));
        assertFalse(state.update(true,"song-a"));assertTrue(state.reveal("song-a"));
        assertTrue(state.update(false,"song-a"));assertFalse(state.expandedFor("song-a"));
        assertFalse(state.update(true,"song-a"));assertFalse(state.expandedFor("song-a"));
    }
    @Test public void noSongCannotOpenControls() {
        var state=new SyncExpansion();assertFalse(state.toggle(null));assertFalse(state.toggle(""));
        assertFalse(state.update(true,""));assertFalse(state.update(true,null));
        assertFalse(state.expandedFor(""));
        assertFalse(state.reveal(null));assertFalse(state.update(true,"song-a"));
        assertFalse(state.expandedFor("song-a"));
    }
}
