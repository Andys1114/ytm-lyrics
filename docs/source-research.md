# GitHub 歌词源实现筛选

> 本页是 2026-10-18 的筛选记录；QQ／网易云均已接入当前版本，当前行为见 [使用说明](usage.md)。

2026-10-18，按现有 Java / Android 16 Xposed 插件的需要检查源码。目标是复用 QQ / 网易取词协议，不替换已跑通的 YTM 页面和播放同步。

## 首选：Spotify_lyric 的来源层

[absswds/Spotify_lyric](https://github.com/absswds/Spotify_lyric)，检查 revision `827a0f483e7808b4b749adbffdd9227ae3035b89`，项目许可 Apache-2.0。

- Android Kotlin 实现，QQ、网易、酷狗和 LRCLIB 分成独立来源类；来源按歌名、歌手查询，所读的 QQ / 网易类不依赖 Spotify 登录。
- `lyrics/qqmusic/QQMusicLyricsSource.kt`：搜索后使用数字 `songid` 作为 `musicid` 取 LRC，Base64 解码。文件说明旧的字母 `songmid` 路径会返回 1101；这只是上游实现记录，当前网络尚未实测确认。
- `lyrics/netease/NeteaseLyricsSource.kt`：HTTP 搜索、取词；兼容两种元数据字段形状，支持 LRC / YRC。首版只需取 LRC，不照搬逐字优先策略。
- 项目注明请求参数及部分解密器参考 / 移植自 Lyricify-Lyrics-Helper。最有价值的是已经落到 Android 上的协议实现，而不是其 Spotify 或通知 UI。
- 不直接复制整套调度器：其代码并行取候选、部分失败转空列表；本项目需要保持来源顺序查询、错误原因可见、保守匹配与人工绑定。

## 备选：Cadence 独立 JVM 库

[FPSMasterTeam/Cadence](https://github.com/FPSMasterTeam/Cadence)，检查 revision `a35ba86e6f942672d852c0ea2d0ff4abaf0c8a2c`，项目许可 MIT。

- 独立 Kotlin JVM 库，Java 8 字节码，Gson 与 Kotlin 标准库运行依赖；没有 Minecraft 依赖。
- `NeteaseMusicApi.kt`、`QQMusicApi.kt` 已有搜索、歌词、错误分类和加密 / 签名；还带播放地址、登录、歌单等本项目不需要的功能。
- QQ 歌词当前使用 `songmid` 路径，与首选参考存在差异，应以实测接口行为决定采用哪条请求，而非按 README 判断可用。
- 当前模块为纯 Java，不为只用几条请求自动引入完整 SDK；Java 8 JVM 兼容也不等于已通过本模块的 Android 真机验收。

## 补充参考：Capsulyric

[FrancoGiudans/Capsulyric](https://github.com/FrancoGiudans/Capsulyric)，检查 revision `dd020bcc39d129695d2064fda405870d96c6e58f`，项目许可 GPL-3.0。

`THIRD_PARTY_NOTICES.md` 明确列出 Lyricify C# → Kotlin 的网易、QQ、酷狗来源、加密和解析器移植；这些移植文件同时带其 GPL-3.0 文件头。适合核对协议、模型与归属，不把整套通知 / 超级岛功能带进 YTM 模块；采用代码前按文件核对适用许可。

## 决定

优先参考 Spotify_lyric 的 QQ / 网易 Android 来源层，Cadence 交叉核对协议；无需从 C# 整库重新开始，也不引入 .NET 运行时或服务器。下一步最小验证为 QQ 搜索 → 数字歌曲 ID → LRC，再接现有 `LyricsSource`。LRCLIB、来源优先级、匹配、绑定、原生 Hook 和同步语义保持不变。

以上是 GitHub 源码筛选阶段的记录。用户随后确认实施，0.2.0 已新增 Java QQ 来源，并用实际生产代码验证数字 ID 取词和时间轴；没有运行或整体引入这些上游 App。网易云接口仍未实测。具体实现、测试与真机验收状态见 [设计记录](design.md)。
