# 参与开发与贡献指南

感谢你关注并愿意为 YTM Lyrics 做出贡献！

在开始编写代码前，请先完整阅读 [构建与签名指南](docs/building.md) 和 [架构与行为契约](docs/architecture.md)。本项目基于 YouTube Music **9.41.52（versionCode 94152240）** 适配；若要适配新版宿主，需同时更新混淆映射、Hook 代码及相应的测试用例。

---

## 规范的工作流

1. **分支策略**：所有改动均从个人的特性分支（如 `feat/xxx` 或 `fix/xxx`）向仓库 `main` 分支提交 Pull Request。
2. **合并规则**：
   - 解决 PR 中的所有讨论项后即可合并。
   - 不强制双人审查，亦不强制等待 CI 状态拦截（CI 为全手动触发）。
   - 保护分支规则：`main` 分支严禁 Force Push（强推）与删除。
3. **提交规范**：
   - 遵循清晰的 Conventional Commits 风格（如 `feat:`, `fix:`, `docs:`, `refactor:`, `test:`）。
   - 沿用项目现有的代码风格与缩进规范（见 `.editorconfig` 与 `.gitattributes`），避免将小功能修复与全仓库代码重格式化混在同一个 PR 中。

---

## 代码放置规范与架构边界

| 改动类型 | 对应目录 / 文件 | 架构与质量要求 |
| --- | --- | --- |
| **新增歌词源** | `core` 的接口与 `app` 的 `source` 包 | 实现 `LyricsSource` 接口，提供稳定来源与歌曲 ID，在 `SourceRegistry` 中注册。接入新来源不修改播放器 Hook。 |
| **匹配与时间算法** | `core/src/main/java/dev/ytmlyrics/core/` | 保持纯 Java 领域模型，不得依赖 Android SDK 或 Xposed；统一使用毫秒精度；严格遵循真实字词时间原则与手动绑定优先。 |
| **网络协议与解密** | `app/src/main/java/dev/ytmlyrics/source/` | 使用可插拔的 HTTP transport，单元测试使用 Mock 数据测试正常解析、空结果、失败及向后兼容，**严禁将真实网络请求写入单元测试**。 |
| **宿主 Hook 适配** | `app/src/main/java/dev/ytmlyrics/hook/` | 适配固定宿主版本；严格维护互斥挂载、原生回退与非歌词面板的属性恢复机制。 |
| **UI 与容错层** | `app/src/main/java/dev/ytmlyrics/ui/` | 处理 `LyricsPanel` 视图布局、WebView 限次自动恢复状态机（两次预算与指数退避）及设置菜单。 |
| **前端与渲染动效** | `demo/src/` | 修改 AMLL 桥接、3 秒跟随或流体背景；完成后必须重新打包离线 Bundle 并同步至 `app/src/main/assets/amll/lyrics.html`，**切勿直接手改生成文件**。 |

---

## 提交前验证清单 (Pre-flight Checklist)

在发起 PR 前，请确保在本地完整执行并跑通相关测试：

### 1. 前端渲染测试（若修改了 `demo/src`）
```bash
npm --prefix demo run build
npm --prefix demo test
```

### 2. Android 与 Java 自动化测试
```powershell
# Windows
.\build.ps1
```
```bash
# Linux / macOS
./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console plain
```

- [ ] 所有 `core` 与 `app` 单元测试 100% 通过（零失败、零错误）。
- [ ] Android Lint 检查通过，无 Fatal 错误。
- [ ] 若涉及 UI 变动，在本地通过浏览器预览工具检查窄屏（360px）自适应、点击跳转、倍速及故障回退表现。
- [ ] 铭记：**宿主 Hook、真实视图挂载与真机音频同步表现由使用者在真机上验证**，电脑端测试不应写为真机已验证的结论（见 [项目约定](AGENTS.md)）。

> [!CAUTION]
> 绝对不要破坏既有契约：保持旧绑定键、原始歌词时间轴、来源启停语义与 2 次恢复预算不变。若确实需要调整这些底层设计，必须在 PR 中详细阐明迁移方案并同步更新文档。

---

## 反馈问题与日志提交

提交 Issue 时请提供完整的复现信息：
1. **环境信息**：设备型号、Android 版本、Root 解决方案、Xposed 框架版本及精确的 YouTube Music 版本号。
2. **复现步骤**：清晰的 1、2、3 操作步骤，以及预期结果和实际结果。
3. **日志与截图脱敏**：
   - 提取 Logcat 中带有 `YtmLyrics` 标签的日志片段。
   - **严格抹去个人信息**：删除日志或截图中的设备序列号、IMEI、Google 账号邮箱、WiFi SSID/IP、私人通知等。
   - **严禁上传**：官方宿主 APK、反编译代码库、维护者签名私钥、真实版权歌词批量导出。测试用例请使用自编虚拟样例。

---

## 开源协议与第三方归属

- 本项目整体开源许可为 **AGPL-3.0-only**。你提交的自写代码必须同意在 AGPL-3.0-only 协议下分发。
- 若引入或参考第三方代码，必须确认其开源许可与 AGPL-3.0-only 兼容（如 Apache-2.0、MIT）。
- 保留第三方文件原有的版权头声明，并在 [第三方说明](THIRD_PARTY_NOTICES.md) 与 [NOTICE](NOTICE) 中同步记录修改说明与来源出处。
