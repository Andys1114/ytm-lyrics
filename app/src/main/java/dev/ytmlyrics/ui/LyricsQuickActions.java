package dev.ytmlyrics.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.transition.Fade;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;

/** Shared title actions, with the offset chip pinned to the end of the available row. */
@android.annotation.SuppressLint("ViewConstructor")
public final class LyricsQuickActions extends LinearLayout {
    static final int ACTION_HEIGHT=48,PILL_INSET_X=2,PILL_INSET_Y=9,PILL_RADIUS=12;
    private static final int ACTION_WIDTH=54,WHEEL_HEIGHT=88,RESET_WIDTH=48,RESET_HEIGHT=40;
    private final LyricsPanel.Actions actions;
    private final LyricsOffsetWheel chip;
    private PopupWindow popup;
    private ValueAnimator opening;
    private LyricsOffsetWheel wheel;
    private Button reset;
    private String songKey="";

    public LyricsQuickActions(Context context,LyricsPanel.Actions actions) {
        super(context);this.actions=actions;setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);
        setLayoutDirection(LAYOUT_DIRECTION_LTR);
        var choose=button("选词","选择歌词",()->actions.choose(context));
        var settings=button("设置","歌词设置",()->actions.settings(context));
        chip=new LyricsOffsetWheel(context,false,actions::sync,actions::revealSync,
                value->actions.adjustOffset(songKey,value,false));
        addView(choose,new LayoutParams(dp(ACTION_WIDTH),dp(ACTION_HEIGHT)));
        addView(settings,new LayoutParams(dp(ACTION_WIDTH),dp(ACTION_HEIGHT)));
        addView(new View(context),new LayoutParams(0,0,1));
        addView(chip,new LayoutParams(dp(ACTION_WIDTH),dp(ACTION_HEIGHT)));
        chip.state(false,"",actions.offsetMs());
    }

    public View syncAnchor(){return chip.isAttachedToWindow() && chip.isShown()?chip:null;}
    boolean hasRoom(){return getHeight()>0 && getWidth()>=3*dp(ACTION_WIDTH);}
    public void syncState(View anchor,String key,int value) {
        songKey=key;
        boolean open=anchor==chip && syncAnchor()!=null;
        chip.state(open,key,value);
        if(open){
            if(popup==null)showWheel(key,value);
            else updateWheel(key,value);
        } else dismissWheel();
    }

    private void showWheel(String key,int value){
        Context context=getContext();
        var column=new LinearLayout(context);column.setOrientation(VERTICAL);column.setGravity(Gravity.CENTER_HORIZONTAL);
        reset=button("","归零本曲歌词偏移",()->actions.adjustOffset(songKey,0,false));
        reset.setCompoundDrawablesWithIntrinsicBounds(new ResetIcon(),null,null,null);
        reset.setPadding(dp(14),0,dp(14),0);
        wheel=new LyricsOffsetWheel(context,true,view->actions.collapseSync(),view->{},
                next->actions.adjustOffset(songKey,next,false));
        column.addView(wheel,new LayoutParams(dp(ACTION_WIDTH),dp(WHEEL_HEIGHT)));
        column.addView(reset,new LayoutParams(dp(RESET_WIDTH),dp(RESET_HEIGHT)));
        updateWheel(key,value);
        // Do not transfer focus away from a finger already dragging on the chip.
        // The popup shares the chip's width, leaving neighboring title actions tappable.
        var window=new PopupWindow(column,dp(ACTION_WIDTH),dp(WHEEL_HEIGHT)+dp(RESET_HEIGHT),false);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);window.setTouchModal(false);
        window.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        window.setAnimationStyle(0);
        // Keep the window and the center readout stationary. Only the wheel ends
        // and reset icon unfold, so there is no input or layout transition.
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        window.setEnterTransition(null);
        window.setExitTransition(animate?new Fade(Fade.OUT).setDuration(95):null);
        if(animate)applyReveal(0);
        window.setOnDismissListener(()->{if(popup==window)actions.collapseSync();});
        popup=window;
        try{
            window.showAsDropDown(chip,0,-(chip.getHeight()+dp(WHEEL_HEIGHT))/2,Gravity.START);
            if(animate){
                opening=ValueAnimator.ofFloat(0,1);
                opening.setDuration(180);
                opening.setInterpolator(new DecelerateInterpolator(1.6f));
                opening.addUpdateListener(animation->applyReveal((float)animation.getAnimatedValue()));
                opening.start();
            }
        } catch(WindowManager.BadTokenException error){
            actions.collapseSync();
        }
    }

    private void applyReveal(float progress){
        wheel.revealProgress(progress);
        reset.setAlpha(progress);reset.setTranslationY(-dp(6)*(1-progress));
    }
    private void updateWheel(String key,int value){
        wheel.state(true,key,value);
        reset.setEnabled(value!=0);
    }
    private void dismissWheel(){
        if(popup==null)return;
        if(opening!=null){opening.cancel();opening=null;}
        var old=popup;popup=null;old.dismiss();
    }
    @Override protected void onDetachedFromWindow(){
        if(popup!=null){dismissWheel();actions.collapseSync();}
        super.onDetachedFromWindow();
    }

    private Button button(String label,String description,Runnable action) {
        var button=new Button(getContext());
        button.setText(label);button.setTextSize(13);button.setSingleLine(true);button.setAllCaps(false);
        button.setContentDescription(description);button.setTooltipText(description);
        button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(0);button.setMinimumHeight(0);
        button.setPadding(0,0,0,0);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{0xff8390a9,0xffe8ecff}));
        var surface=new GradientDrawable();surface.setColor(0x403d4d65);surface.setCornerRadius(dp(PILL_RADIUS));
        int horizontal=dp(PILL_INSET_X),vertical=dp(PILL_INSET_Y);
        button.setBackground(new InsetDrawable(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),surface,null),
                horizontal,vertical,horizontal,vertical));
        button.setOnClickListener(view->action.run());return button;
    }
    private final class ResetIcon extends Drawable {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        // Google Material Icons "replay", Apache-2.0. Exact filled SVG geometry;
        // original asset and attribution: demo/vendor/material-icons/replay.svg and README.md.
        private final Path path=replayPath();
        private Path replayPath() {
            var p=new Path();p.moveTo(12,5);p.lineTo(12,1);p.lineTo(7,6);p.rLineTo(5,5);p.lineTo(12,7);
            p.rCubicTo(3.31f,0,6,2.69f,6,6);
            p.cubicTo(18,16.31f,15.31f,19,12,19);
            p.cubicTo(8.69f,19,6,16.31f,6,13);p.lineTo(4,13);
            p.rCubicTo(0,4.42f,3.58f,8,8,8);
            p.cubicTo(16.42f,21,20,17.42f,20,13);
            p.cubicTo(20,8.58f,16.42f,5,12,5);p.close();return p;
        }
        @Override public void draw(Canvas canvas) {
            int save=canvas.save();var bounds=getBounds();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);
            paint.setColor(enabled()?0xffc1ccff:0xff667089);paint.setStyle(Paint.Style.FILL);
            canvas.drawPath(path,paint);canvas.restoreToCount(save);
        }
        private boolean enabled(){for(int value:getState())if(value==android.R.attr.state_enabled)return true;return false;}
        @Override public boolean isStateful(){return true;}
        @Override protected boolean onStateChange(int[] state){invalidateSelf();return true;}
        @Override public int getIntrinsicWidth(){return dp(20);}
        @Override public int getIntrinsicHeight(){return dp(20);}
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
}
