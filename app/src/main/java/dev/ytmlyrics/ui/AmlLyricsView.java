package dev.ytmlyrics.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.source.LyricsJson;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipFile;

/** Local-only AMLL surface. The host's network, playback and navigation stay native. */
@SuppressLint("ViewConstructor")
final class AmlLyricsView extends FrameLayout {
    interface Callbacks { void seek(long positionMs); void changed(); }
    private static String cachedHtml;
    private final String moduleApk;
    private final Callbacks callbacks;
    private WebView web;
    private boolean active, translation = true;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final RendererSession session;
    private long serial;
    private long pageGeneration;
    private Candidate candidate;
    private String lastPlayback;
    private ArtworkFrame artwork=ArtworkFrame.empty(0);

    AmlLyricsView(Context context,String moduleApk,Callbacks callbacks) {
        super(context); this.moduleApk=moduleApk; this.callbacks=callbacks;
        session=new RendererSession(new RendererSession.Driver() {
            @Override public void create(){createWeb();}
            @Override public void destroy(){destroyWeb();}
            @Override public void schedule(Runnable task,long delay){main.postDelayed(task,delay);}
            @Override public void cancel(Runnable task){main.removeCallbacks(task);}
            @Override public void changed(){callbacks.changed();}
        });
        setBackgroundColor(ArtworkFrame.DEFAULT_COLOR);
    }
    boolean available() { return session.ready(); }
    String status() { return session.status(); }
    void data(Candidate value,long id,boolean showTranslation) {
        candidate=value; serial=id; translation=showTranslation; lastPlayback=null;
        if (session.ready()) sendData();
    }
    void translation(boolean value) {
        translation=value; call("setTranslation",Boolean.toString(value));
    }
    void artwork(ArtworkFrame value) {
        if(artwork==value)return;
        artwork=value;setBackgroundColor(value.color());call("setArtwork",value.json());
    }
    void sync(long position,boolean playing,float speed,long epoch,int advanceMs) {
        lastPlayback=LyricsJson.playback(serial,position,playing,speed,epoch,advanceMs);
        call("sync",lastPlayback);
    }
    void active(boolean value) {
        active=value;
        if (web != null) {
            if(value)web.onResume(); else web.onPause();
            call("setVisible",Boolean.toString(value));
        }
        session.active(value);
    }
    void retry() {
        session.retry();
    }
    private void sendData() {
        if(candidate!=null)call("setData",LyricsJson.payload(candidate,serial,translation));
        call("setArtwork",artwork.json());
        if(lastPlayback!=null)call("sync",lastPlayback);
        call("setVisible",Boolean.toString(active));
    }
    private void call(String method,String argument) {
        if(session.ready() && web!=null)web.evaluateJavascript("window.YtmLyricsHost."+method+"("+argument+");",null);
    }
    @SuppressLint({"SetJavaScriptEnabled","AddJavascriptInterface"}) // Only the bundled CSP-restricted local page can reach this bridge.
    private void createWeb() {
        try {
            LocalLyricsDocument document=new LocalLyricsDocument(html(moduleApk));
            web=new WebView(getContext()); web.setBackgroundColor(Color.TRANSPARENT);
            WebSettings settings=web.getSettings(); settings.setJavaScriptEnabled(true);
            settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
            settings.setDomStorageEnabled(false); settings.setBlockNetworkLoads(true);
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            long page=++pageGeneration;
            web.addJavascriptInterface(new Bridge(page),"YtmLyricsBridge");
            web.setWebViewClient(new WebViewClient() {
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return true;}
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    var response=document.request(request.getUrl().toString(),request.getMethod(),request.isForMainFrame());
                    return new WebResourceResponse(response.mimeType(),"UTF-8",response.status(),response.reason(),
                            java.util.Map.of("Cache-Control","no-store","X-Content-Type-Options","nosniff"),
                            new ByteArrayInputStream(response.body()));
                }
                @Override public void onReceivedError(WebView view,WebResourceRequest request,android.webkit.WebResourceError error){
                    if(view==web && request.isForMainFrame())fail("本地歌词页加载失败（"+error.getErrorCode()+"）",page);
                }
                @Override public void onReceivedHttpError(WebView view,WebResourceRequest request,WebResourceResponse error){
                    if(view==web && request.isForMainFrame())fail("本地歌词页加载失败（HTTP "+error.getStatusCode()+"）",page);
                }
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){
                    if(view==web && page==pageGeneration)session.failed("WebView 渲染进程退出",true);
                    return true;
                }
            });
            addView(web,new LayoutParams(-1,-1));
            // loadDataWithBaseURL may enter shouldInterceptRequest as a data: main frame.
            // Serve one explicit local HTTPS document instead; every other request stays blocked.
            web.loadUrl(LocalLyricsDocument.URL);
        }catch(IOException|RuntimeException error){session.failed(error.getMessage()==null?"初始化失败":error.getMessage(),false);}
    }
    private static synchronized String html(String moduleApk) throws IOException {
        if(cachedHtml!=null)return cachedHtml;
        try(var zip=new ZipFile(moduleApk)){
            var entry=zip.getEntry("assets/amll/lyrics.html");
            if(entry==null)throw new IOException("APK 缺少 AMLL 本地资源");
            try(var input=zip.getInputStream(entry)){cachedHtml=new String(input.readAllBytes(),StandardCharsets.UTF_8);}
        }
        return cachedHtml;
    }
    private void fail(String reason,long page) {
        post(() -> {
            if(web==null || page!=pageGeneration)return;
            session.failed(reason==null?"初始化失败":reason,false);
        });
    }
    private void destroyWeb() {
        pageGeneration++;
        WebView old=web; web=null;
        if(old!=null){removeView(old);old.removeJavascriptInterface("YtmLyricsBridge");old.destroy();}
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();session.attached(true);}
    @Override protected void onDetachedFromWindow(){session.attached(false);super.onDetachedFromWindow();}
    public final class Bridge {
        private final long page;
        Bridge(long page){this.page=page;}
        @JavascriptInterface public void ready(){post(() -> {if(web==null || page!=pageGeneration)return;session.readyPage();sendData();});}
        @JavascriptInterface public void failed(String reason){fail(reason,page);}
        @JavascriptInterface public void backgroundFailed(String reason){
            post(()->{if(web!=null && page==pageGeneration)android.util.Log.w("YtmLyrics","AMLL 背景使用静态回退："+reason);});
        }
        @JavascriptInterface public void seek(long id,long position){post(() -> {
            if(session.ready() && page==pageGeneration && id==serial && candidate!=null && position>=0
                    && (candidate.durationMs()<=0 || position<=candidate.durationMs()))callbacks.seek(position);
        });}
    }
}
