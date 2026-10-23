package dev.ytmlyrics.hook;

import java.util.Objects;

/** Artwork has its own generation: lyric-source queries must not cancel a cover update. */
final class ArtworkState<T> {
    record Request<T>(long revision, T source, boolean ownerChanged) {}
    private String owner;
    private T source;
    private volatile long revision;

    Request<T> update(String nextOwner,T nextSource) {
        boolean changed=!Objects.equals(owner,nextOwner);
        if(!changed && source==nextSource)return null;
        owner=nextOwner;source=nextSource;
        return new Request<>(++revision,nextSource,changed);
    }
    boolean current(Request<T> request) { return request.revision()==revision; }
}
