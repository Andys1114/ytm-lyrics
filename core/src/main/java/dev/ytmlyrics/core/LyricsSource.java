package dev.ytmlyrics.core;

import java.util.List;

/** New sources implement this boundary; they do not depend on Android or the hook API. */
public interface LyricsSource {
    String id();
    String name();
    List<Candidate> search(Song song, String query) throws Exception;
    default boolean supportsEnhancement() { return false; }
    /** Fetch by the user's original stable ID, without searching or changing recordings. */
    default Candidate refresh(Candidate cached) throws Exception { return cached; }
}
