package dev.ytmlyrics.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.Lrc;
import dev.ytmlyrics.core.LyricsOffset;
import java.util.ArrayList;
import java.util.List;

/** Replaces the page content returned to the host; native content is mounted only as fallback. */
@android.annotation.SuppressLint("ViewConstructor") // Created only in code with its playback actions.
public final class LyricsPanel extends FrameLayout {
    public interface Actions {
        void settings(Context context);
        int offsetMs();
        void choose(Context context);
        void sources(Context context);
        void retry();
        void seek(long timeMs);
        void visible();
        default boolean translations() { return true; }
        default void translations(boolean value) {}
    }
    private static final int INK = Color.rgb(15, 15, 15);
    private final TextView status;
    private final ScrollView scroll;
    private final LinearLayout lyrics;
    private final Actions actions;
    private final AmlLyricsView renderer;
    private final LinearLayout menuRow;
    private long serial;
    private String message = "";
    private final List<TextView> rows = new ArrayList<>();
    private final FrameLayout custom;
    private final ExclusiveContent<View> content;
    private View nativeContent;
    private List<Lrc.Line> lines = List.of();
    private Candidate selected;
    private int active = -1;
    private long lastTouch;

    public LyricsPanel(Context context, Actions actions) { this(context,actions,null); }
    public LyricsPanel(Context context, Actions actions, String moduleApk) {
        super(context);
        this.actions = actions;
        setContentDescription("自定义歌词");
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        addView(column, new LayoutParams(-1, -1));
        menuRow = new LinearLayout(context);
        menuRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        menuRow.setPadding(dp(12), 0, dp(12), 0);
        Button menu = new Button(context);
        menu.setText("歌词设置"); menu.setTextSize(15); menu.setAllCaps(false);
        menu.setTextColor(Color.WHITE); menu.setBackgroundColor(Color.TRANSPARENT);
        menu.setContentDescription("歌词设置");
        menu.setOnClickListener(v -> actions.settings(context));
        menuRow.addView(menu,new LinearLayout.LayoutParams(-2,dp(48)));
        column.addView(menuRow,new LinearLayout.LayoutParams(-1,-2));
        status = text("播放歌曲后查询歌词", 14);
        status.setTextColor(Color.LTGRAY);
        status.setBackgroundColor(INK);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        column.addView(status, new LinearLayout.LayoutParams(-1, -2));
        scroll = new ScrollView(context) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN || event.getActionMasked() == MotionEvent.ACTION_MOVE)
                    lastTouch = SystemClock.uptimeMillis();
                return super.dispatchTouchEvent(event);
            }
        };
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(INK);
        scroll.setVisibility(GONE);
        lyrics = new LinearLayout(context);
        lyrics.setOrientation(LinearLayout.VERTICAL);
        lyrics.setPadding(dp(24), dp(24), dp(24), dp(80));
        scroll.addView(lyrics, new ScrollView.LayoutParams(-1, -2));
        FrameLayout body = new FrameLayout(context);
        content = new ExclusiveContent<>(new ExclusiveContent.Mount<View>() {
            @Override public void add(View view) { body.addView(view,new FrameLayout.LayoutParams(-1,-1)); }
            @Override public void remove(View view) { body.removeView(view); }
        });
        custom = new FrameLayout(context);
        custom.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        renderer = moduleApk == null ? null : new AmlLyricsView(context,moduleApk,new AmlLyricsView.Callbacks() {
            @Override public void seek(long time) { actions.seek(time); }
            @Override public void changed() { updateBody(); }
        });
        if(renderer!=null){renderer.setVisibility(GONE);custom.addView(renderer,new FrameLayout.LayoutParams(-1,-1));}
        column.addView(body,new LinearLayout.LayoutParams(-1,0,1));
    }
    public void display(Candidate candidate, String message) {
        this.message=message;
        if (selected != candidate) {
            selected = candidate;
            serial++;
            if(renderer!=null)renderer.data(candidate,serial,actions.translations());
            buildFallback();
        }
        updateBody();
        setBackgroundColor(INK);
    }
    /** Called once before the host receives this replacement view. */
    public void nativeContent(View view) { nativeContent=view; updateBody(); }
    private void buildFallback() {
        Candidate candidate=selected;
        active = -1;
        rows.clear();
        lyrics.removeAllViews();
        scroll.scrollTo(0, 0);
        lines = candidate == null ? List.of() : candidate.lines();
        if (candidate != null) {
            if (!lines.isEmpty()) {
                for (int i=0;i<lines.size();i++) {
                    Lrc.Line line=lines.get(i);
                    String translation = candidate.timeline().get(i).translation();
                    TextView row = text((line.text().isBlank() ? "♪" : line.text())
                            + (actions.translations() && !translation.isBlank() ? "\n"+translation : ""), 22);
                    row.setTextColor(Color.GRAY);
                    row.setPadding(0, dp(10), 0, dp(10));
                    row.setOnClickListener(v -> actions.seek(line.timeMs()));
                    row.setContentDescription(line.text() + "，点击跳转播放");
                    rows.add(row);
                    lyrics.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
            } else {
                TextView plain = text(candidate.plain(), 21);
                plain.setLineSpacing(dp(8), 1);
                plain.setTextIsSelectable(true);
                lyrics.addView(plain, new LinearLayout.LayoutParams(-1, -2));
            }
        }
    }
    public void headerMenuAttached(boolean attached) { menuRow.setVisibility(attached ? GONE:VISIBLE); }
    public void artwork(ArtworkFrame value) {
        if(renderer!=null)renderer.artwork(value);
        // The old-tab settings row uses the same edge color as the WebView.
        menuRow.setBackgroundColor(value.color());
    }
    public void restartRenderer() { if(renderer!=null)renderer.retry(); }
    public String rendererStatus() {
        return selected!=null && selected.hasSynced() && renderer!=null && !renderer.available() ? renderer.status():"";
    }
    public void translationChanged() {
        if(renderer!=null)renderer.translation(actions.translations());
        buildFallback();
    }
    private void updateBody() {
        boolean available=selected!=null;
        content.show(available ? custom : nativeContent);
        boolean web=available && selected.hasSynced() && renderer!=null && renderer.available();
        scroll.setVisibility(available && !web ? VISIBLE:GONE);
        if(renderer!=null){renderer.setVisibility(web?VISIBLE:GONE);renderer.active(available && selected.hasSynced() && isShown() && getWindowVisibility()==VISIBLE);}
        String notice=available ? rendererStatus() : message;
        status.setText(notice);
        status.setVisibility(notice.isBlank() ? GONE:VISIBLE);
    }
    public void position(long positionMs, boolean playing, float speed, long epoch) {
        if(renderer!=null && selected!=null && selected.hasSynced()) {
            renderer.sync(positionMs,playing,speed,epoch,actions.offsetMs());
            if(renderer.available())return;
        }
        position(positionMs);
    }
    public void position(long positionMs) {
        int next = Lrc.activeLine(lines, LyricsOffset.at(positionMs,actions.offsetMs()));
        if (next == active) return;
        if (active >= 0 && active < rows.size()) style(rows.get(active), false);
        active = next;
        if (next < 0 || next >= rows.size()) return;
        TextView row = rows.get(next);
        style(row, true);
        if (SystemClock.uptimeMillis() - lastTouch > 3000)
            scroll.smoothScrollTo(0, Math.max(0, row.getTop() - scroll.getHeight() / 3));
    }
    private void style(TextView row, boolean highlighted) {
        row.setTextColor(highlighted ? Color.WHITE : Color.GRAY);
        row.setTypeface(null, highlighted ? Typeface.BOLD : Typeface.NORMAL);
    }
    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow(); updateBody(); post(actions::visible);
    }
    @Override protected void onDetachedFromWindow() {
        if(renderer!=null)renderer.active(false);
        super.onDetachedFromWindow();
    }
    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if(renderer!=null)renderer.active(visibility==VISIBLE && isShown() && selected!=null && selected.hasSynced());
        if (actions != null && visibility == VISIBLE) post(actions::visible);
    }
    @Override protected void onVisibilityChanged(View changedView,int visibility) {
        super.onVisibilityChanged(changedView,visibility);
        if(renderer!=null)renderer.active(isShown() && getWindowVisibility()==VISIBLE && selected!=null && selected.hasSynced());
        if(actions!=null && isShown())post(actions::visible);
    }
    private TextView text(String value, float size) {
        TextView view = new TextView(getContext());
        view.setText(value); view.setTextSize(size); view.setTextColor(Color.WHITE);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
