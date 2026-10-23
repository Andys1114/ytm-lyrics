# YTM Lyrics

在 YouTube Music 原生歌词页显示自定义来源歌词，补足缺词歌曲，也可替换已有原生歌词。

使用 **modern libxposed API 102**，以 **AMLL** 呈现逐字填色、弹簧滚动和封面流动背景。当前版本 **0.6.0**。

## 功能

- **三个来源**：LRCLIB、QQ 音乐、网易云音乐，可启停、排序和手动选词。
- **真实同步**：支持 LRC 逐行、QQ QRC／网易云 YRC 逐字和来源自带译文，不伪造字词时间。
- **自动择优**：先匹配歌曲版本、歌手、专辑和时长，再比较逐字／逐行及译文；手动绑定优先。
- **AMLL 显示**：当前行与译文高亮、点击歌词跳转、手动滚动三秒后跟随；封面驱动背景，暂停／隐藏时停止流动。
- **本曲同步**：每首歌独立保存 ±10 秒偏移，以 0.1 秒微调，不改变音频进度。
- **队列预载**：后台准备接下来三首歌词，当前歌曲查询优先。
- **原生集成**：替换歌词正文，保留 YTM 的标题、关闭和返回；无可用结果时恢复原生正文。WebView 进程异常最多自动恢复两次。

## 支持环境

| 项目 | 当前目标 |
| --- | --- |
| Android | Android 16 / API 36，root |
| YouTube Music | **9.41.52 / versionCode 94152240** |
| 框架 | 支持 modern libxposed API 102 的框架；已有 LSPosed 2.2.1 环境记录 |
| 模块 | `dev.ytmlyrics`，仅作用于 `com.google.android.apps.youtube.music` |

Hook 与宿主混淆类名按上述 YTM 版本适配。其他版本不注入歌词页面；升级 YTM 前需重新适配。模块没有独立启动界面，设置入口在 YTM 歌词页。

## 安装与使用

1. 从 [Releases](https://github.com/Andys1114/ytm-lyrics/releases) 下载维护者发布的 APK；尚未发布时按 [构建说明](docs/building.md) 自行构建。
2. 安装模块，在框架管理器中启用，作用域仅选择 YouTube Music。
3. 完全退出并重新打开 YTM，播放歌曲并打开“歌词”。
4. 点原生标题 **歌词 ⋯** 进入统一设置，管理候选、来源、译文、预载、同步偏移和重新获取。旧 tab／其他标题样式下显示一个“歌词设置”按钮。

更新时使用同一签名的 APK 覆盖安装。不要为更新清除 YTM 数据，否则绑定、来源设置、缓存与本曲偏移会一并删除。

详细行为与限制见 [使用说明](docs/usage.md)。**手机安装与测试由使用者负责**；电脑构建／浏览器检查不代表目标设备已经验收。

## 构建

需要 JDK 17、Android SDK Platform 36、Build Tools 36.0.0；Gradle Wrapper 已包含在仓库中。

```powershell
# Windows：测试、Lint、构建，输出到 dist/
.\build.ps1
```

Linux／macOS 及修改渲染源码后的构建步骤见 [构建与签名](docs/building.md)。生成后的 HTML 随源码保存，普通 Android 构建不需要 Node.js。

## 源码导航

- [`core/`](core/src/main/java/dev/ytmlyrics/core/)：纯 Java 歌曲／歌词模型、匹配、时间轴、偏移和预载策略。
- [`app/`](app/src/main/java/dev/ytmlyrics/)：宿主 Hook、来源协议、持久化和 Android 显示层。
- [`demo/`](demo/README.md)：AMLL 渲染源码、离线预览与 Node 检查；生产 HTML 由此生成。
- [`docs/`](docs/README.md)：使用、架构、构建、隐私及历史实现记录。
- [更新记录](CHANGELOG.md) · [参与开发](CONTRIBUTING.md) · [上传与发布](docs/publishing.md)

## 隐私与来源限制

查询向已启用的来源发送歌名、歌手、可选专辑和时长；选词后按来源歌曲 ID 取词。不读取账号凭据，不跨来源拼接译文，不调用机器翻译。封面来自宿主现有媒体会话，不额外下载。详情见 [隐私与数据流](docs/privacy.md)。

QQ／网易云使用未公开接口，服务端变化可能导致失效。请遵守来源服务条款及歌词权利人的要求。本仓库不分发宿主 APK、反编译产物、真实歌词数据或用户设备资料。

## 许可与致谢

项目采用 **AGPL-3.0-only**，全文见 [LICENSE](LICENSE)。AMLL 使用相同许可；QQ／网易云协议与 QRC 解密改编代码保留其 Apache-2.0 归属。具体来源、修改与完整许可证位置见 [第三方说明](THIRD_PARTY_NOTICES.md) 和 [NOTICE](NOTICE)。

感谢 [AMLL](https://github.com/amll-dev/applemusic-like-lyrics)、[Spotify_lyric](https://github.com/absswds/Spotify_lyric)、[Lyricify Lyrics Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) 与 [LRCLIB](https://lrclib.net/)。项目与 YouTube、Google、Apple、腾讯及网易无隶属关系。
