package dev.ytmlyrics.hook;

import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

public final class LyricsModule extends XposedModule {
    private static final String TARGET = "com.google.android.apps.youtube.music";
    private static final String TABS = "com.google.android.apps.youtube.music.ui.tabs.TabbedView";
    private static final String LOADING = "com.google.android.apps.youtube.music.ui.components.loading.LoadingFrameLayout";
    private static final int PLAYER_TABS_ID = 0x7f0b01c3;
    private static final int LYRICS_TITLE = 0x7f1404b7;
    private LyricsRuntime runtime;
    private boolean installed;
    private boolean warned;
    private String processName;
    private boolean missingTabLogged;

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName = param.getProcessName();
        report("framework=" + getFrameworkName() + " " + getFrameworkVersion() + ", api=" + getApiVersion(), null);
    }
    @Override public void onPackageReady(PackageReadyParam param) {
        if (!TARGET.equals(param.getPackageName()) || !TARGET.equals(processName) || installed) return;
        installed = true;
        runtime = new LyricsRuntime(this::report, getModuleApplicationInfo().sourceDir);
        try {
            hook(MediaSession.class.getDeclaredMethod("setMetadata", MediaMetadata.class)).intercept(chain -> {
                Object result = chain.proceed();
                runtime.metadata((MediaSession) chain.getThisObject(), (MediaMetadata) chain.getArg(0));
                return result;
            });
            hook(MediaSession.class.getDeclaredMethod("setPlaybackState", PlaybackState.class)).intercept(chain -> {
                Object result = chain.proceed();
                runtime.playback((MediaSession) chain.getThisObject(), (PlaybackState) chain.getArg(0));
                return result;
            });
            Class<?> tabs = Class.forName(TABS, false, param.getClassLoader());
            // jfr is an immutable pager entry. Supply its replacement content before native C
            // constructs it, rather than mutating the entry or adding an overlay afterward.
            hook(tabs.getDeclaredMethod("C", Class.forName("afop", false, param.getClassLoader()), View.class, View.class))
                    .intercept(chain -> {
                        Object[] args=chain.getArgs().toArray();
                        try {
                            ViewGroup tabView=(ViewGroup)chain.getThisObject();
                            Object model=field(args[0],"a");
                            if(tabView.getId()==PLAYER_TABS_ID && model!=null && args[2] instanceof ViewGroup root
                                    && root.getContext().getPackageManager().getPackageInfo(TARGET,0).getLongVersionCode()==94152240L
                                    && root.getResources().getString(LYRICS_TITLE).contentEquals(String.valueOf(field(model,"e")))) {
                                var replacement=runtime.attach(root);
                                if(replacement!=null)args[2]=replacement;
                            }
                        } catch(ReflectiveOperationException|RuntimeException|android.content.pm.PackageManager.NameNotFoundException error) {
                            report("旧歌词正文替换失败，保留原生入口",error);
                        }
                        return chain.proceed(args);
                    });
            hook(tabs.getDeclaredMethod("r", int.class)).intercept(chain -> {
                Object result = chain.proceed();
                try { attachLyrics((ViewGroup) chain.getThisObject()); }
                catch (ReflectiveOperationException | RuntimeException error) { report("歌词页适配失败", error); }
                return result;
            });
            Class<?> loading = Class.forName(LOADING, false, param.getClassLoader());
            hook(loading.getDeclaredMethod("h", int.class)).intercept(chain -> {
                Object result = chain.proceed();
                runtime.loadingChanged((ViewGroup) chain.getThisObject());
                return result;
            });
            report("9.41.52 hooks installed (media session / lyrics tab / loading state)", null);
            new ModernPlayerAdapter(this, runtime, this::report).install(param.getClassLoader());
        } catch (ReflectiveOperationException | RuntimeException error) { report("模块初始化失败", error); }
        try { new QueueAdapter(this,runtime,this::report).install(param.getClassLoader()); }
        catch(ReflectiveOperationException|RuntimeException error){report("预载队列接缝不可用，保留当前歌曲查询",error);}
    }
    private void attachLyrics(ViewGroup tabs) throws ReflectiveOperationException {
        if (tabs.getId() != PLAYER_TABS_ID) return;
        try {
            long code = tabs.getContext().getPackageManager().getPackageInfo(TARGET, 0).getLongVersionCode();
            if (code != 94152240L) {
                if (!warned) {
                    warned = true; report("跳过未适配的 YTM versionCode=" + code, null);
                    Toast.makeText(tabs.getContext(), "YTM Lyrics 目前仅适配 9.41.52", Toast.LENGTH_LONG).show();
                }
                return;
            }
        } catch (android.content.pm.PackageManager.NameNotFoundException error) { report("无法确认 YTM 版本", error); return; }
        Object pager = field(tabs, "a");
        List<?> entries = (List<?>) invoke(pager, "c", new Class<?>[0]);
        Object tabBar = field(tabs, "b");
        int tabCount = (Integer) invoke(tabBar, "b", new Class<?>[0]);
        if (tabCount != entries.size()) return; // Native construction/clear notification, not a ready pager.
        String lyricTitle = tabs.getResources().getString(LYRICS_TITLE);
        boolean found = false;
        for (int physicalIndex = 0; physicalIndex < entries.size(); physicalIndex++) {
            Object entry = entries.get(physicalIndex);
            Object model = field(field(entry, "d"), "a");
            if (model == null || !lyricTitle.contentEquals(String.valueOf(field(model, "e")))) continue;
            Object content = field(entry, "c");
            if (!(content instanceof ViewGroup root)) continue;
            found = true;
            // Entries use physical order; TabbedView.j(int) would apply a second RTL mapping.
            Object nativeTab = invoke(tabBar, "c", new Class<?>[]{int.class}, physicalIndex);
            View header = (View) field(nativeTab, "g");
            header.setEnabled(true); header.setAlpha(1f);
            // Content was supplied at C(), before the native pager stored its immutable entry.
        }
        if (!found && !entries.isEmpty() && !missingTabLogged) {
            missingTabLogged = true;
            report("原生播放器未提供旧歌词 tab；使用 action-bar / engagement-panel 适配", null);
        }
    }
    private static Object field(Object object, String name) throws ReflectiveOperationException {
        for (Class<?> type = object.getClass(); type != null; type = type.getSuperclass()) {
            try { Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object); }
            catch (NoSuchFieldException ignored) {}
        }
        throw new NoSuchFieldException(object.getClass().getName() + "." + name);
    }
    private static Object invoke(Object object, String name, Class<?>[] types, Object... args) throws ReflectiveOperationException {
        Method method = object.getClass().getMethod(name, types); method.setAccessible(true); return method.invoke(object, args);
    }
    private void report(String message, Throwable error) {
        if (error == null) { Log.i("YtmLyrics", message); log(Log.INFO, "YtmLyrics", message); }
        else { Log.e("YtmLyrics", message, error); log(Log.ERROR, "YtmLyrics", message, error); }
    }
}
