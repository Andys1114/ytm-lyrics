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
