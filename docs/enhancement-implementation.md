# 0.3.0 歌词增强实装

> 本页保留各版本的实现与证据；当前用法见 [使用说明](usage.md)，构建见 [构建说明](building.md)。

2026-10-18，用户确认真实 AMLL Demo 后授权实装。代码、电脑验证与 APK 打包已完成；手机安装和验收由用户执行。

## 本次实现

- 保留 9.41.52（94152240）/ libxposed API 102 Hook、原生 engagement-panel、来源设置和旧绑定入口。
- `TimedLyrics` 统一真实毫秒行／字词时间和来源译文；LRC 不虚构字词时间，QRC / YRC 解析校验次序及范围。
- 顺序查询，逐字 > 逐行 > 纯文本；同等级按原来源次序，歧义仍人工确认。可靠逐行结果先显示，再继续找逐字。
- QQ：数字 ID 取 LRC / 译文，加 QRC POST、QQ 非标准 DES 三段解密、zlib 与格式提取。增强失败仍保留可用 LRC，并显示具体原因。
- 网易云：搜索兼容 ar/al/dt 和 artists/album/duration，按数字 ID 取 LRC、YRC、ytlrc/tlyric；新增来源默认启用、追加到旧次序后。
- 来源译文只在近邻时间（最多 1000 ms）、不复用同一译文条目的条件下对齐；未匹配则留空，不按数组下标强配，不跨来源拼接，不调用机器翻译。
- 旧绑定先显示缓存，只向原来源按原 ID 补取。升级字段追加到原 JSON，保存真实逐行投影供旧读者使用；补取失败、不完整或迟到不会清除旧增强数据或改绑。
- APK 内嵌 AMLL Core 0.6.0；WebView 禁止网络、文件、内容 URI 和导航，只有固定本地页能访问 ready / failed / seek 桥。数据 serial 和页面 generation 隔离旧歌、旧页面的回调。
- Native MediaSession 每 150 ms 给绝对位置锚点、播放／暂停、速度与跳转代次；渲染帧不累加媒体时间。倍速同步 Web Animations 播放速率；隐藏页停帧，拆页销毁 WebView、重进再加载。
- 保留原生选词／来源／重试，增加持久化译文开关。AMLL 加载或渲染失败时明确提示并用原生逐行显示，重试可重新初始化。

## 电脑证据

- `build.ps1` 成功：**50 个 JUnit 测试**、Android Lint 和 APK 编译通过。Lint 仅有固定 targetSdk 36 的 OldTargetApi 提示，无错误。
- `demo` 两组 Node 测试通过：样例与 AMLL 适配、数据不可变、桥接锚点、暂停、1.5x / 2x、跳转、旧歌代次及成品脚本／离线资源检查。
- 实际 APK 渲染 HTML 经 `demo/host-preview.html` 的电脑控制条检查：逐字／逐行（不伪造填色）、空数据与换歌清旧词、旧 serial 进度拒绝、译文开关、倍速、暂停遮罩冻结和三秒后归位。控制台无警告／错误。测试控制条不打入 APK；这不是 Android 真机测试。
- QQ 生产 Java `refresh()` 按 ID `317199253` 联网：97 行 QRC、766 个字词，解密解析成功；该中文歌曲没有译文。未把真实歌词纳入测试或发布源文件。
- 网易云生产 Java 搜索和取词：`1901371647` 返回 57 行 YRC；Mili / Through Patches of Violet 返回来源译文，但实测候选没有 YRC，按逐行显示。先前 PowerShell TLS 失败未出现在生产 Java 传输中；没有关闭证书校验。
- `dist/ytm-lyrics-0.3.0-debug.apk`：versionCode 3，APK v2 签名验证通过，包内存在 AMLL HTML、第三方许可及 modern Xposed 入口／作用域元数据。

## 交付与尚待验收

安装包：`dist/ytm-lyrics-0.3.0-debug.apk`。来源、字词和缓存测试用例位于 `core/src/test`、`app/src/test`；桥接源码和构建在 `demo/src`、`demo/build.mjs`。源码包排除签名私钥、设备资料、取回的真实歌词、node_modules 和构建缓存。

**本版没有助手真机验收。** 用户覆盖安装并重启 YTM 后检查：

1. 旧绑定立即显示缓存，随后能按原来源补取；同名／剪辑版不被自动改绑。
2. QQ 与网易云逐字、暂停、译文开关、点击跳转；来源无逐字／译文时正确显示可用等级。
3. 关闭、返回、重进歌词页；关闭来源后恢复原生内容；若 WebView 出错，提示原因并能逐行显示／重试。

此处完成条件为代码和电脑验证、可安装 APK 交付；手机结果待用户反馈后追加，不沿用 0.1.0 / 0.2.0 的旧真机结论。

## 0.3.1：修复本地主页面被拦截

用户反馈 0.3.0 内嵌页停在 `data:text/html;charset=utf-8;base64,` 的 `net::ERR_HTTP_RESPONSE_CODE_FAILURE`。原 `shouldInterceptRequest` 对所有请求返回 HTTP 403，也会拦截 `loadDataWithBaseURL` 产生的本地 `data:` 主页面；原错误处理只覆盖 `onReceivedError`，缺少独立的 `onReceivedHttpError`。电脑浏览器渲染验证未覆盖 Android WebView 的此加载路径。

0.3.1 改用 `loadUrl` 加载固定地址 `https://ytm-lyrics.invalid/lyrics.html`，由本地拦截器直接返回 APK 的 UTF-8 HTML（HTTP 200）。仅允许该地址的主框架 GET 请求，其余地址、方法及子框架仍返回 403，网络／文件／内容 URI 访问仍禁用。新增主框架 HTTP 错误处理，失败时明确提示并回退原生逐行，不把 WebView 错误页当作已加载内容。

回归测试覆盖本地主页面 200 与原文完整、其他地址／方法／子框架阻断、实际 APK HTML 桥接和离线策略。`build.ps1` 成功，合计 53 个 JUnit 测试零失败，Lint 无错误；APK v2 签名验证通过。修复版安装包为 `dist/ytm-lyrics-0.3.1-debug.apk`（versionCode 4），保留原签名供覆盖更新；手机加载恢复情况待用户验证。

加载路径依据：[Android 本地内容加载指南](https://developer.android.com/develop/ui/views/layout/webapps/load-local-content)；[`WebViewClient` 文档](https://developer.android.com/reference/android/webkit/WebViewClient)明确拦截回调也适用于 `data:`，且 HTTP 400 及以上错误使用独立回调。

## 0.3.2：译文放大与自动择优

2026-10-22，用户要求“翻译的字调大一点，自动选择最合适的歌词”。插件与独立预览均使用 `max(18px,.62em)` 译文字号（原插件为 `max(14px,.46em)`，原预览为 `max(14px,.42em)`），行高为 1.5，保留 AMLL 自身测量与换行布局。

匹配先要求歌名、歌手和不超过 2 秒的时长差。展示标签 Official Music Video / Official Audio / Lyrics 和歌手 - Topic 不视为演唱版本；Live / Remix / Acoustic / Remastered / Instrumental 等标记仍参与匹配。可靠候选先按同专辑、时长差是否在 1 秒内排序，再比较逐字／逐行／纯文本、非空歌词行的译文覆盖率、绝对时长差；最后按来源与搜索次序稳定选择。

这是对 0.3.0 自动选择约定的明确更新：不再将“多个可靠候选”全部留给用户，也不在首个逐字结果处停止。所有已启用来源都参与比较，当前最佳同步结果先显示，后续有更合适的结果才更新；来源失败保留已找到的最佳结果，切歌继续丢弃旧请求。手动绑定的缓存优先、原来源原 ID 增强、来源启停和排序均保留。

新增自动选择回归测试覆盖跨来源译文优选、译文覆盖率、专辑与时长优先、秒级时长取整、逐字优先、版本／歌手／短 MV 不误配、展示标签及稳定排序。无可靠匹配仍保留原生并提供手动选词，不自动保存为手动绑定。

电脑检查：60 个 JUnit 测试零失败，两组 Node 渲染／时间轴测试通过，Lint 无错误（保留固定 targetSdk 的旧提示），APK v2 签名验证通过。实际生产渲染页检查了放大字号、译文关／开重新布局及 390 像素窄屏，窄屏译文实际计算字号为 18 px，无横向溢出；独立预览也检查了实际画面。安装包为 `dist/ytm-lyrics-0.3.2-debug.apk`（versionCode 5），手机验收仍由用户执行。用户可选一首未手动绑定的歌检查自动选择；已绑定歌曲若要重新自动选择，用“选词 → 修改搜索 / 清除绑定 → 清除本曲绑定”。

## 0.3.3：译文随当前行亮起

用户要求译文也亮起来。插件与预览复用 AMLL Core 0.6.0 的 `FmKaba_active` 行状态：当前行译文 opacity 从默认 0.3 过渡到 1，进入时过渡 160 ms；退出后恢复上游淡出。不增加时间轴或伪造译文逐字时间，字号、自动选择、来源及绑定规则不变。

浏览器实际检查了预览和生产渲染页：7.5 秒时第二行译文 opacity 为 1，其余行稳定为 0.3；暂停保持当前高亮，向后跳到 2 秒时第一行译文亮起；译文关闭后清空、重开后恢复正确高亮，控制台无错误。两组 Node 测试增加成品 CSS 回归检查并通过；Android 构建、既有 60 项 JUnit 检查和 Lint 通过。安装包为 `dist/ytm-lyrics-0.3.3-debug.apk`（versionCode 6），真机效果由用户验证。

## 0.4.0：根据播放队列滚动预载

2026-10-22，用户要求根据播放列表预载歌词。默认按 YTM 当前队列提前准备接下来 3 首，切歌、换序及随机播放后更新计划；不一次抓取整个歌单。来源对话框新增持久化开关“预载接下来 3 首歌词”和动态进度，命中时歌词状态显示“已命中播放列表缓存”。

### 队列来自实际发布顺序

对用户提供的 9.41.52 APK 做静态定位：`heo.j()` 是该版本调用 `MediaSession.setQueue` 的入口，会发布队列窗口（默认上限 25 项），并随换序／随机状态更新。平台队列条目缺少视频 ID、真实时长和完整专辑；模块在此方法之后，通过原生队列模型的 `m()` 队列 ID 与平台窗口对应，使用 `hxg.h()/g()` 的歌名／歌手和 `hxj.t()` 的稳定视频 ID，不扫描界面文字，不请求远程歌单或账号接口。具体反射契约限于 versionCode 94152240；接缝失败保留当前歌曲查询并记录原因。

`QueuePlan` 优先按活动队列 ID 定位；没有该 ID 时使用媒体 ID，或唯一的歌名／歌手对应，不猜测第一项就是当前歌曲。`acaf.n()` 的基本媒体元数据未设置标准 MEDIA_ID，因此增加了仅用于自动缓存的队列身份映射；原有手动绑定键不改变。静态定位材料保留在本机忽略目录 `.analysis/`，不随源码分发。平台队列含义参见 [Android MediaController.getQueue](https://developer.android.com/reference/android/media/session/MediaController#getQueue())。

### 请求和缓存边界

- 一个独立低优先级后台工作线程，最多安排 3 首。当前歌曲查询／绑定增强和人工搜索期间暂停预载；来源请求可能在返回后才观察到取消，但迟到结果不会写入新计划。
- 重复发布相同计划不重复取词；切换队列、来源次序／启停或关闭预载会作废旧计划。失败有 5 分钟冷却，后续计划变化可再尝试，不循环重试。
- 未知队列时长保持 0，仅保存候选。切歌后使用实际播放元数据及既有匹配规则重新择优；缓存没有可靠结果时继续按实际信息联网查询，不把候选缓存当作已完成的歌曲匹配。
- `dev.ytmlyrics.preload` 独立保存自动候选池，保留 24 小时，最多 30 首、候选 JSON 数据总量上限 4 MiB，超限先淘汰较早记录；来源策略不符不复用。成功的当前歌曲查询也进入同一缓存。空结果不擦掉已有可用缓存。
- 手动绑定仍先读取，再按原来源和歌曲 ID 增强；自动缓存不创建、替换或改写手动绑定。禁用来源不会经缓存重新启用。`BindingStore` 提取共用 JSON 编解码，旧字段、真实逐行投影及增强缓存兼容行为保留。

### 检查与手机验收

76 个 JUnit 测试通过，覆盖串行限量、重复队列、换序取消、前台优先、来源策略变化、失败冷却、未知时长择优、媒体 ID 缺失映射、缓存期限／容量、旧绑定及开关持久化。集成测试使用模拟原生队列条目和测试来源，贯通队列规划、Resolver、持久缓存及切歌择优，不作为 Android Hook 真机证据。渲染资源未改变，沿用 0.3.3 的有效检查。

0.4.0（versionCode 7）已完成 Android 编译和 Lint 检查。手机由用户覆盖安装后确认：来源页出现预载进度；等待预载后切到下一首出现缓存命中；随机／换序后准备新的后续歌曲；关闭开关后不启动新预载。若一直显示“等待播放队列”或队列读取失败，需要该次用户实测反馈才能判断当前服务器配置下的接缝表现。

## 0.4.1：正文替换与限次恢复

2026-10-22，用户要求不再覆盖原生歌词组件，并确认 WebView 崩溃后需要限次自动恢复。适配范围仍为 Android 16 / YTM 9.41.52（94152240）/ modern libxposed API 102，来源、绑定、预载和浏览器渲染资源不改。

### 在原生呈现入口换正文

对已提供 APK 的 `vke.c()`、`vlf` 的呈现调用和旧 `TabbedView.C()` 做静态复核：新版呈现器接受 `View`；原生标题及关闭由面板控制器管理，正文由 `c()` 提供。新版只在原来识别的歌词 panel ID 范围，返回稳定的 `LyricsPanel` 替代原始 LoadingFrameLayout；原生 `vke.h` 字段、其内容及加载事件继续归宿主所有。旧 tab 则在 `C(afop, View, View)` 的 content 参数中提供替换 View，再让宿主构造原本的不可变 `jfr`，不修改 final 字段或 pager 的条目模型。

`LyricsPanel` 的工具栏／状态与正文是垂直排版，正文槽通过 `ExclusiveContent` 先移出旧对象，再挂入新对象。自定义结果存在时只挂模块显示容器；无结果时挂回原始的整个 LoadingFrameLayout，它已接收期间的原生加载更新。移除了 `bringToFront`、原生子项顶部加 100dp 和遮蔽无障碍子树的逻辑；不再把模块加到原生正文根节点上。普通 loading/error 更新只改变原生对象内部，不会把自定义正文盖住。独立无词面板原有的 content 状态规则保留，评论／相关推荐未扩大作用域。

### 恢复只针对退出的渲染进程

`RendererSession` 管理实际 WebView 的创建、销毁、可见性和恢复预算。`onRenderProcessGone` 仅处理回调给出的本模块 WebView，立即清除引用、从树中移除并销毁，再返回已处理；没有操作 YTM 其他 WebView，也不会重启 YTM。处理依据：[Android WebViewClient.onRenderProcessGone](https://developer.android.com/reference/android/webkit/WebViewClient#onRenderProcessGone(android.webkit.WebView,%20android.webkit.RenderProcessGoneDetail))。

- 最多两次自动重建，分别等待 1000 ms、3000 ms。ready 不补回次数，避免“启动成功后立刻再崩”的循环；手动“重试”可重新开始预算。
- 隐藏页取消等待、不创建新的 WebView；拆页销毁现有页，已消耗的预算不重置。回到同一面板后才恢复未用完的计划，取消过的旧定时回调不能生效。
- 重建／不可用期间显示模块逐行歌词。重建成功清除恢复提示、切回 AMLL；其间继续保存最新 serial、歌曲、译文开关及实际 MediaSession 播放锚点，ready 后重新发送。旧页面的 ready/seek/failed 仍由 pageGeneration 和 serial 隔离。
- 初始化、HTTP 或脚本错误保留具体原因，只逐行回退，等待人工重试；不会把持久错误做成自动加载循环。fallback 译文也随开关更新，不保留旧布局。

### 证据与交付

85 项 JUnit 测试零失败（core 30、app 55）；新增的 9 项测试使用生产 `ExclusiveContent`／`RendererSession`，覆盖互斥挂载、原生对象更新后恢复、同对象重复展示与挂载重入、两次预算及退避、ready 后再崩、隐藏和拆页取消、旧定时任务、手动重试、非进程错误。Android 编译和 Lint 通过，保留既有 OldTargetApi 警告；APK v2 签名验证通过。HTML/AMLL 源码未改变，沿用其既有电脑浏览器检查。

交付：`dist/ytm-lyrics-0.4.1-debug.apk`（versionCode 8）和对应源码 ZIP，沿用原签名。手机验收由用户完成：有词／无词入口、返回／关闭／重进，原生正文无重影／不抢触摸，无自定义匹配时原生加载／错误／正文恢复，以及真实渲染进程退出后的逐行回退、限次恢复和正确播放位置。单元测试不作为 Android 真实视图或崩溃回调已经验收的证据。

## 0.5.0：手动偏移、按钮可用与单一设置入口

2026-10-23，用户截图显示原生标题、模块操作栏和来源状态行三层堆叠，并要求手动歌词偏移、播放器歌词按钮始终激活和菜单合并。截图作为用户提供的布局证据，不扩展为同步或恢复功能的真机验收。

### 一个原生标题，设置集中管理

在固定 94152240 APK 的 `vly.c(Context)` 之后，歌词 panel 的现有标题 `J` 增加 ⋯ 提示并打开统一设置；不在原生按索引管理的 action strip 添加子项，不改变关闭和返回。标题保留宿主文字与样式，设置 48dp 最小触摸高度和可聚焦／无障碍入口；若标题原有点击动作，则保留它并使用模块内单个设置按钮。标题复用到非歌词面板时恢复原来的可访问属性。

正常自定义歌词显示时，重复“歌词”标题、四个常驻按钮和整行来源详情均移除。来源／候选状态、选择歌词、重新获取、来源启停／排序、译文、预载与本曲同步放到同一滚动设置内。查询无结果或渲染恢复期间仍在正文旁显示必要状态。切歌会关闭旧设置，来源设置即时生效。

### 偏移不更改歌曲和歌词数据

`LyricsOffset` 约定正值提前、负值延后，范围 ±10000 ms，UI 以 100 ms 调节。`OffsetStore` 使用原有 `Song.key()` 作为歌曲身份，独立保存到 `dev.ytmlyrics.offsets`，不写入绑定或预载池；无媒体 ID 时沿用歌名／歌手／时长，避免把短 MV 与完整曲混用。

桥接保留真实媒体 `positionMs`，额外传 `offsetMs`；显示时钟为媒体锚点外推后加偏移，可在曲首为负。偏移变化推进 epoch，因此微调 100 ms 也立即重新定位；来源字词时间和译文时间不变。译文重建以媒体时间重新锚定，只在绘制时加偏移。native LRC 回退用同一显示时间；点击行将原始行时间减去偏移，再按实际歌曲时长截断。原生 YTM 回退正文不修改。

### 只激活歌词按钮

绑定实际 `com.facebook.litho.ComponentHost` 歌词无障碍标签（中文或当前宿主语言），将 enabled／clickable 保持 true、view alpha 保持 1；不改整个 action bar 的缺元数据 protobuf 标志，也不假造歌词 endpoint。通过实际组件继承链取得属性方法，避免 override 绕过基础 View Hook。没有原生点击处理器时直接尝试已注册的自定义原生面板；原生有词路线与无词反馈路线继续保留。

额外复核固定 APK：ComponentHost 的 `setContentDescription` 只写自身字段，不调用 View 的实现，`setAlpha` 也有 override，因此 Hook 取实际继承方法。仅启用 View 仍可能保留灰色绘制；新增 `LyricsActionAppearance` 在匹配的 ComponentHost 绘制范围内，让 Litho 文本叶子 `fsd` 和位图／矢量图标使用宿主 `ytTextPrimary` 激活色。底色与原生点击反馈不重绘，不修改复用 drawable 或缓存 TextPaint；临时 Canvas layer 在 finally 中恢复，离开范围后其他按钮不受着色。

`LyricsActionBinding` 记录宿主对这些属性的最后请求，在换标签、复用为评论等其他按钮前恢复，避免“歌词按钮常亮”扩散到其他 action。标题、按钮 Hook 的实际 Android 呈现仍需用户手机验证。

### 电脑证据与验收

97 个 JUnit 测试通过（core 33、app 64），覆盖偏移方向／边界／跳转反算、歌曲持久化、JSON 中媒体位置不变、零偏移兼容、按钮复用恢复，以及绘制范围的嵌套／异常／跨线程隔离；两组 Node 测试通过，覆盖偏移后的暂停／倍速、微调 seek 检测、负时间及译文重新锚定。实际生产 HTML 的电脑浏览器检查：3.80 秒时 +500 ms 激活第二行、-500 ms 激活第一行，媒体进度不动；关开译文保持正确活动行，曲首延后没有活动行，点击首行落到媒体 0.50 秒。控制台无警告／错误。

Android 编译和 Lint 完成（0 errors、1 项既有 OldTargetApi 提示），APK v2 签名验证通过。360 像素电脑视口检查了带偏移的逐字／译文换行，页面没有横向裁切。交付 `dist/ytm-lyrics-0.5.0-debug.apk`（versionCode 9）与对应源码包；源码包排除设备资料、用户截图和签名私钥。用户覆盖安装后验收标题合并、按钮外观和点击、偏移持久化及实际音频对齐；既有原生回退、播放列表预载、WebView 两次恢复仍为回归项。

## 0.6.0：封面驱动的 AMLL 背景

### 封面和歌词各自管理身份

固定 94152240 APK 的 `acaf.n()` 会将当前封面写入 `android.media.metadata.ALBUM_ART`。运行时优先读取该位图，其次 ART、DISPLAY_ICON；不请求封面网络 URL。`ArtworkState` 以 Song.key 和位图实例去重，使用独立递增 revision：来源查词、手动绑定和预载改变歌词 generation 不会取消封面；切歌、清空和同曲换图则淘汰旧的编码结果。换歌先发空帧，避免旧图遗留；同曲补图只替换背景，不重建歌词。

`ArtworkEncoder` 在单独工作线程把最长边限制为 256px，以 JPEG 82 编码，并取采样平均色的 30% 亮度作为标题／正文顶部衔接色。宿主原图只借用；硬件位图先复制为可读副本，仅回收模块自建对象。封面只在内存保留，不进入绑定和预载；播放消息仍只有媒体锚点、速率、偏移与 epoch。WebView ready 或进程恢复时，重发最新 artwork、歌词与播放状态。

### AMLL 背景不接管歌词时钟

采用当前已固定的 core 0.6.0 `MeshGradientRenderer`，设置 renderScale 0.35、FPS 30、flowSpeed 2。原生传来的 data JPEG 解码为 Image 元素后交给 setAlbum，避免上游字符串资源路径使用 fetch；CSP 仅增加 img-src data:，connect-src、文件和 content URI 限制不变。构建许可收集改为独立 Demo 与生产渲染 bundle 的实际依赖并集。

背景 Canvas 置于歌词下层，指针与无障碍不参与交互；底层保留静态模糊封面，顶层暗色遮罩与封面暗色渐隐保证文字可读。切换封面利用 AMLL 自带混合过渡。暂停或减少动态效果时 flowSpeed 立即归零、staticMode 完成淡入后停止请求帧；隐藏直接 pause。原生可见标志与 document.hidden 分开，网页的可见变化不能擅自恢复已被宿主隐藏的页面。

背景加载、图片解码或真实 WebGL context lost 失败时只释放背景实例，本次页面后续保持静态回退，不重启 WebView或禁用歌词；故障记录到 YtmLyrics 日志。重新创建页面时重新尝试背景。没有封面时清空静态图与 AMLL 纹理，显示中性暗底。新图片解码请求、纹理请求及销毁均有版本隔离。

原生 `vly.c(Context)` 的歌词标题根视图跟随当前封面暗色；重绑定前恢复记录的原背景，再让宿主绑定，非歌词复用不保留模块颜色。着色使用新 ColorDrawable，不修改原 Drawable，返回原生正文时恢复原背景。既有关闭／返回／菜单结构不变。

### 电脑证据与交付

103 项 JUnit（core 33、app 70）零失败／错误，三组 Node 检查通过；Android 编译和 Lint 成功（0 errors、1 项既有 OldTargetApi 提示）。新增测试覆盖封面去重、切歌／同曲补图／清空、旧结果淘汰、JSON、暂停／隐藏／减少动态效果、失败静态回退及销毁。

实际生产 HTML 的电脑浏览器检查：冷暖自绘封面均由 WebGL 绘制；播放 flowing、隐藏 hidden、暂停 static。暂停后两次截图的正文区域像素完全相同。无封面显示中性暗底；损坏 JPEG 进入 fallback；用 WEBGL_lose_context 触发真实上下文丢失后，静态底图保留、歌词继续播放，控制台无 error。桌面软件图形环境有一条上游 EXT_color_buffer_float 不支持的提示，正常显示不受影响。360px 窄屏检查了原文、译文和顶部衔接色。电脑预览中的“原生标题衔接色示意”仅为色条，不作为真实 Android 标题已验证的证据。

交付 `dist/ytm-lyrics-0.6.0-debug.apk`（versionCode 10）与对应源码包。用户负责覆盖安装后检查真实封面输入、切歌过渡、原生标题配色、隐藏／暂停与实际手机流畅度、发热；歌词偏移、菜单、预载和正文／进程恢复作为回归项。
