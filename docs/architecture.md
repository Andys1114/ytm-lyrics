# 架构与行为契约

## 模块边界

```text
YTM MediaSession / 原生歌词呈现入口
             │
     app/hook/LyricsRuntime
       ├── SourceRegistry → LyricsSource → Resolver（core）
       ├── BindingStore / PreloadCache / OffsetStore
       ├── QueueAdapter → PlaylistPreloader（core）
       └── LyricsPanel
             ├── 原生正文：无自定义结果时回退
             ├── 原生逐行文本：WebView 不可用时回退
             └── AmlLyricsView → 离线 HTML / AMLL
                                  ├── 媒体锚点 + 显示偏移
                                  └── 独立封面 revision / 网格背景
```

`core` 不依赖 Android 或 Xposed，负责 `Song`、`Candidate`、`TimedLyrics`、匹配、时间轴、偏移和预载策略。`app/source` 负责 HTTP 协议、解析、序列化与 YTM 私有存储；`app/hook` 负责固定宿主接缝；`app/ui` 负责正文互斥挂载及 WebView 恢复。

## 歌曲、候选与异步结果

媒体元数据建立当前 `Song`。来源查询带递增 generation，自动查询、人工搜索和补取的迟到结果不得覆盖另一首歌。`Song.key()` 同时承载旧绑定身份；当前没有媒体 ID 时保留原有歌名／歌手／时长语义。

手动绑定先显示缓存，再按原来源和歌曲 ID 补取逐字／译文；补取不重新搜索或替换演唱版本。没有绑定时依次查询启用来源，可靠结果可先显示，最终比较全部可靠候选。比较顺序见 [使用说明](usage.md#来源选择与旧绑定)。

预载使用当前实际发布队列的后续三首候选池。缺少时长的队列项不提前视为可靠匹配；切歌后用真实播放信息重新择优。队列缓存身份不改变手动绑定键。

## 媒体时间与显示时间

所有源时间以毫秒保存，逐字数据必须来自真实字／词起止时间。只有逐行数据时不等分伪造字词时间。

播放位置由媒体锚点、更新时间和速率外推，暂停使用固定位置。150ms 原生采样通过 serial／epoch 同步到浏览器；动画帧绘制绝对位置，不以帧间差累积播放时间。

显示时间 = 媒体时间 + 本曲提前量，正值提前，范围 ±10000ms，曲首允许为负。点击原始行时间时减去提前量后跳转真实媒体位置。译文重新布局保存媒体锚点，不重复叠加偏移。

## 原生正文与恢复

9.41.52 新版入口替换 `vke.c()` 的内容返回值；旧版在 `TabbedView.C(afop, View, View)` 构造 pager 条目前提供替换正文。原生 LoadingFrameLayout 保留并继续接收原生模型更新，只有回退时挂回显示树；不叠加覆盖层或改其他面板。

现代标题复用原生“歌词 ⋯”作为设置入口；复用为其他面板前恢复修改的属性与背景。播放器歌词 action 的激活仅限匹配的 ComponentHost，复用前恢复宿主请求的状态。

WebView 渲染进程退出后先退到模块逐行显示，最多自动重建两次，等待 1 秒／3 秒。ready 不补充预算，隐藏／拆页取消等待，手动重试可重置。普通脚本或加载错误不循环重启。

## 背景独立于歌词

封面来自媒体会话的 ALBUM_ART／ART／DISPLAY_ICON。独立 worker 缩图至最长边 256px，仅在变化时传递本地 data JPEG；ArtworkState 的 revision 独立于来源查询，切歌和补图的旧结果会失效。

浏览器用解码后的 Image 元素调用 AMLL MeshGradientRenderer，保持 `connect-src 'none'`；CSP 只为封面开放 `img-src data:`。背景最高 30FPS、renderScale 0.35，暂停／减少动态效果停止流动，隐藏直接暂停。图片／图形上下文失败只释放背景并使用静态底图，不消耗 WebView 恢复预算，也不影响歌词时间轴。

## 适配依据与验证

宿主混淆类与资源 ID 固定为 versionCode 94152240。新增版本须使用有权取得的 APK 本机定位，提交映射依据和自写测试样例，不分发宿主文件或反编译源码。已有细节见 [历史实装记录](enhancement-implementation.md)，电脑证据与手机反馈分别记录。
