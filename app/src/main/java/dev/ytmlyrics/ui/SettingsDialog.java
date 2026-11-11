package dev.ytmlyrics.ui;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipFile;

/** An offline MDUI sheet with no dependency on the host's resources or AndroidX classes. */
public final class SettingsDialog {
    static final String URL = "https://ytm-lyrics.invalid/settings.html";
    static int heightPx(int screenHeight) { return Math.round(screenHeight * .9f); }
    public interface Controller {
        SettingsState state();
        void command(SettingsCommand command);
        void failed(String reason);
    }
    private static String cachedHtml;
    private final Dialog dialog;
    private final Controller controller;
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView web;
    private final TextView loading;
    private final android.app.Activity owner;
    private final android.app.Application.ActivityLifecycleCallbacks lifecycle;
    private boolean ready, disposed;
    private String lastState = "";
    private final Runnable startupTimeout = () -> fail("设置页启动超时");

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"}) // Only this intercepted, network-blocked local document can access the bridge.
    public SettingsDialog(Context context, String moduleApk, Controller controller) throws IOException {
        this.controller = controller;
        owner = activity(context);
        if (owner == null) throw new IllegalArgumentException("歌词控件需要 Activity");
        lifecycle = new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityDestroyed(android.app.Activity activity) { if(activity==owner) destroy(); }
            @Override public void onActivityCreated(android.app.Activity a,android.os.Bundle b) { }
            @Override public void onActivityStarted(android.app.Activity a) { }
            @Override public void onActivityResumed(android.app.Activity a) { }
            @Override public void onActivityPaused(android.app.Activity a) { if(a==owner) dismiss(); }
            @Override public void onActivityStopped(android.app.Activity a) { }
            @Override public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b) { }
        };
        LocalLyricsDocument document = new LocalLyricsDocument(URL, html(moduleApk));
        dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Color.TRANSPARENT);
        loading = new TextView(context);
        GradientDrawable loadingBackground = new GradientDrawable();
        loadingBackground.setColor(Color.rgb(20, 22, 28));
        float corner = 28 * context.getResources().getDisplayMetrics().density;
        loadingBackground.setCornerRadii(new float[]{corner,corner,corner,corner,0,0,0,0});
        loading.setBackground(loadingBackground);
        loading.setText("正在打开歌词设置…"); loading.setTextColor(Color.LTGRAY);
        loading.setTextSize(16); loading.setGravity(Gravity.CENTER);
        root.addView(loading, new FrameLayout.LayoutParams(-1, -1));
        web = new WebView(context);
        web.setBackgroundColor(Color.TRANSPARENT);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false); settings.setAllowContentAccess(false);
        settings.setDomStorageEnabled(false); settings.setBlockNetworkLoads(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(), "YtmSettingsBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return true; }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                var response = document.request(request.getUrl().toString(), request.getMethod(), request.isForMainFrame());
                return new WebResourceResponse(response.mimeType(), "UTF-8", response.status(), response.reason(),
                        Map.of("Cache-Control", "no-store", "X-Content-Type-Options", "nosniff"), new ByteArrayInputStream(response.body()));
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) main.post(() -> fail("设置页加载失败"));
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse response) {
                if (request.isForMainFrame()) main.post(() -> fail("设置页加载失败（HTTP " + response.getStatusCode() + "）"));
            }
            @Override public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                fail("设置页渲染进程退出"); // Destroy this WebView before returning; never restart YTM.
                return true;
            }
        });
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        dialog.setContentView(root);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(ignored -> { if(web!=null)web.onPause(); });
        owner.getApplication().registerActivityLifecycleCallbacks(lifecycle);
        // One local navigation per Activity, also when warmed before the first tap.
        main.postDelayed(startupTimeout, 10_000);
        web.loadUrl(URL);
    }

    public void show() {
        if(disposed)return;
        web.onResume();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            var metrics = dialog.getContext().getResources().getDisplayMetrics();
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            window.setDimAmount(.55f);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setLayout(Math.min(metrics.widthPixels, Math.round(560 * metrics.density)), heightPx(metrics.heightPixels));
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        lastState="";update();
    }

    public boolean reusable(Context context) { return !disposed && activity(context)==owner; }
    public void dismiss() { dialog.dismiss(); }
    public void destroy() { dialog.dismiss(); dispose(); }
    public void update() {
        if (!ready || disposed || web == null || !dialog.isShowing()) return;
        SettingsState snapshot=controller.state();
        Window window=dialog.getWindow();
        if(window!=null) {
            var metrics=dialog.getContext().getResources().getDisplayMetrics();
            int target=heightPx(metrics.heightPixels);
            if(window.getAttributes().height!=target)window.setLayout(Math.min(metrics.widthPixels,Math.round(560*metrics.density)),target);
        }
        String state = snapshot.json();
        if (lastState.equals(state)) return;
        lastState = state;
        web.evaluateJavascript("window.YtmSettingsHost.update(" + state + ");", null);
    }
    private void fail(String reason) {
        if (disposed) return;
        boolean visible=dialog.isShowing();
        destroy();
        if(visible)controller.failed(reason);
        else android.util.Log.w("YtmLyrics",reason);
    }
    private void dispose() {
        if (disposed) return;
        disposed = true; ready = false; main.removeCallbacks(startupTimeout);
        owner.getApplication().unregisterActivityLifecycleCallbacks(lifecycle);
        WebView old = web; web = null;
        if (old != null) {
            if (old.getParent() instanceof android.view.ViewGroup parent) parent.removeView(old);
            old.removeJavascriptInterface("YtmSettingsBridge"); old.destroy();
        }
    }
    private static android.app.Activity activity(Context context) {
        while(context instanceof android.content.ContextWrapper wrapper) {
            if(context instanceof android.app.Activity value)return value;
            context=wrapper.getBaseContext();
        }
        return null;
    }
    private static synchronized String html(String moduleApk) throws IOException {
        if (cachedHtml != null) return cachedHtml;
        try (var zip = new ZipFile(moduleApk)) {
            var entry = zip.getEntry("assets/settings/settings.html");
            if (entry == null) throw new IOException("APK 缺少设置页资源");
            try (var input = zip.getInputStream(entry)) { cachedHtml = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
        }
        return cachedHtml;
    }
    public final class Bridge {
        @JavascriptInterface public void ready() {
            main.post(() -> { if (!disposed) { ready = true; loading.setVisibility(android.view.View.GONE); main.removeCallbacks(startupTimeout); update(); if(!dialog.isShowing() && web!=null)web.onPause(); } });
        }
        @JavascriptInterface public void failed() { main.post(() -> fail("设置组件加载失败")); }
        @JavascriptInterface public void command(String encoded) {
            main.post(() -> {
                if (disposed || !ready || !dialog.isShowing()) return;
                try { controller.command(SettingsCommand.parse(encoded)); }
                catch (IllegalArgumentException error) { android.util.Log.w("YtmLyrics", "已忽略无效的设置操作"); }
            });
        }
    }
}
