package dev.ytmlyrics.ui;

/** A content slot has exactly one mounted surface; native content is retained, not covered. */
final class ExclusiveContent<T> {
    interface Mount<T> { void add(T view); void remove(T view); }
    private final Mount<T> mount;
    private T current;
    ExclusiveContent(Mount<T> mount) { this.mount = mount; }
    void show(T view) {
        if (current == view) return;
        T old = current;
        current = null;
        if (old != null) mount.remove(old);
        // Android attachment callbacks can synchronously request this same surface again.
        current = view;
        if (view != null) mount.add(view);
    }
}
