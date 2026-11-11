# YTM Lyrics

YTM Lyrics 是一个 YouTube Music 歌词增强模块，在原生歌词页提供逐字填色、译文、封面动态背景和单曲同步微调。

默认依次查询 **网易云音乐 → QQ 音乐 → LRCLIB**，也可以在设置里调整顺序。未匹配到合适的同步歌词时，保留 YouTube Music 原生歌词。

## 支持环境

- Android 16，已 Root
- YouTube Music **9.41.52**（versionCode **94152240**）
- 支持 modern libxposed **API 102** 的 Xposed 框架

## 安装

1. 从本仓库的 [Releases](https://github.com/Xposed-Modules-Repo/io.github.andys1114.ytmlyrics/releases) 安装 APK。
2. 在 Xposed 管理器启用 **YTM Lyrics**（`io.github.andys1114.ytmlyrics`），作用域仅选 YouTube Music。
3. 强行停止并重新启动 YouTube Music，再打开播放器的歌词页。

[使用说明](https://github.com/Andys1114/ytm-lyrics/blob/main/docs/usage.md) · [隐私说明](https://github.com/Andys1114/ytm-lyrics/blob/main/docs/privacy.md) · [源码](https://github.com/Andys1114/ytm-lyrics) · [问题反馈](https://github.com/Andys1114/ytm-lyrics/issues)
