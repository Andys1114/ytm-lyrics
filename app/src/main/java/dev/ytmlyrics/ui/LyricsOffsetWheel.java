package dev.ytmlyrics.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import dev.ytmlyrics.core.LyricsOffset;
import java.util.function.IntConsumer;
import java.util.function.Consumer;

import static dev.ytmlyrics.ui.LyricsQuickActions.ACTION_HEIGHT;
import static dev.ytmlyrics.ui.LyricsQuickActions.PILL_INSET_X;
import static dev.ytmlyrics.ui.LyricsQuickActions.PILL_INSET_Y;
import static dev.ytmlyrics.ui.LyricsQuickActions.PILL_RADIUS;

/** A compact chip or a vertical half-second wheel, with the same centered readout. */
@android.annotation.SuppressLint("ViewConstructor")
final class LyricsOffsetWheel extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Typeface medium=Typeface.create("sans-serif-medium",Typeface.NORMAL);
    private final Consumer<View> toggle,reveal;
    private final boolean roller;
    private final IntConsumer commit;
    private final int slop;
    private String key="",label="偏移";
    private int value,origin,pointer=-1;
    private boolean expanded,startedOpen,dragging;
    private float downY,fraction,revealProgress=1;
    private ValueAnimator settling;

    LyricsOffsetWheel(Context context,boolean roller,Consumer<View> toggle,Consumer<View> reveal,IntConsumer commit) {
        super(context);
        this.roller=roller;this.toggle=toggle;this.reveal=reveal;this.commit=commit;
        slop=ViewConfiguration.get(context).getScaledTouchSlop();
        setClickable(true);setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        describe();
    }
    void state(boolean open,String songKey,int offset) {
        boolean sameSong=key.equals(songKey);
        if(!sameSong || (expanded && !open)){finish();stopSettle();}
        if(expanded==open && key.equals(songKey) && value==offset)return;
        if(roller && sameSong && expanded && open && pointer==-1 && value!=offset)
            settleFrom(value<offset?-.23f:.23f);
        expanded=open;key=songKey;value=offset;label=value==0?"偏移":LyricsOffset.compactLabel(value);
        describe();invalidate();
    }
    void revealProgress(float progress){revealProgress=progress;invalidate();}
    private void describe(){
        setContentDescription(roller?"本曲歌词偏移滚轮":expanded?"收起歌词偏移":"展开歌词偏移");
        setStateDescription(LyricsOffset.label(value));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width=getWidth(),center=width/2,y=getHeight()/2f;
        float pillHalf=pillHalfHeight(),inset=Math.round(dp(PILL_INSET_X)),radius=dp(PILL_RADIUS);
        paint.setTextAlign(Paint.Align.CENTER);
        if(roller && revealProgress>0){
            float half=pillHalf+(y-dp(4)-pillHalf)*revealProgress;
            paint.setColor(0xff171b23);
            canvas.drawRoundRect(0,y-half,width,y+half,dp(16),dp(16),paint);
            paint.setTypeface(Typeface.DEFAULT);paint.setTextSize(sp(12));
            float baseline=-(paint.ascent()+paint.descent())/2;
            for(int i=-2;i<=2;i++){
                if(i==0)continue;
                int next=LyricsOffset.stepBy(value,i);
                if(next==value || (Math.abs(i)==2 && next==LyricsOffset.stepBy(value,Integer.signum(i))))continue;
                float line=y+(i-fraction)*slotHeight()*(.75f+.25f*revealProgress);
                float fade=Math.max(0,Math.min(1,(Math.abs(line-y)-pillHalf)/dp(8)));
                fade=Math.min(fade,Math.max(0,Math.min(1,(Math.min(line,getHeight()-line)-dp(4))/dp(12))));
                paint.setColor(0xffaab6d4);paint.setAlpha(Math.round(fade*revealProgress*255));
                canvas.drawText(detentLabel(next),center,line+baseline,paint);
            }
        }
        paint.setColor(isPressed()?0xff435172:0xff35415e);
        canvas.drawRoundRect(inset,y-pillHalf,width-inset,y+pillHalf,radius,radius,paint);
        paint.setTypeface(medium);
        drawLabel(canvas,center,y,sp(14));
        if(isFocused()){
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xffc1ccff);
            canvas.drawRoundRect(dp(1),dp(1),width-dp(1),getHeight()-dp(1),dp(12),dp(12),paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
    private void drawLabel(Canvas canvas,float center,float y,float size){
        paint.setColor(0xffeff2ff);paint.setTextSize(size);
        float needed=paint.measureText(label),available=getWidth()-dp(12);
        if(needed>available && available>0)paint.setTextSize(size*available/needed);
        canvas.drawText(label,center,y-(paint.ascent()+paint.descent())/2,paint);
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    private float pillHalfHeight(){return Math.round(dp(ACTION_HEIGHT))/2f-Math.round(dp(PILL_INSET_Y));}
    private float sp(int value){return android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,value,getResources().getDisplayMetrics());}
    private boolean step(int direction){return change(LyricsOffset.stepBy(value,direction));}
    private boolean change(int target){
        if(key.isBlank() || target==value)return false;
        commit.accept(target);return true;
    }
    private static String detentLabel(int ms) {
        int steps=Math.abs(ms)/LyricsOffset.STEP_MS;
        return (ms<0?"−":ms>0?"+":"")+(steps/2)+(steps%2==0?"":".5");
    }
    private float slotHeight(){return dp(80)/3f;}
    private void stopSettle(){
        if(settling!=null){settling.cancel();settling=null;}
        fraction=0;invalidate();
    }
    private void settleFrom(float start){
        stopSettle();
        if(!roller || Math.abs(start)<.01f || !ValueAnimator.areAnimatorsEnabled())return;
        settling=ValueAnimator.ofFloat(start,0);
        settling.setDuration(135);
        settling.setInterpolator(new DecelerateInterpolator());
        settling.addUpdateListener(animation->{fraction=(float)animation.getAnimatedValue();invalidate();});
        settling.start();
    }
    private void finish(){
        if(pointer==-1)return;
        pointer=-1;fraction=0;setPressed(false);invalidate();
        if(getParent()!=null)getParent().requestDisallowInterceptTouchEvent(false);
    }
    @Override protected void onDetachedFromWindow(){finish();stopSettle();super.onDetachedFromWindow();}
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                if(key.isBlank())return super.onTouchEvent(event);
                stopSettle();
                pointer=event.getPointerId(0);downY=event.getRawY();
                startedOpen=expanded;dragging=false;origin=value;
                setPressed(true);getParent().requestDisallowInterceptTouchEvent(true);
                if(!roller)reveal.accept(this);
                return true;
            case MotionEvent.ACTION_MOVE:
                int index=event.findPointerIndex(pointer);
                if(index<0)return super.onTouchEvent(event);
                float dy=downY-event.getRawY(index);
                if(Math.abs(dy)>slop)dragging=true;
                if(dragging){
                    double lower=Math.floor((-LyricsOffset.LIMIT_MS-origin)/(double)LyricsOffset.STEP_MS);
                    double upper=Math.ceil((LyricsOffset.LIMIT_MS-origin)/(double)LyricsOffset.STEP_MS);
                    double travel=Math.max(lower,Math.min(upper,dy/slotHeight()));
                    int ticks=(int)Math.copySign(Math.floor(Math.abs(travel)+.5),travel);
                    fraction=(float)(travel-ticks);
                    if(change(LyricsOffset.stepBy(origin,ticks)))performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK);
                    if(roller)invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if(pointer==-1)return super.onTouchEvent(event);
                float remainder=fraction;
                finish();
                if(dragging)settleFrom(remainder);
                else {
                    float fromCenter=event.getY()-getHeight()/2f;
                    if(roller && Math.abs(fromCenter)>pillHalfHeight())step(fromCenter>0?1:-1);
                    else if(roller || startedOpen)performClick();
                    else super.performClick(); // Press already revealed the wheel.
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
                if(event.getPointerId(event.getActionIndex())==pointer)finish();
                return true;
            case MotionEvent.ACTION_CANCEL:
                if(pointer==-1)return super.onTouchEvent(event);
                finish();return true;
            default:return pointer!=-1 || super.onTouchEvent(event);
        }
    }
    @Override public boolean performClick(){super.performClick();toggle.accept(this);return true;}
    @Override public boolean onGenericMotionEvent(MotionEvent event){
        if(!key.isBlank() && event.getActionMasked()==MotionEvent.ACTION_SCROLL
                && (event.getMetaState()&KeyEvent.META_CTRL_ON)==0){
            float delta=-event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if(delta!=0){if(!roller)reveal.accept(this);step(delta>0?1:-1);return true;}
        }
        return super.onGenericMotionEvent(event);
    }
    @Override public boolean onKeyDown(int code,KeyEvent event){
        if(expanded || roller)switch(code){
            case KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_DOWN:step(-1);return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_UP:step(1);return true;
            case KeyEvent.KEYCODE_MOVE_HOME:change(-LyricsOffset.LIMIT_MS);return true;
            case KeyEvent.KEYCODE_MOVE_END:change(LyricsOffset.LIMIT_MS);return true;
            case KeyEvent.KEYCODE_ESCAPE:performClick();return true;
        }
        return super.onKeyDown(code,event);
    }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName(roller?"android.widget.SeekBar":"android.widget.Button");
        if(!roller)return;
        info.setRangeInfo(new AccessibilityNodeInfo.RangeInfo(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT,
                -LyricsOffset.LIMIT_MS/1000f,LyricsOffset.LIMIT_MS/1000f,value/1000f));
        if(value<LyricsOffset.LIMIT_MS)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        if(value>-LyricsOffset.LIMIT_MS)info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        info.addAction(new AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK,"收起偏移滚轮"));
        info.setScrollable(true);
    }
    @Override public boolean performAccessibilityAction(int action,Bundle args){
        if(roller){
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)return step(1);
            if(action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)return step(-1);
            if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && args!=null){
                float seconds=args.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE);
                return change(LyricsOffset.sliderValue(Math.round((seconds*1000+LyricsOffset.LIMIT_MS)/LyricsOffset.STEP_MS)));
            }
        }
        return super.performAccessibilityAction(action,args);
    }
}
