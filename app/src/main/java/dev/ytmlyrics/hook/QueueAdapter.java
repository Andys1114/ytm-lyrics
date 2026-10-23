package dev.ytmlyrics.hook;

import android.content.Context;
import android.media.session.MediaSession;
import dev.ytmlyrics.core.QueuePlan;
import dev.ytmlyrics.core.Song;
import io.github.libxposed.api.XposedModule;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/** Reads the actual queue heo.j publishes, including its shuffle/reorder window. */
final class QueueAdapter {
    private final XposedModule api;
    private final LyricsRuntime runtime;
    private final LyricsRuntime.Logger logger;
    private boolean warned;
    QueueAdapter(XposedModule api,LyricsRuntime runtime,LyricsRuntime.Logger logger){this.api=api;this.runtime=runtime;this.logger=logger;}
    void install(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> publisher=Class.forName("heo",false,loader);
        Class<?> metadata=Class.forName("hxg",false,loader);
        api.hook(publisher.getDeclaredMethod("j")).intercept(chain->{
            Object result=chain.proceed();
            try{read(chain.getThisObject(),metadata);}
            catch(ReflectiveOperationException|RuntimeException error){
                if(!warned){warned=true;logger.write("播放队列读取失败，保留当前歌曲查询",error);}
            }
            return result;
        });
        logger.write("9.41.52 playlist preload hook installed (heo.j / published queue)",null);
    }
    private void read(Object publisher,Class<?> metadata) throws ReflectiveOperationException {
        Context context=(Context)HostPanelFactory.get(publisher,"j");
        try {
            if(context.getPackageManager().getPackageInfo(context.getPackageName(),0).getLongVersionCode()!=94152240L)return;
        }catch(android.content.pm.PackageManager.NameNotFoundException error){return;}
        Object owner=HostPanelFactory.call(HostPanelFactory.get(publisher,"c"),"hP");
        Object compat=HostPanelFactory.get(owner,"a");
        if(compat==null)return;
        MediaSession session=(MediaSession)HostPanelFactory.get(HostPanelFactory.get(compat,"c"),"a");
        var published=session.getController().getQueue();
        if(published==null || published.isEmpty()){runtime.queue(context,session,List.of());return;}
        var wanted=new HashSet<Long>();for(var entry:published)wanted.add(entry.getQueueId());
        Object manager=HostPanelFactory.call(HostPanelFactory.get(publisher,"b"),"hP");
        var models=(List<?>)HostPanelFactory.call(manager,"n");
        var songs=new HashMap<Long,Song>();
        for(Object model:models) {
            long id=((Number)HostPanelFactory.call(model,"m")).longValue();
            if(wanted.contains(id) && metadata.isInstance(model))songs.put(id,QueueItemDecoder.read(model));
            if(songs.size()==wanted.size())break;
        }
        var items=new ArrayList<QueuePlan.Item>();
        for(var entry:published)items.add(new QueuePlan.Item(entry.getQueueId(),songs.get(entry.getQueueId())));
        runtime.queue(context,session,List.copyOf(items));
    }
}
