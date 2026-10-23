# 参与开发

先阅读 [构建说明](docs/building.md) 和 [架构](docs/architecture.md)。本项目按 YTM 9.41.52 / 94152240 适配，变更宿主版本要同时更新 Hook、静态定位依据与验收范围。

## 改动放在哪里

- 新来源：实现 `core/LyricsSource`，返回稳定来源／歌曲 ID 与 `Candidate`，在 `SourceRegistry` 注册；不要为新来源修改播放器 Hook。
- 匹配／时间模型／队列策略：放在纯 Java `core`，保持毫秒单位、真实字词时间和手动绑定优先。
- 协议与存储：放在 `app/source`，通过可替换 transport 测试正常响应、失败、空结果和旧数据兼容，不把网络请求放进单元测试。
- Android／宿主适配：放在 `app/hook`、`app/ui`，保留无匹配时的原生回退与其他面板行为。
- 渲染：修改 `demo/src`，再重新生成 APK HTML。保持媒体时间、显示偏移与封面 revision 分离；不要直接编辑生成文件。

`.editorconfig` 和 `.gitattributes` 规定缩进、UTF-8 与行尾。沿用相邻代码风格，避免把功能改动与全仓库格式化混在一个 PR。

## 交付前运行

```sh
npm --prefix demo run build
npm --prefix demo test
```

渲染未改时可略过 Node；Android 改动运行 [平台对应的完整构建命令](docs/building.md#构建-apk)。UI 改动要在浏览器检查实际画面、暂停／跳转／切歌和窄屏；Hook、Android 页面与真实音频同步仍需要使用者手机验收。手机操作的项目约定见 [AGENTS.md](AGENTS.md)。

不要为兼容新行为改变旧绑定键、原始歌词时间、来源启停含义或恢复次数。需要变更这些契约时，在 PR 中说明影响和迁移方式，并更新对应测试与文档。

## 反馈问题

使用 Issue 模板描述环境、复现步骤、预期与实际表现。日志只保留相关片段并脱敏，截图遮挡通知与个人信息。不要上传宿主 APK、反编译文件、设备序列号、账号／Cookie、维护者签名或完整真实歌词。可复现样例使用自写内容。

## 第三方改编

新增或升级依赖时核对许可与原始来源，更新 [第三方说明](THIRD_PARTY_NOTICES.md)、NOTICE 和 APK 许可。保留已有文件的原作者、许可与修改说明。提交的代码应有权按本项目 AGPL-3.0-only 许可分发；第三方文件继续保留适用的原许可标识。
