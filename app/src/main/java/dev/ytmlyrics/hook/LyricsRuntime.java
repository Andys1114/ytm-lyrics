package dev.ytmlyrics.hook;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.view.Gravity;
import dev.ytmlyrics.core.Candidate;
import dev.ytmlyrics.core.LyricsSource;
import dev.ytmlyrics.core.Resolver;
import dev.ytmlyrics.core.Song;
import dev.ytmlyrics.core.QueuePlan;
import dev.ytmlyrics.core.PlaylistPreloader;
import dev.ytmlyrics.core.LyricsOffset;
import dev.ytmlyrics.source.BindingStore;
import dev.ytmlyrics.source.PreloadCache;
import dev.ytmlyrics.source.SourceRegistry;
import dev.ytmlyrics.source.OffsetStore;
import dev.ytmlyrics.ui.LyricsPanel;
import dev.ytmlyrics.ui.ArtworkFrame;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Owns one current playback item and discards results from all earlier generations. */
final class LyricsRuntime implements LyricsPanel.Actions {
    interface Logger { void write(String message, Throwable error); }
    private final Logger logger;
    private final String moduleApk;
    private boolean showTranslation = true;
    private long playbackEpoch;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicLong generation = new AtomicLong();
    private final ExecutorService network = Executors.newFixedThreadPool(2, job -> {
        Thread thread = new Thread(job, "ytm-lyrics-source"); thread.setDaemon(true); return thread;
    });
    private final WeakHashMap<ViewGroup, WeakReference<LyricsPanel>> panels = new WeakHashMap<>();
    private final WeakHashMap<View,Drawable> headers=new WeakHashMap<>();
    private final ArtworkState<Bitmap> artworkState=new ArtworkState<>();
    private ArtworkFrame artwork=ArtworkFrame.empty(0);
    private final ExecutorService artworkWorker=Executors.newSingleThreadExecutor(job->{
        Thread thread=new Thread(job,"ytm-lyrics-artwork");thread.setDaemon(true);return thread;
    });
    private Song song;
    private Candidate selected;
    private List<Candidate> candidates = List.of();
    private String message = "播放歌曲后查询歌词";
    private String requestedKey;
    private SourceRegistry sources;
    private OffsetStore offsets;
    private int lyricAdvanceMs;
    private WeakReference<AlertDialog> settingsDialog=new WeakReference<>(null);
    private WeakReference<TextView> settingsStatus=new WeakReference<>(null);
    private BindingStore bindings;
    private PreloadCache cache;
    private PlaylistPreloader preloader;
    private List<QueuePlan.Item> queue=List.of();
    private MediaSession.Token queueToken;
    private boolean foregroundBusy;
    private PlaylistPreloader.Progress preloadProgress=new PlaylistPreloader.Progress(0,0,false);
    private WeakReference<TextView> preloadStatus=new WeakReference<>(null);
    private MediaController controller;
    private PlaybackState playback;
    private boolean ticking;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            boolean visible = false;
            for (var ref : new ArrayList<>(panels.values())) {
                LyricsPanel panel = ref.get();
                if (panel != null && panel.isAttachedToWindow() && panel.getWindowVisibility() == android.view.View.VISIBLE) {
                    visible = true;
                    if (panel.isShown()) panel.position(position(), isPlaying(), speed(), playbackEpoch);
                }
            }
            ticking = visible;
            if (visible) main.postDelayed(this, 150);
        }
    };
    LyricsRuntime(Logger logger, String moduleApk) { this.logger = logger; this.moduleApk = moduleApk; }

    LyricsPanel attach(ViewGroup root) {
        if (!(root instanceof FrameLayout)) {
            logger.write("歌词内容容器不是预期 FrameLayout，跳过替换", null); return null;
        }
        var known = panels.get(root);
        if (known != null && known.get() != null) { startTicker(); return known.get(); }
        initialize(root.getContext());
        LyricsPanel panel = new LyricsPanel(root.getContext(), this, moduleApk);
        panels.put(root, new WeakReference<>(panel));
        // Return this content view to the native presenter instead of adding a sibling over
        // LoadingFrameLayout. Keep that original root intact for native updates and fallback.
        ViewGroup parent=root.getParent() instanceof ViewGroup group ? group : null;
        int index=parent==null ? -1 : parent.indexOfChild(root);
        var params=root.getLayoutParams();
        if(parent!=null)parent.removeView(root);
        panel.nativeContent(root);
        panel.display(selected, message);
        panel.artwork(artwork);
        if(parent!=null)parent.addView(panel,index,params);
        panel.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(android.view.View view) { startTicker(); }
            @Override public void onViewDetachedFromWindow(android.view.View view) {}
        });
        panel.position(position(),isPlaying(),speed(),playbackEpoch);
        lookup(false);
        startTicker();
        return panel;
    }
    void loadingChanged(ViewGroup root) {
        var known = panels.get(root);
        if (known != null && known.get() != null) startTicker();
    }
    private void initialize(Context context) {
        if(sources!=null)return;
        Context app=context.getApplicationContext();
        var prefs=app.getSharedPreferences("dev.ytmlyrics.preferences",Context.MODE_PRIVATE);
        sources=new SourceRegistry(prefs);bindings=new BindingStore(prefs);
        offsets=new OffsetStore(app.getSharedPreferences("dev.ytmlyrics.offsets",Context.MODE_PRIVATE));
        lyricAdvanceMs=offsets.get(song);
        cache=new PreloadCache(app.getSharedPreferences("dev.ytmlyrics.preload",Context.MODE_PRIVATE));
        showTranslation=prefs.getBoolean("show-translation",true);
        var worker=Executors.newSingleThreadExecutor(job->{
            Thread thread=new Thread(job,"ytm-lyrics-preload");thread.setDaemon(true);thread.setPriority(Thread.MIN_PRIORITY);return thread;
        });
        preloader=new PlaylistPreloader(worker,new PlaylistPreloader.Cache(){
            @Override public boolean contains(Song value,String policy){
                Candidate binding=bindings.get(value);
                // Source enable/order is captured in the policy; do not access mutable registry state here.
                String enabled=policy.substring(policy.indexOf('/')+1);
                return (binding!=null && java.util.Arrays.asList(enabled.split(",")).contains(binding.sourceId())) || cache.get(value,policy)!=null;
            }
            @Override public void put(Song value,String policy,List<Candidate> found,boolean complete){cache.put(value,policy,found,complete);}
        },(value,enabled,cancelled)->new Resolver().resolve(value,enabled,cancelled),System::currentTimeMillis,
                progress->main.post(()->{preloadProgress=progress;render();}),
                (value,error)->logger.write("队列歌词预载失败",error));
    }
    void queue(Context context,MediaSession session,List<QueuePlan.Item> items) {
        main.post(()->{
            initialize(context);
            if(controller!=null && !controller.getSessionToken().equals(session.getSessionToken()))return;
            controller=session.getController();queueToken=session.getSessionToken();queue=items;
            lookup(false);updatePreload();
        });
    }
    private List<Song> preloadSongs() {
        if(controller==null || queueToken==null || !queueToken.equals(controller.getSessionToken()))return List.of();
        long active=playback==null ? -1:playback.getActiveQueueItemId();
        return QueuePlan.following(queue,active,song,3);
    }
    private void updatePreload() {
        if(preloader==null)return;
        preloader.update(sources.preloadEnabled()?preloadSongs():List.of(),sources.enabled(),foregroundBusy);
    }
    private Song cacheIdentity(Song value) {
        if(controller==null || queueToken==null || !queueToken.equals(controller.getSessionToken()))return value;
        return QueuePlan.cacheIdentity(queue,playback==null?-1:playback.getActiveQueueItemId(),value);
    }
    private void foreground(boolean busy){foregroundBusy=busy;updatePreload();}
    private String preloadLabel() {
        if(sources==null || !sources.preloadEnabled())return "预载已关闭";
        if(queueToken==null)return "预载：等待播放队列";
        int total=preloadSongs().size();
        if(total==0)return "预载：队列内暂无后续歌曲";
        if(foregroundBusy)return "预载：当前歌曲优先";
        return "队列预载 "+Math.min(preloadProgress.cached(),total)+"/"+total+(preloadProgress.working()?"…":"");
    }
    void metadata(MediaSession session, MediaMetadata metadata) {
        main.post(() -> {
            if (metadata == null) {
                updateArtwork(null,null);
                closeSettings();lyricAdvanceMs=0;
                generation.incrementAndGet(); song = null; selected = null; candidates = List.of(); requestedKey = null;
                playback = null;foreground(false); message = "当前没有歌曲信息"; render(); return;
            }
            controller = session.getController();
            String title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE);
            if (title == null || title.isBlank()) title = metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE);
            String artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null || artist.isBlank()) artist = metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            Song next = new Song(metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID), title, artist,
                    metadata.getString(MediaMetadata.METADATA_KEY_ALBUM), metadata.getLong(MediaMetadata.METADATA_KEY_DURATION));
            boolean changed = song == null || !song.equals(next);
            song = next;
            Bitmap cover=metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if(cover==null)cover=metadata.getBitmap(MediaMetadata.METADATA_KEY_ART);
            if(cover==null)cover=metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
            updateArtwork(next.key(),cover);
            if (changed) {
                closeSettings();lyricAdvanceMs=offsets==null?0:offsets.get(next);
                generation.incrementAndGet(); selected = null; candidates = List.of(); requestedKey = null;
                playback = controller.getPlaybackState(); playbackEpoch++;
                message = "正在查询歌词…"; render();
            }
            lookup(false); updatePreload();startTicker();
        });
    }
    private void updateArtwork(String owner,Bitmap source) {
        var request=artworkState.update(owner,source);
        if(request==null)return;
        if(request.ownerChanged() || source==null) {
            artwork=ArtworkFrame.empty(request.revision());renderArtwork();
        }
        if(source==null)return;
        artworkWorker.execute(()->{
            if(!artworkState.current(request))return;
            try {
                ArtworkFrame frame=ArtworkEncoder.encode(source,request.revision());
                main.post(()->{if(artworkState.current(request)){artwork=frame;renderArtwork();}});
            } catch(RuntimeException error) {
                main.post(()->{if(artworkState.current(request)) {
                    artwork=ArtworkFrame.empty(request.revision());renderArtwork();
                    logger.write("歌曲封面处理失败，使用静态底色",error);
                }});
            }
        });
    }
    void lyricsHeader(View view,boolean enabled) {
        if(enabled) {
            if(!headers.containsKey(view))headers.put(view,view.getBackground());
            tintHeaders();
        } else if(headers.containsKey(view))view.setBackground(headers.remove(view));
    }
    private void tintHeaders() {
        for(var entry:new ArrayList<>(headers.entrySet())) {
            if(selected!=null && selected.hasSynced())entry.getKey().setBackground(new android.graphics.drawable.ColorDrawable(artwork.color()));
            else entry.getKey().setBackground(entry.getValue());
        }
    }
    private void renderArtwork() {
        for(var ref:new ArrayList<>(panels.values())){var panel=ref.get();if(panel!=null)panel.artwork(artwork);}
        tintHeaders();
    }
    void playback(MediaSession session, PlaybackState state) {
        main.post(() -> {
            controller = session.getController();
            if (playback == null || state == null || Math.abs(position(state) - position()) > 350
                    || playback.getPlaybackSpeed() != state.getPlaybackSpeed()) playbackEpoch++;
            playback = state; updatePreload();startTicker();
        });
    }
    private void startTicker() { if (!ticking) { ticking = true; main.post(tick); } }
    private boolean isPlaying() { return playback != null && playback.getState() == PlaybackState.STATE_PLAYING; }
    private float speed() { return playback == null ? 1 : playback.getPlaybackSpeed(); }
    private long position() { return position(playback); }
    private long position(PlaybackState state) {
        if (state == null) return 0;
        long position = state.getPosition();
        if (state.getState() == PlaybackState.STATE_PLAYING && state.getLastPositionUpdateTime() > 0)
            position += Math.round((SystemClock.elapsedRealtime() - state.getLastPositionUpdateTime()) * state.getPlaybackSpeed());
        if (song != null && song.durationMs() > 0) position = Math.min(song.durationMs(), position);
        return Math.max(0, position);
    }
    private void lookup(boolean force) {
        if (sources == null || song == null) return;
        if (!force && song.key().equals(requestedKey)) return;
        Song requestSong = song;
        requestedKey = song.key();
        long token = generation.incrementAndGet();
        foregroundBusy=false;
        selected = null; candidates = List.of();
        List<LyricsSource> enabled = sources.enabled();
        Candidate binding = bindings.get(song);
        if (binding != null && enabled.stream().anyMatch(s -> s.id().equals(binding.sourceId()))) {
            selected = binding; message = label(binding) + " · 已记住的选择"; render();
            LyricsSource provider = sources.provider(binding.sourceId());
            if (provider != null && provider.supportsEnhancement()
                    && (force || !binding.hasWords() || !binding.hasTranslation())) refreshBinding(requestSong, binding, provider, token);
            else foreground(false);
            return;
        }
        if (song.title().isBlank() || song.artist().isBlank()) {
            message = "歌曲信息不完整，保留原生歌词；可到“歌词设置”搜索";foreground(false); render(); return;
        }
        if (enabled.isEmpty()) { message = "未启用歌词来源，保留原生歌词";foreground(false); render(); return; }
        String policy=PlaylistPreloader.policy(enabled);
        Song cacheSong=cacheIdentity(requestSong);
        PreloadCache.Pool pool=cache.get(cacheSong,policy);
        Candidate cached=pool==null ? null:pool.best(song);
        if(pool!=null) {
            selected=cached;candidates=pool.candidates();
            if(cached!=null)message=label(cached)+" · 已命中播放列表缓存";
            else message="已载入缓存候选，正在按实际播放信息查询…";
            render();
            // A queue query can lack duration/album. An unmatched pool must not
            // suppress the normal lookup with complete current playback metadata.
            if(!force && cached!=null && pool.complete()){foreground(false);return;}
        }
        if(cached==null)message = "正在查询歌词…";
        foreground(true);render();
        network.execute(() -> {
            Resolver.Result result = new Resolver().resolve(requestSong, enabled, () -> token != generation.get(), partial -> main.post(() -> {
                if(token!=generation.get() || !sameSong(requestSong))return;
                selected=partial; message=label(partial)+" · 正在比较其他来源…";render();
            }));
            main.post(() -> {
                if (token != generation.get() || result.cancelled()) return;
                foreground(false);
                if (!result.errors().isEmpty()) logger.write("来源查询失败：" + String.join("；", result.errors()), null);
                cache.put(cacheSong,policy,result.candidates(),result.errors().isEmpty());
                selected = result.selected(); candidates = result.candidates();
                if(selected==null && !result.errors().isEmpty() && cached!=null){selected=cached;candidates=pool.candidates();}
                if (selected != null) message = label(selected) + " · 已自动择优";
                else if (candidates.stream().anyMatch(Candidate::hasLyrics)) message = "找到候选但匹配不确定，请到“歌词设置”选择歌词";
                else if (!result.errors().isEmpty()) message = String.join("；", result.errors()) + "；保留原生歌词";
                else message = "未找到可用歌词，保留原生歌词";
                if (selected != null && !result.errors().isEmpty()) message += " · 部分来源查询失败";
                render();
            });
        });
    }
    private void refreshBinding(Song request, Candidate cached, LyricsSource provider, long token) {
        foreground(true);
        message = label(cached) + " · 已显示缓存，正在补取增强数据…"; render();
        network.execute(() -> {
            try {
                Candidate updated = provider.refresh(cached).preserveCache(cached);
                main.post(() -> {
                    if (token != generation.get() || !sameSong(request) || !sources.isEnabled(cached.sourceId())) return;
                    foreground(false);
                    selected = updated; bindings.put(request, updated);
                    message = label(updated) + " · 已记住的选择"; render();
                });
            } catch (Exception error) {
                main.post(() -> {
                    if (token != generation.get() || !sameSong(request)) return;
                    foreground(false);
                    message = label(cached) + " · 增强取词失败，继续显示缓存：" + error.getMessage();
                    logger.write("绑定增强取词失败", error); render();
                });
            }
        });
    }
    @Override public boolean translations() { return showTranslation; }
    @Override public void translations(boolean enabled) {
        showTranslation = enabled;
        if (sources != null) sources.setTranslation(enabled);
        for (var ref : new ArrayList<>(panels.values())) { var panel = ref.get(); if (panel != null) panel.translationChanged(); }
    }
    private String label(Candidate candidate) {
        return sources.nameOf(candidate.sourceId()) + (candidate.hasWords() ? " · 逐字同步" : candidate.hasSynced() ? " · 逐行同步" : " · 纯文本")
                + (candidate.hasTranslation() ? " · 含译文" : "")
                + (candidate.enhancementError().isBlank() ? "" : " · " + candidate.enhancementError());
    }
    private void render() {
        tintHeaders();
        TextView preloadView=preloadStatus.get();if(preloadView!=null)preloadView.setText(preloadLabel());
        TextView settingView=settingsStatus.get();if(settingView!=null)settingView.setText(message);
        for (var ref : new ArrayList<>(panels.values())) {
            LyricsPanel panel = ref.get();
            if (panel != null) {
                panel.display(selected, message);
                // Queue the real anchor with a new timeline, not a first-line flash
                // followed by a correction on the next 150 ms ticker interval.
                panel.position(position(),isPlaying(),speed(),playbackEpoch);
            }
        }
    }
    @Override public void retry() { main.post(() -> lookup(true)); }
    @Override public void visible() {
        for(var ref:new ArrayList<>(panels.values())) {
            var panel=ref.get();if(panel!=null && panel.isShown())panel.position(position(),isPlaying(),speed(),playbackEpoch);
        }
        startTicker();
    }
    @Override public void seek(long timeMs) {
        if (controller != null) controller.getTransportControls().seekTo(LyricsOffset.seek(timeMs,lyricAdvanceMs,song==null?0:song.durationMs()));
    }
    @Override public int offsetMs(){return lyricAdvanceMs;}
    private void closeSettings(){var dialog=settingsDialog.get();if(dialog!=null)dialog.dismiss();settingsDialog.clear();}
    @Override public void settings(Context context) {
        initialize(context);closeSettings();
        Song request=song;
        int pad=Math.round(16*context.getResources().getDisplayMetrics().density);
        LinearLayout list=new LinearLayout(context);list.setOrientation(LinearLayout.VERTICAL);list.setPadding(pad,0,pad,pad);
        TextView detail=new TextView(context);detail.setText(message);detail.setTextSize(15);detail.setPadding(0,pad/2,0,pad);
        settingsStatus=new WeakReference<>(detail);
        list.addView(detail);
        for(var ref:panels.values()) {var panel=ref.get();if(panel!=null && panel.isShown() && !panel.rendererStatus().isBlank()) {
            TextView error=new TextView(context);error.setText(panel.rendererStatus());error.setTextSize(15);list.addView(error);break;
        }}
        LinearLayout commands=new LinearLayout(context);
        Button choose=new Button(context);choose.setText("选择歌词");choose.setOnClickListener(v->{closeSettings();choose(context);});
        Button retry=new Button(context);retry.setText("重新获取");retry.setOnClickListener(v->{
            closeSettings();for(var ref:new ArrayList<>(panels.values())){var panel=ref.get();if(panel!=null)panel.restartRenderer();}retry();
        });
        commands.addView(choose,new LinearLayout.LayoutParams(0,-2,1));commands.addView(retry,new LinearLayout.LayoutParams(0,-2,1));list.addView(commands);
        CheckBox translation=new CheckBox(context);translation.setText("显示来源译文");translation.setChecked(showTranslation);
        translation.setOnCheckedChangeListener((button,value)->translations(value));list.addView(translation);
        TextView timing=new TextView(context);timing.setText(request==null?"播放歌曲后可调整同步":"本曲同步 · "+request.title());timing.setTextSize(16);timing.setPadding(0,pad,0,pad/2);list.addView(timing);
        TextView amount=new TextView(context);amount.setText(LyricsOffset.label(lyricAdvanceMs));amount.setTextSize(20);amount.setGravity(Gravity.CENTER);list.addView(amount);
        SeekBar slider=new SeekBar(context);slider.setMax(2*LyricsOffset.LIMIT_MS/LyricsOffset.STEP_MS);
        slider.setMinimumHeight(pad*3);
        slider.setProgress((lyricAdvanceMs+LyricsOffset.LIMIT_MS)/LyricsOffset.STEP_MS);slider.setEnabled(request!=null);
        slider.setContentDescription("本曲歌词同步，左侧延后，右侧提前，范围正负十秒");list.addView(slider);
        java.util.function.IntConsumer apply=value->{
            if(request==null || !sameSong(request)){closeSettings();return;}
            lyricAdvanceMs=LyricsOffset.clamp(value);offsets.put(request,lyricAdvanceMs);playbackEpoch++;
            amount.setText(LyricsOffset.label(lyricAdvanceMs));slider.setProgress((lyricAdvanceMs+LyricsOffset.LIMIT_MS)/LyricsOffset.STEP_MS);
            visible();
        };
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user)apply.accept(progress*LyricsOffset.STEP_MS-LyricsOffset.LIMIT_MS);}
            @Override public void onStartTrackingTouch(SeekBar bar){}
            @Override public void onStopTrackingTouch(SeekBar bar){}
        });
        LinearLayout timingButtons=new LinearLayout(context);
        String[] names={"延后 0.1 秒","重置","提前 0.1 秒"};int[] deltas={-100,0,100};
        for(int i=0;i<names.length;i++) {int delta=deltas[i];Button button=new Button(context);button.setText(names[i]);button.setEnabled(request!=null);
            button.setOnClickListener(v->apply.accept(delta==0?0:lyricAdvanceMs+delta));timingButtons.addView(button,new LinearLayout.LayoutParams(0,-2,1));}
        list.addView(timingButtons);
        TextView timingNote=new TextView(context);timingNote.setText("歌词慢了就提前，快了就延后。仅记住本曲，不改变播放进度。");timingNote.setTextSize(14);timingNote.setPadding(0,pad/2,0,pad);list.addView(timingNote);
        TextView sourceTitle=new TextView(context);sourceTitle.setText("歌词来源");sourceTitle.setTextSize(16);list.addView(sourceTitle);
        populateSources(context,list,false);
        ScrollView scroll=new ScrollView(context);scroll.addView(list);
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle("歌词设置").setView(scroll).setPositiveButton("完成",null).create();
        settingsDialog=new WeakReference<>(dialog);dialog.show();
    }
    @Override public void choose(Context context) {
        if (song == null) { toast(context, "请先播放歌曲"); return; }
        Song request = song;
        List<Candidate> available = candidates.stream().filter(Candidate::hasLyrics).toList();
        if (!available.isEmpty()) showCandidates(context, request, available);
        else searchDialog(context, request);
    }
    private void searchDialog(Context context, Song request) {
        EditText input = new EditText(context);
        input.setSingleLine(true); input.setText(String.join(" ", request.title(), request.artist()));
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(context).setTitle("搜索歌词").setView(input)
                .setPositiveButton("搜索", (dialog, which) -> manualSearch(context, request, input.getText().toString().strip()))
                .setNeutralButton("清除本曲绑定", (dialog, which) -> {
                    bindings.remove(request);
                    if (sameSong(request)) lookup(true);
                }).setNegativeButton("取消", null).show();
    }
    private void manualSearch(Context context, Song request, String query) {
        if (!sameSong(request)) { toast(context, "歌曲已切换，请重新选词"); return; }
        if (query.isBlank()) { toast(context, "请输入歌名或歌手"); return; }
        List<LyricsSource> enabled = sources.enabled();
        if (enabled.isEmpty()) { toast(context, "请先启用歌词来源"); return; }
        long token = generation.incrementAndGet();
        foreground(true);
        message = "正在搜索候选…"; render();
        WeakReference<Context> owner = new WeakReference<>(context);
        network.execute(() -> {
            List<Candidate> found = new ArrayList<>(); List<String> errors = new ArrayList<>();
            for (LyricsSource source : enabled) {
                if (token != generation.get()) return;
                try { found.addAll(source.search(request, query).stream().filter(Candidate::hasLyrics).toList()); }
                catch (Exception error) { errors.add(source.name() + "：" + error.getMessage()); }
            }
            main.post(() -> {
                if (token != generation.get() || !sameSong(request)) return;
                foreground(false);
                candidates = List.copyOf(found);
                message = found.isEmpty() ? (errors.isEmpty() ? "没有搜索到可用歌词" : String.join("；", errors)) : "请选择对应的演唱版本";
                render();
                Context live = owner.get();
                if (live != null && liveActivity(live) && !found.isEmpty()) showCandidates(live, request, found);
            });
        });
    }
    private void showCandidates(Context context, Song request, List<Candidate> found) {
        String[] labels = found.stream().map(c -> c.label() + "\n" + sources.nameOf(c.sourceId()) + " · " + c.durationMs() / 1000 + "秒 · " + (c.hasWords() ? "逐字" : c.hasSynced() ? "逐行" : "纯文本") + (c.hasTranslation() ? " · 含译文" : "")).toArray(String[]::new);
        new AlertDialog.Builder(context).setTitle("选择歌词 · " + request.title())
                .setItems(labels, (dialog, index) -> {
                    if (!sameSong(request)) { toast(context, "歌曲已切换，请重新选词"); return; }
                    generation.incrementAndGet();
                    foreground(false);
                    selected = found.get(index); bindings.put(request, selected);
                    message = label(selected) + " · 已记住的选择"; render();
                }).setNeutralButton("修改搜索 / 清除绑定", (dialog, index) -> searchDialog(context, request))
                .setNegativeButton("取消", null).show();
    }
    @Override public void sources(Context context) {
        LinearLayout list = new LinearLayout(context); list.setOrientation(LinearLayout.VERTICAL);
        populateSources(context, list);
        new AlertDialog.Builder(context).setTitle("歌词来源 · 按顺序查询").setView(list)
                .setPositiveButton("完成", (dialog, which) -> lookup(true))
                .setOnCancelListener(dialog -> lookup(true)).show();
    }
    private void populateSources(Context context, LinearLayout list) {
        populateSources(context,list,true);
    }
    private void populateSources(Context context,LinearLayout list,boolean clear) {
        if(clear)list.removeAllViews();
        LinearLayout sourceList=new LinearLayout(context);sourceList.setOrientation(LinearLayout.VERTICAL);list.addView(sourceList);
        populateSourceRows(context,sourceList);
    }
    private void populateSourceRows(Context context,LinearLayout list) {
        list.removeAllViews();
        List<LyricsSource> ordered = sources.ordered();
        for (int i = 0; i < ordered.size(); i++) {
            LyricsSource source = ordered.get(i);
            LinearLayout row = new LinearLayout(context);
            CheckBox enabled = new CheckBox(context);
            enabled.setText(String.format(java.util.Locale.getDefault(), "%d. %s", i + 1, source.name())); enabled.setChecked(sources.isEnabled(source.id()));
            enabled.setOnCheckedChangeListener((button, checked) -> {sources.setEnabled(source.id(), checked);updatePreload();lookup(true);});
            row.addView(enabled, new LinearLayout.LayoutParams(0, -2, 1));
            Button up = new Button(context); up.setText("上移"); up.setEnabled(i > 0);
            up.setOnClickListener(view -> { sources.moveUp(source.id());updatePreload();populateSourceRows(context,list);lookup(true); });
            row.addView(up); list.addView(row);
        }
        CheckBox preload=new CheckBox(context);preload.setText("预载接下来 3 首歌词");preload.setChecked(sources.preloadEnabled());
        preload.setOnCheckedChangeListener((button,checked)->{sources.setPreloadEnabled(checked);updatePreload();render();});
        list.addView(preload);
        TextView preloadState=new TextView(context);preloadState.setText(preloadLabel());preloadState.setTextSize(14);
        preloadStatus=new WeakReference<>(preloadState);
        list.addView(preloadState);
        TextView note = new TextView(context);
        note.setText("自动比较已启用来源：先匹配歌曲版本、专辑和时长，再优先逐字、逐行及译文；同等条件按来源次序。手动绑定保留。查询会发送歌名、歌手、专辑和时长。");
        int pad = Math.round(16 * context.getResources().getDisplayMetrics().density);
        note.setPadding(pad, pad / 2, pad, pad); note.setTextSize(14); list.addView(note);
    }
    private boolean sameSong(Song request) { return song != null && song.key().equals(request.key()); }
    private static boolean liveActivity(Context context) {
        while (context instanceof android.content.ContextWrapper wrapper) {
            if (context instanceof android.app.Activity activity) return !activity.isFinishing() && !activity.isDestroyed();
            context = wrapper.getBaseContext();
        }
        return false;
    }
    private static void toast(Context context, String message) { Toast.makeText(context, message, Toast.LENGTH_LONG).show(); }
}
