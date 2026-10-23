package dev.ytmlyrics.hook;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.view.View;
import io.github.libxposed.api.XposedModule;
import java.util.function.Predicate;

/** Use the host's active text color for the lyric label/icon, never for its pill background. */
@android.annotation.SuppressLint("ResourceType") // ytTextPrimary in the pinned host APK.
final class LyricsActionAppearance {
    static void install(XposedModule api,ClassLoader loader,Predicate<View> matches) throws ReflectiveOperationException {
        var scope=new ActionDrawScope();
        Class<?> host=Class.forName("com.facebook.litho.ComponentHost",false,loader);
        api.hook(host.getDeclaredMethod("dispatchDraw",Canvas.class)).intercept(chain->{
            View view=(View)chain.getThisObject();
            Integer color=null;
            if(matches.test(view)) {
                var values=view.getContext().obtainStyledAttributes(new int[]{0x7f0409ad});
                try{color=values.getColor(0,Color.WHITE);}finally{values.recycle();}
            }
            return scope.draw(color,chain::proceed);
        });
        // fsd is this APK's Litho TextDrawable (Layout + TextContent); its setColorFilter
        // is a no-op. Tint its isolated draw, not the pooled drawable or cached TextPaint.
        for(Class<?> leaf:new Class<?>[]{Class.forName("fsd",false,loader),VectorDrawable.class,BitmapDrawable.class}) {
            api.hook(leaf.getDeclaredMethod("draw",Canvas.class)).intercept(chain->{
                Integer color=scope.color();
                if(color==null)return chain.proceed();
                Drawable drawable=(Drawable)chain.getThisObject();Canvas canvas=(Canvas)chain.getArg(0);
                Paint paint=new Paint();paint.setColorFilter(new PorterDuffColorFilter(color,PorterDuff.Mode.SRC_IN));
                int save=canvas.saveLayer(new RectF(drawable.getBounds()),paint);
                try{return chain.proceed();}finally{canvas.restoreToCount(save);}
            });
        }
    }
}
