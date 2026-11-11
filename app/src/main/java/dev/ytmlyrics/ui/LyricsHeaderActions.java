package dev.ytmlyrics.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/** Mounts a toolbar in the host's existing weighted center column, not its title constraints. */
@android.annotation.SuppressLint("ResourceType") // Fixed YTM 9.41.52 IDs.
public final class LyricsHeaderActions {
    private static final int TITLE_ROW = 0x7f0b05d0;
    private final WeakHashMap<View, Mount> mounts = new WeakHashMap<>();

    private record Toolbar(WeakReference<FrameLayout> frame, WeakReference<View> center,
                           LinearLayout.LayoutParams hostParams, int hostVisibility,
                           WeakReference<LyricsQuickActions> controls) {
        void restore() {
            var wrapper = frame.get();
            var original = center.get();
            if (wrapper == null || original == null || original.getParent() != wrapper
                    || !(wrapper.getParent() instanceof LinearLayout row)) return;
            int index = row.indexOfChild(wrapper);
            wrapper.removeView(original);
            original.setVisibility(hostVisibility);
            row.removeViewAt(index);
            row.addView(original, index, hostParams);
        }
    }

    private static final class Mount implements View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<View> root;
        final Toolbar toolbar;
        final Runnable changed;
        ViewTreeObserver observer;
        boolean shown, usable;

        Mount(View view, Toolbar toolbar, Runnable changed) {
            root = new WeakReference<>(view);
            this.toolbar = toolbar;
            this.changed = changed;
        }
        @Override public void onViewAttachedToWindow(View view) {
            observer = view.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(this);
            update();
        }
        @Override public void onViewDetachedFromWindow(View view) {
            stopObserving();
            update();
        }
        void stopObserving() {
            if (observer != null && observer.isAlive()) observer.removeOnGlobalLayoutListener(this);
            observer = null;
        }
        @Override public void onGlobalLayout() { update(); }
        void update() {
            View view = root.get();
            boolean visible = observer != null && view != null && view.isShown() && view.getWindowVisibility() == View.VISIBLE;
            var controls = toolbar == null ? null : toolbar.controls().get();
            boolean ready = visible && controls != null && controls.hasRoom();
            if (controls != null) controls.setVisibility(ready ? View.VISIBLE : View.INVISIBLE);
            if (visible != shown || ready != usable) {
                shown = visible;
                usable = ready;
                changed.run();
            }
        }
    }

    public boolean attach(View root, TextView title, CharSequence fallbackTitle, LyricsPanel.Actions actions, Runnable changed) {
        Mount existing = mounts.get(root);
        if (existing != null) return existing.toolbar != null;
        Toolbar toolbar = title == null ? null : insert(root, title, fallbackTitle, actions);
        var mount = new Mount(root, toolbar, changed);
        mounts.put(root, mount);
        root.addOnAttachStateChangeListener(mount);
        if (root.isAttachedToWindow()) mount.onViewAttachedToWindow(root);
        return toolbar != null;
    }

    private static Toolbar insert(View root, TextView title, CharSequence fallbackTitle, LyricsPanel.Actions actions) {
        View center = title;
        while (center.getParent() instanceof View parent && parent.getId() != TITLE_ROW && parent != root) center = parent;
        if (!(center.getParent() instanceof LinearLayout row) || row.getId() != TITLE_ROW
                || row.getOrientation() != LinearLayout.HORIZONTAL || root.findViewById(TITLE_ROW) != row
                || !(center.getLayoutParams() instanceof LinearLayout.LayoutParams hostParams)
                || hostParams.width != 0 || hostParams.weight <= 0) return null;

        var context = row.getContext();
        float density = row.getResources().getDisplayMetrics().density;
        int height = Math.round(48 * density);
        var frame = new FrameLayout(context);
        var tools = new LinearLayout(context);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setGravity(Gravity.CENTER_VERTICAL);
        tools.setPaddingRelative(Math.round(16 * density), Math.round(4 * density), 0, Math.round(4 * density));
        var caption = new TextView(context);
        caption.setText(TextUtils.isEmpty(title.getText()) ? fallbackTitle : title.getText());
        caption.setTextColor(Color.WHITE);
        caption.setTextSize(20);
        caption.setTypeface(null, Typeface.BOLD);
        caption.setSingleLine();
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setMaxWidth(Math.round(96 * density));
        caption.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        var captionParams = new LinearLayout.LayoutParams(-2, height);
        captionParams.setMarginEnd(Math.round(8 * density));
        tools.addView(caption, captionParams);
        var controls = new LyricsQuickActions(context, actions);
        controls.setVisibility(View.INVISIBLE);
        tools.addView(controls, new LinearLayout.LayoutParams(0, height, 1));

        int index = row.indexOfChild(center);
        int visibility = center.getVisibility();
        row.removeViewAt(index);
        center.setVisibility(View.GONE);
        frame.addView(center, new FrameLayout.LayoutParams(-1, -2));
        frame.addView(tools, new FrameLayout.LayoutParams(-1, -2));
        // F/G still has exactly the same number of direct children at the same indices.
        row.addView(frame, index, new LinearLayout.LayoutParams(hostParams));
        return new Toolbar(new WeakReference<>(frame), new WeakReference<>(center), hostParams,
                visibility, new WeakReference<>(controls));
    }

    public void detach(View root) {
        Mount mount = mounts.remove(root);
        if (mount == null) return;
        root.removeOnAttachStateChangeListener(mount);
        mount.stopObserving();
        if (mount.toolbar != null) mount.toolbar.restore();
        mount.changed.run();
    }

    public List<View> visibleHeaders() {
        var result = new ArrayList<View>();
        mounts.forEach((view, mount) -> { if (mount.shown) result.add(view); });
        return result;
    }

    public void syncState(View anchor, String songKey, int offsetMs) {
        for (var mount : new ArrayList<>(mounts.values())) {
            var controls = mount.toolbar == null ? null : mount.toolbar.controls().get();
            if (controls != null) controls.syncState(anchor, songKey, offsetMs);
        }
    }

    public boolean hasUsableHeader(Predicate<View> owner) {
        for (var entry : mounts.entrySet()) if (entry.getValue().usable && owner.test(entry.getKey())) return true;
        return false;
    }

    public View syncAnchor(Predicate<View> owner) {
        for (var entry : mounts.entrySet()) {
            var mount = entry.getValue();
            if (!mount.usable || !owner.test(entry.getKey())) continue;
            var controls = mount.toolbar.controls().get();
            if (controls != null && controls.syncAnchor() != null) return controls.syncAnchor();
        }
        return null;
    }
}
