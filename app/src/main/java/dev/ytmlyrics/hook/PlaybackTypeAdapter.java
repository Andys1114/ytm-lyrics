package dev.ytmlyrics.hook;

import android.content.Context;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Adds the current item's raw type to the same metadata publication, without changing artist or ID. */
final class PlaybackTypeAdapter {
    private final XposedModule api;
    private final LyricsRuntime.Logger logger;
    private Boolean supported;
    private boolean warned;

    PlaybackTypeAdapter(XposedModule api, LyricsRuntime.Logger logger) {
        this.api = api;
        this.logger = logger;
    }

    void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> contributor = Class.forName("hey", false, loader);
        Class<?> publisher = Class.forName("acaf", false, loader);
        Method typeReader = HostPlaybackType.endpointReader(loader);
        Method write = Class.forName("ek", false, loader).getDeclaredMethod("c", String.class, long.class);
        write.setAccessible(true);
        // 94152240: acaf.n() builds ek, then hey.h(ek, abzh) contributes ALBUM.
        // hey.c -> PlaybackQueueManager.l() is also how hey.h reads the actual
        // playing item; do not wait for a separate published-queue callback.
        api.hook(publisher.getDeclaredMethod("n")).intercept(chain -> {
            Object result = chain.proceed();
            try {
                Context context = (Context) HostPanelFactory.get(chain.getThisObject(), "f");
                if (!supported(context)) return result;
                Object provider = HostPanelFactory.get(chain.getThisObject(), "n");
                if (!contributor.isInstance(provider)) return result;
                Object queue = HostPanelFactory.call(HostPanelFactory.get(provider, "c"), "hP");
                Object current = HostPanelFactory.call(queue, "l");
                write.invoke(result, HostPlaybackType.METADATA_KEY, (long) HostPlaybackType.wireType(current, typeReader));
            } catch (ReflectiveOperationException | RuntimeException | android.content.pm.PackageManager.NameNotFoundException error) {
                if (!warned) {
                    warned = true;
                    logger.write("播放类型读取失败，保留原有查询语义", error);
                }
            }
            return result;
        });
        logger.write("9.41.52 playback type hook installed (acaf.n / current watch endpoint)", null);
    }

    private boolean supported(Context context) throws android.content.pm.PackageManager.NameNotFoundException {
        if (supported == null) supported = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode() == 94152240L;
        return supported;
    }
}
