# 第三方代码与协议参考

## QQ 音乐、网易云与 QRC 解密

来源协议参考并改编自 [absswds/Spotify_lyric](https://github.com/absswds/Spotify_lyric)，固定 revision `827a0f483e7808b4b749adbffdd9227ae3035b89`，Apache-2.0：

- `lyrics/qqmusic/QQMusicLyricsSource.kt`：数字歌曲 ID 搜索、Base64 LRC、QRC POST 参数。
- `lyrics/qqmusic/QrcConverter.kt`：QRC 响应及 `LyricContent` 提取。
- `lyrics/qqmusic/QrcDecrypter.kt`：QQ 非标准 DES 位序与 S-box、三段解密和 zlib。
- `lyrics/netease/NeteaseLyricsSource.kt`、`NeteaseApi.kt`：网易云搜索、LRC/YRC/译文与两种元数据字段形状。

Copyright 2026 absswds。上游也注明请求参数、格式和解密器参考／移植自 [WXRIW/Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper)（Apache-2.0）。

本项目改为 Java、顺序且限量请求、稳定数字 ID、显式错误、按原 ID 增强旧绑定，并加入格式验证、长度限制和来源译文对齐。不引入上游 Spotify 集成、通知、播放器 UI、并发调度或机器翻译。

## AMLL 歌词与背景渲染

采用 npm 官方发布的 `@applemusic-like-lyrics/core` **0.6.0**，项目为 [amll-dev/applemusic-like-lyrics](https://github.com/amll-dev/applemusic-like-lyrics)，许可 **AGPL-3.0-only**。歌词遮罩、字词上浮／长音强调、距离模糊、弹簧布局及 MeshGradientRenderer 网格流动背景由 AMLL 实现。

本地构建只将上游 `AUTO_ALIGN_RESUME_DELAY_MS` 从 500 改为 3000，以遵守用户的三秒手动浏览约定。APK 内的 HTML、构建源码和固定依赖位于 `demo/`；`demo/build.mjs` 生成 `app/src/main/assets/amll/lyrics.html`。其余播放桥接、UI 外壳、时间模型、来源及缓存代码为本项目实现。

AMLL 和实际打包的运行依赖许可证全文位于 APK `assets/third_party/AMLL-NOTICES.txt`，同时保存在 `demo/licenses/THIRD_PARTY.txt`。`@pixi/colord` 的 npm 包遗漏 LICENSE.md，补入其原作者 [Vlad Shilov 的 MIT 许可](https://github.com/omgovich/colord/blob/master/LICENSE.md)。构建依赖固定于 `demo/package-lock.json`。

Apache-2.0 全文和改编归属另位于 APK `assets/third_party/Apache-2.0.txt`、`NOTICE.txt`。这些代码许可不授予来源返回的歌词文本权利；不将真实取回的歌词纳入发布源码或测试样例。

## MDUI 设置组件

歌词设置页使用 [zdhxiong/mdui](https://github.com/zdhxiong/mdui) **2.1.5**（MIT），按需打包 Button、ButtonIcon、Switch、TextField、List 和 ListItem；未修改上游组件。界面样式、无障碍标签适配和 Android 桥接为本项目代码。所有脚本、样式及许可内嵌，不访问 CDN 或远程字体。

MDUI 和实际打包依赖的完整许可由 `demo/build-settings.mjs` 从固定 npm 包生成，保存在 `demo/licenses/SETTINGS-THIRD-PARTY.txt`，并随 APK 打包为 `assets/third_party/SETTINGS-NOTICES.txt`。设置页面也可展开“开源组件与许可”查看。

## Material 归零图标

归零按钮采用 Google [Material Design Icons](https://github.com/google/material-design-icons) 的官方 `replay` 24px 图标，许可 **Apache-2.0**。原始 SVG 与来源记录在 `demo/vendor/material-icons/`；电脑预览直接内嵌 SVG，Android 将相同的填充轮廓转换为 `Path`。只调整显示颜色和尺寸，不引入整套图标库。许可全文随 APK `assets/third_party/Apache-2.0.txt` 和设置页开源许可打包。

## 离线简繁转换

搜索字段和歌曲匹配使用 [houbb/opencc4j](https://github.com/houbb/opencc4j) **1.14.0** 的 `ZhConverterUtil.toSimple`，采用标准繁体转简体，不改写来源歌词、原始歌曲元数据或手动绑定键。词典随 Java resources 打包，运行时不联网、不下载词典，也不引入可选的 Jieba 分词库。

以下 Maven Central 运行库均未修改，许可均为 **Apache-2.0**：

- `com.github.houbb:opencc4j:1.14.0` — [houbb/opencc4j](https://github.com/houbb/opencc4j)。
- `com.github.houbb:heaven:0.13.0` — [houbb/heaven](https://github.com/houbb/heaven)。
- `com.github.houbb:nlp-common:0.0.5` — [houbb/nlp-common](https://github.com/houbb/nlp-common)。
- `org.apiguardian:apiguardian-api:1.0.0` — [@API Guardian Team](https://github.com/apiguardian-team/apiguardian)，由实际传递依赖引入。

Apache-2.0 全文位于 APK `assets/third_party/Apache-2.0.txt`，运行库归属列于 `NOTICE` 与 APK `assets/third_party/NOTICE.txt`。
