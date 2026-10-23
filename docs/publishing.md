# 源码提交与发布

## 提交源代码

项目仓库为 [Andys1114/ytm-lyrics](https://github.com/Andys1114/ytm-lyrics)，主分支为 `main`。提交前先检查内容：

```sh
git status --short
git add .
git diff --cached --stat
git diff --cached --name-only
```

确认清单没有 `.tooling`／`.analysis`／`.device`、签名、宿主 APK、真实歌词、账号信息或 `dist`。再用你自己的 Git 身份创建提交：

```sh
git commit -m "Describe the change"
git push origin main
```

维护者提交应使用已在 GitHub 登记的签名密钥和对应提交邮箱；推送后查看提交页的 Verified 状态。不要把整个工作目录拖入网页上传：忽略目录中的本机材料应留在本地。

根目录已包含 AGPL-3.0-only LICENSE、第三方归属和贡献说明。公开前确认仓库说明与该许可符合你的发行意图；需要更换许可时，先处理已使用第三方代码的许可要求。

## 检查 GitHub CI

工作流在 push／pull_request 时重建渲染器，检查生成文件、Node、JUnit、Lint 和 APK 构建。它只授予 contents: read，不使用维护者签名、不自动发布 Release。GitHub 环境中的第一次运行只能在上传后确认，本地检查不等同于远端已通过。

CI APK 使用该运行环境的调试签名，仅供构建验证；不同运行的密钥也可能不同，不保证覆盖已有安装。检查报告与 APK Artifacts 保留 14 天。

## 发布给使用者

1. 维护者确认目标手机行为，按 [签名说明](building.md#签名决定能否覆盖更新) 保留原更新密钥。
2. 更新 `app/build.gradle.kts` 的 versionCode／versionName 和 CHANGELOG；渲染有改动时重新生成资源。
3. 本机构建并确认测试、Lint、APK 签名验证通过。不要使用 CI 的临时签名替代维护者密钥。
4. 在 GitHub Releases 创建与版本匹配的标签，例如 `v0.6.0`，上传 `dist/` 中的同签名 APK。源码由仓库标签关联；如附加源码 ZIP，保持许可、源码和生成步骤完整。
5. 发布说明列明精确宿主版本、安装方法、变化、手机验证范围和已知限制。

仓库不保存 APK 和历史压缩包；本地 dist 中的既有交付物保留，只在发行时有选择地上传。严禁随 APK 附上维护者密钥、YTM 安装包或设备回滚材料。
