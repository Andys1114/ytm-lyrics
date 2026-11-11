package dev.ytmlyrics.hook;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import io.github.libxposed.api.XposedModule;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.WeakHashMap;

/** Uses YTM's own engagement-panel controller, header, dismissal and content container. */
@android.annotation.SuppressLint("ResourceType") // Resource IDs belong to the fixed host APK, not this module.
final class ModernPlayerAdapter {
    private static final String LOADING = "com.google.android.libraries.youtube.rendering.ui.widget.loadingframe.LoadingFrameLayout";
    private static final int VIDEO_ACTION_BAR = 0x7f0b0e3d;
    private final XposedModule api;
    private final LyricsRuntime runtime;
    private final LyricsRuntime.Logger logger;
    private final WeakHashMap<ViewGroup, Boolean> customPanels = new WeakHashMap<>();
    private WeakReference<Object> controller = new WeakReference<>(null);
    private HostPanelFactory factory;
    private Class<?> panelClass;
    private Class<?> endpointClass;
    private Class<?> transitionClass;
    private long pendingLyricsClick;
    private Boolean supportedVersion;

    ModernPlayerAdapter(XposedModule api, LyricsRuntime runtime, LyricsRuntime.Logger logger) {
        this.api = api; this.runtime = runtime; this.logger = logger;
    }
    void install(ClassLoader loader) throws ReflectiveOperationException {
        factory = new HostPanelFactory(loader);
        panelClass = factory.type("akpp"); endpointClass = factory.type("ajrz"); transitionClass = factory.type("vkm");
        Class<?> controllerClass = factory.type("vlf");
        for (Method method : controllerClass.getDeclaredMethods()) {
            if (!method.getName().equals("w") && !method.getName().equals("x")) continue;
            if (!java.util.Arrays.asList(method.getParameterTypes()).contains(panelClass)) continue;
            api.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                controller = new WeakReference<>(chain.getThisObject());
                return result;
            });
        }
        Class<?> panel = factory.type("vke");
        Class<?> header=factory.type("vly");
        api.hook(header.getDeclaredMethod("c",Context.class)).intercept(chain->{
            Object previous=HostPanelFactory.get(chain.getThisObject(),"F");
            if(previous instanceof View view)runtime.unbindLyricsHeader(view);
            Object result=chain.proceed();
            String id=(String)HostPanelFactory.get(chain.getThisObject(),"O");
            if(LyricsAction.panel(id) && result instanceof View view && supported(view.getContext())) {
                Object title=HostPanelFactory.get(chain.getThisObject(),"J");
                if(!runtime.bindLyricsHeader(view,title instanceof android.widget.TextView text?text:null,
                        view.getResources().getText(0x7f1404b7)))logger.write("原生标题槽未匹配，使用正文内快捷行",null);
            }
            return result;
        });
        // The host may replace the rounded shell's background after the header is bound.
        api.hook(controllerClass.getDeclaredMethod("C",android.graphics.drawable.GradientDrawable.class,int.class))
                .intercept(chain->{Object result=chain.proceed();runtime.tintHeaders();return result;});
        api.hook(controllerClass.getDeclaredMethod("F",factory.type("vko")))
                .intercept(chain->{Object result=chain.proceed();runtime.tintHeaders();return result;});
        api.hook(factory.type("vmc").getDeclaredMethod("g",Context.class,boolean.class,boolean.class,boolean.class,
                boolean.class,View.class,View.class,factory.type("vui"),boolean.class)).intercept(chain->{
            boolean first=chain.getArg(5) instanceof View view && runtime.restoreHeaderBackground(view);
            boolean second=chain.getArg(6) instanceof View view && runtime.restoreHeaderBackground(view);
            try { return chain.proceed(); }
            finally { if(first || second)runtime.tintHeaders(); }
        });
        api.hook(panel.getDeclaredMethod("c")).intercept(chain -> {
            Object result = chain.proceed();
            String id = (String) HostPanelFactory.call(chain.getThisObject(), "w");
            if (id != null && id.toLowerCase(Locale.ROOT).contains("lyrics") && result instanceof ViewGroup loading
                    && supported(loading.getContext())) {
                pendingLyricsClick = 0;
                // The native presenter consumes a View, not a LoadingFrameLayout. Swap its
                // content return value; the original root remains owned by the native model.
                if (HostPanelFactory.ID.equals(id)) customPanels.put(loading, true);
                var replacement=runtime.attach(loading);
                if (HostPanelFactory.ID.equals(id)) HostPanelFactory.call(chain.getThisObject(), "W");
                if(replacement!=null)return replacement;
            }
            return result;
        });
        // Server-rendered Chinese labels can coexist with English local resources. Track the
        // actual lyric action instead of assuming its feedback uses the local resource language.
        api.hook(View.class.getDeclaredMethod("performClick")).intercept(chain -> {
            View clicked = (View) chain.getThisObject();
            boolean lyricsAction = isLyricsAction(clicked);
            pendingLyricsClick = lyricsAction ? android.os.SystemClock.uptimeMillis() : 0;
            Object result=chain.proceed();
            if(lyricsAction && Boolean.FALSE.equals(result)) {
                try {if(openCustom(clicked.getContext()))return true;}
                catch(ReflectiveOperationException|RuntimeException error){logger.write("歌词按钮打开失败",error);}
            }
            return result;
        });
        // Litho may apply the disabled properties before it binds the accessibility label.
        // Repair once at label binding, then keep only this action enabled across rebinds.
        var binding=new LyricsActionBinding<>(new LyricsActionBinding.Port<View>() {
            @Override public boolean matches(View view){return isLyricsAction(view);}
            @Override public boolean enabled(View view){return view.isEnabled();}
            @Override public boolean clickable(View view){return view.isClickable();}
            @Override public float alpha(View view){return view.getAlpha();}
            @Override public void apply(View view,boolean enabled,boolean clickable,float alpha){view.setEnabled(enabled);view.setClickable(clickable);view.setAlpha(alpha);}
        });
        Class<?> action=factory.type("com.facebook.litho.ComponentHost");
        api.hook(action.getMethod("setContentDescription",CharSequence.class)).intercept(chain->{
            View view=(View)chain.getThisObject();binding.beforeLabel(view);
            Object result=chain.proceed();binding.afterLabel(view);
            return result;
        });
        for(String name:new String[]{"setEnabled","setClickable","setAlpha"}) {
            boolean alpha=name.equals("setAlpha");
            api.hook(action.getMethod(name,alpha?float.class:boolean.class)).intercept(chain->{
                Object original=chain.getArg(0);
                Object value=binding.requested((View)chain.getThisObject(),name,original);
                return value.equals(original) ? chain.proceed():chain.proceed(new Object[]{value});
            });
        }
        LyricsActionAppearance.install(api,loader,this::isLyricsAction);
        Class<?> loading = Class.forName(LOADING, false, loader);
        Method contentState = loading.getDeclaredMethod("g", int.class);
        contentState.setAccessible(true);
        api.hook(contentState).intercept(chain -> {
            Object result = chain.proceed();
            ViewGroup root = (ViewGroup) chain.getThisObject();
            if (customPanels.containsKey(root) && (Integer) chain.getArg(0) != 2) contentState.invoke(root, 2);
            runtime.loadingChanged(root);
            return result;
        });
        // Exact native snackbar sink for the no-lyrics action; other notifications and native
        // lyric endpoints are unchanged. Only a successful native-panel open replaces the feedback.
        api.hook(factory.type("jgp").getDeclaredMethod("d", factory.type("aeav"))).intercept(chain -> {
            Context current = (Context) HostPanelFactory.get(chain.getThisObject(), "a");
            CharSequence text = (CharSequence) HostPanelFactory.call(chain.getArg(0), "e");
            if (text != null && current.getPackageName().equals("com.google.android.apps.youtube.music")) {
                String expected = current.getString(0x7f1404b5);
                boolean fromLyricsAction = pendingLyricsClick > 0 && android.os.SystemClock.uptimeMillis() - pendingLyricsClick < 1500;
                if (expected.contentEquals(text) || fromLyricsAction) {
                    pendingLyricsClick = 0;
                    try { if (openCustom(current)) return null; }
                    catch (ReflectiveOperationException | RuntimeException error) { logger.write("无法打开原生自定义歌词面板", error); }
                }
            }
            return chain.proceed();
        });
        try { installLyricsActionOrder(); }
        catch (ReflectiveOperationException | RuntimeException error) {
            logger.write("无法调整歌词按钮顺序，保留播放器原始排列", error);
        }
        logger.write("9.41.52 modern player hooks installed", null);
    }
    private void installLyricsActionOrder() throws ReflectiveOperationException {
        // jma.b renders both client-generated and server-provided player action bars.
        // Only replace the Elements bytes of this bar; do not reorder mounted RecyclerView children.
        Class<?> presenter = factory.type("jma");
        Class<?> renderer = factory.type("akmt");
        Class<?> extension = factory.type("aich");
        Class<?> byteString = factory.type("aibu");
        Field bar = presenter.getDeclaredField("a");
        bar.setAccessible(true);
        Object elements = factory.type("armg").getField("a").get(null);
        Method read = renderer.getMethod("b", extension);
        Method bytes = byteString.getMethod("G");
        Method wrap = byteString.getMethod("J", byte[].class);
        Method toBuilder = factory.type("aicy").getMethod("toBuilder");
        Method write = factory.type("aics").getMethod("e", extension, Object.class);
        api.hook(presenter.getDeclaredMethod("b", renderer, boolean.class)).intercept(chain -> {
            Object original = chain.getArg(0);
            Object replacement = original;
            try {
                if (bar.get(chain.getThisObject()) instanceof View view && view.getId() == VIDEO_ACTION_BAR
                        && supported(view.getContext())) {
                    byte[] before = (byte[]) bytes.invoke(read.invoke(original, elements));
                    byte[] after = LyricsActionOrder.moveFallback(before, view.getResources().getString(0x7f1404b7));
                    if (before != after) {
                        Object builder = toBuilder.invoke(original);
                        write.invoke(builder, elements, wrap.invoke(null, after));
                        replacement = HostPanelFactory.call(builder, "build");
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException error) {
                logger.write("歌词按钮重排失败，保留播放器原始排列", error);
            }
            return replacement == original ? chain.proceed() : chain.proceed(new Object[]{replacement, chain.getArg(1)});
        });
    }
    private boolean openCustom(Context current) throws ReflectiveOperationException {
        if (!supported(current)) return false;
        Object host = controller.get();
        if (host == null) return false;
        Method find = host.getClass().getMethod("c", String.class); find.setAccessible(true);
        if (find.invoke(host, HostPanelFactory.ID) == null) {
            Method register = host.getClass().getMethod("w", panelClass); register.setAccessible(true);
            register.invoke(host, factory.model(current.getString(0x7f1404b7)));
        }
        if (find.invoke(host, HostPanelFactory.ID) == null) { logger.write("原生歌词面板未注册，保留原生提示", null); return false; }
        Method show = host.getClass().getMethod("e", endpointClass, transitionClass); show.setAccessible(true);
        Object shown = show.invoke(host, factory.endpoint(), null);
        if (shown == null) { logger.write("原生控制器未打开歌词面板，保留原生提示", null); return false; }
        logger.write("custom lyrics opened in native engagement panel", null);
        return true;
    }
    private boolean supported(Context context) {
        if (supportedVersion == null) {
            try { supportedVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode() == 94152240L; }
            catch (android.content.pm.PackageManager.NameNotFoundException error) { supportedVersion = false; }
        }
        return supportedVersion;
    }
    private boolean isLyricsAction(View view) {
        if(!view.getClass().getName().equals("com.facebook.litho.ComponentHost"))return false;
        String label=view.getContentDescription()==null?"":view.getContentDescription().toString();
        return LyricsAction.matches(view.getClass().getName(),label,view.getResources().getString(0x7f1404b7)) && supported(view.getContext());
    }
}
