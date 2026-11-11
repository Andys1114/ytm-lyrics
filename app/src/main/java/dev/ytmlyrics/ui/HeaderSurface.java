package dev.ytmlyrics.ui;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.WeakHashMap;

/** Owns only the backgrounds it installed, even while YTM restyles or swaps its two header holders. */
public final class HeaderSurface<V, B> {
    public enum Style { SURFACE, CLEAR, TOUCH }
    public record Target<V>(V view, Style style) {}
    public interface Port<V, B> {
        B background(V view);
        void background(V view, B value);
        B styled(V view, B original, Style style, int color);
    }
    private record Paint<B>(B original, B applied, Style style, int color) {}
    private final WeakHashMap<V, Paint<B>> painted = new WeakHashMap<>();
    private final Port<V, B> port;

    public HeaderSurface(Port<V, B> port) { this.port = port; }

    public void paint(List<Target<V>> targets, int color) {
        var desired = new IdentityHashMap<V, Style>();
        for (var target : targets) desired.put(target.view(), target.style());
        for (V view : List.copyOf(painted.keySet())) if (!desired.containsKey(view)) restore(view);
        desired.forEach((view, style) -> {
            B current = port.background(view);
            var previous = painted.get(view);
            boolean owns = previous != null && current == previous.applied();
            if (owns && previous.style() == style && (style != Style.SURFACE || previous.color() == color)) return;
            B original = owns ? previous.original() : current;
            B applied = port.styled(view, original, style, color);
            port.background(view, applied);
            painted.put(view, new Paint<>(original, applied, style, color));
        });
    }

    /** Give the host its actual drawable before it mutates the shape in place. */
    public boolean restore(V view) {
        var paint = painted.remove(view);
        if (paint == null) return false;
        if (port.background(view) == paint.applied()) port.background(view, paint.original());
        return true;
    }
}
