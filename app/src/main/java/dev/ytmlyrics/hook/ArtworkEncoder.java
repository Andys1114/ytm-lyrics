package dev.ytmlyrics.hook;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Base64;
import dev.ytmlyrics.ui.ArtworkFrame;
import java.io.ByteArrayOutputStream;

/** Downsamples the borrowed YTM bitmap on a worker. Never recycles or modifies the host's image. */
final class ArtworkEncoder {
    static ArtworkFrame encode(Bitmap source,long revision) {
        int width=source.getWidth(),height=source.getHeight();
        float ratio=Math.min(1f,256f/Math.max(width,height));
        Bitmap small=Bitmap.createBitmap(Math.max(1,Math.round(width*ratio)),Math.max(1,Math.round(height*ratio)),Bitmap.Config.ARGB_8888);
        Bitmap readable=null;
        try {
            if(source.getConfig()==Bitmap.Config.HARDWARE)readable=source.copy(Bitmap.Config.ARGB_8888,false);
            Canvas canvas=new Canvas(small);canvas.drawColor(Color.rgb(36,36,43));
            canvas.drawBitmap(readable==null?source:readable,null,new android.graphics.Rect(0,0,small.getWidth(),small.getHeight()),new Paint(Paint.FILTER_BITMAP_FLAG));
            long red=0,green=0,blue=0;int count=0;
            for(int y=0;y<small.getHeight();y+=4)for(int x=0;x<small.getWidth();x+=4) {
                int pixel=small.getPixel(x,y);red+=Color.red(pixel);green+=Color.green(pixel);blue+=Color.blue(pixel);count++;
            }
            int color=Color.rgb((int)(red/count*.3),(int)(green/count*.3),(int)(blue/count*.3));
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            if(!small.compress(Bitmap.CompressFormat.JPEG,82,bytes))throw new IllegalStateException("封面压缩失败");
            return new ArtworkFrame(revision,"data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP),color);
        } finally { small.recycle();if(readable!=null)readable.recycle(); }
    }
}
