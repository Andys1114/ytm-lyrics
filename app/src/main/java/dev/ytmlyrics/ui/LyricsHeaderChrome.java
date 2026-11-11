package dev.ytmlyrics.ui;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;

/** The verified 9.41.52 shell is wider than vly.F: the close button is its sibling. */
@android.annotation.SuppressLint("ResourceType")
public final class LyricsHeaderChrome {
    private static final int HEADER_SLOT = 0x7f0b08b0, CLOSE = 0x7f0b026a;
    private static final int HEADER_WRAPPER = 0x7f0b08b2, HEADER_CONTAINER = 0x7f0b05a2, BORDER = 0x7f0b08b1;
    interface Tree<V> {
        V parent(V view);
        int id(V view);
        V find(V view, int id);
        boolean icon(V view);
    }
    record Shell<V>(V surface, V holder, V close, V border) {}
    private static final Tree<View> VIEWS = new Tree<>() {
        @Override public View parent(View view) { return view.getParent() instanceof View parent ? parent : null; }
        @Override public int id(View view) { return view.getId(); }
        @Override public View find(View view, int id) { return view.findViewById(id); }
        @Override public boolean icon(View view) { return view instanceof ImageView; }
    };
    private final HeaderSurface<View, Drawable> surface = new HeaderSurface<>(new HeaderSurface.Port<>() {
        @Override public Drawable background(View view) { return view.getBackground(); }
        @Override public void background(View view, Drawable value) { view.setBackground(value); }
        @Override public Drawable styled(View view, Drawable original, HeaderSurface.Style style, int color) {
            if (style == HeaderSurface.Style.TOUCH) return new RippleDrawable(
                    android.content.res.ColorStateList.valueOf(0x33ffffff), null,
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.WHITE));
            // The host casts its header background to GradientDrawable to adjust rounding.
            var state = original == null ? null : original.getConstantState();
            var copy = state == null ? null : state.newDrawable(view.getResources()).mutate();
            var gradient = copy instanceof GradientDrawable shape ? shape : new GradientDrawable();
            gradient.setColor(style == HeaderSurface.Style.SURFACE ? color : android.graphics.Color.TRANSPARENT);
            return gradient;
        }
    });

    public void paint(List<View> headers, int color) {
        var targets = new ArrayList<HeaderSurface.Target<View>>();
        for (View title : headers) {
            var shell = locate(title, VIEWS);
            if (shell == null) continue;
            targets.add(new HeaderSurface.Target<>(shell.surface(), HeaderSurface.Style.SURFACE));
            targets.add(new HeaderSurface.Target<>(shell.holder(), HeaderSurface.Style.CLEAR));
            targets.add(new HeaderSurface.Target<>(shell.close(), HeaderSurface.Style.TOUCH));
            if (shell.border() != null) targets.add(new HeaderSurface.Target<>(shell.border(), HeaderSurface.Style.CLEAR));
            collectButtons(title, targets);
        }
        surface.paint(targets, color);
    }

    public boolean restore(View view) { return surface.restore(view); }

    static <V> Shell<V> locate(V title, Tree<V> tree) {
        V slot = tree.parent(title);
        if (slot == null || tree.id(slot) != HEADER_SLOT) return null;
        V row = tree.parent(slot);
        if (row == null) return null;
        V close = tree.find(row, CLOSE);
        if (close == null || !tree.icon(close) || tree.parent(close) != row) return null;
        V holder = tree.parent(row);
        V wrapper = holder == null ? null : tree.parent(holder);
        if (wrapper == null || tree.id(wrapper) != HEADER_WRAPPER) return null;
        V column = tree.parent(wrapper);
        V surface = column == null ? null : tree.parent(column);
        if (surface == null || tree.id(surface) != HEADER_CONTAINER) return null;
        V border = tree.find(column, BORDER);
        return new Shell<>(surface, holder, close, border != null && tree.parent(border) == column ? border : null);
    }

    private static void collectButtons(View view, List<HeaderSurface.Target<View>> targets) {
        if (view instanceof ImageView && view.getBackground() != null)
            targets.add(new HeaderSurface.Target<>(view, HeaderSurface.Style.TOUCH));
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) collectButtons(group.getChildAt(i), targets);
    }
}
