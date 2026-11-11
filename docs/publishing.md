# 源码提交与模块目录发布

源码保留在 [Andys1114/ytm-lyrics](https://github.com/Andys1114/ytm-lyrics)。Xposed 模块目录使用独立的分发仓，只放模块介绍和 Release，不转移源码仓。

## 申请 Xposed Modules Repo

按[提交指南](https://github.com/Xposed-Modules-Repo/submission#readme)与[收录规则](https://github.com/Xposed-Modules-Repo#readme)，用 GitHub 账号 `Andys1114` 在 [submission](https://github.com/Xposed-Modules-Repo/submission/issues/new) 创建 Issue：

- **标题**：`[submission] io.github.andys1114.ytmlyrics`
- **正文**：`YTM Lyrics — A lyrics enhancement module for YouTube Music. Adds word-by-word highlighting and source translations with NetEase Cloud Music, QQ Music, and LRCLIB. Source and support: https://github.com/Andys1114/ytm-lyrics`

接受机器人发出的维护邀请后，在 `Xposed-Modules-Repo/io.github.andys1114.ytmlyrics` 的 GitHub 网页填写 **Description** 为 `YTM Lyrics`，**Website** 为 `https://github.com/Andys1114/ytm-lyrics`。把 [目录仓文案](../publishing/xposed-repo/) 中的 `SUMMARY` 和 `README.md` 放到目录仓根目录，源码继续在原仓库维护。

## 源码提交与工作流

项目 GitHub 仓库为 [Andys1114/ytm-lyrics](https://github.com/Andys1114/ytm-lyrics)，默认保护分支为 `main`。

### 1. 分支与提交流程

所有代码变更均通过特性分支（Feature Branch）向 `main` 提交 Pull Request：

```bash
# 1. 创建工作分支
git switch -c feat/your-feature-name

# 2. 检查待提交状态与排除文件
git status --short

# 3. 按文件暂存并审查（以这次构建配置改动为例）
git add -- app/build.gradle.kts docs/publishing.md
git diff --cached --stat
git diff --cached --name-only
```

> [!CAUTION]
> 提交前务必确认暂存清单中**绝不包含**以下敏感文件：
> - 维护者私有签名文件（`.tooling/debug.keystore`, `*.jks`）
> - 本机临时分析产物（`.analysis/`, `.device/`）
> - 宿主官方 APK 安装包或反编译工程
> - 个人测试导出的真实设备资料与未脱敏日志
> - 聊天附件目录（`.codex-remote-attachments/`）
> - 本地打包产物（`dist/`, `build/`）

### 2. 创建提交与 PR

```bash
git commit -m "feat: 简明扼要描述本次改动"
git push -u origin HEAD
```

在 GitHub 网页发起指向 `main` 分支的 Pull Request。项目不强制双人审查，亦不强制 CI 状态拦截；分支保护规则严禁对 `main` 进行强推（Force Push）与删除。

---

## GitHub Actions CI 验证

为节约自动化配额并避免无意义触发，项目的 GitHub Actions 工作流采用**全手动触发**机制：

- **触发方式**：在 GitHub 仓库页面进入 **Actions** → 选择 **Build and check** → 点击 **Run workflow**，选择要验证的分支。
- **CI 执行内容**：
  1. 初始化 Ubuntu 运行环境与 Android SDK Platform 36。
  2. 重建 AMLL 前端离线包并比对提交的生成文件一致性。
  3. 执行 `demo` 模块 Node 测试。
  4. 执行 `core` 与 `app` 的 JUnit 单元测试。
  5. 运行 Android Lint 静态分析。
  6. 编译生成 Debug APK。
- **构建产物与报告**：测试报告与 CI 编译生成的临时 APK 作为 Artifact 保留 14 天。
- **权限与签名限制**：CI 仅被授予只读权限（`contents: read`）；**CI 使用随机生成的调试密钥签名，不能作为可供用户覆盖安装的正式发布包**。

---

## 构建并签名目录版

确认发布前，更新本次对应的 `CHANGELOG.md` 并合入源码仓。后续正式更新须递增 `versionCode`；界面预览和试装不升版本。若改过前端，构建时带 `-RebuildRenderer`，同时提交生成文件。

```powershell
.\build.ps1 -Catalog
```

该命令跑单元测试和 Release Lint，再产出 `dist/ytm-lyrics-<versionCode>-<versionName>-unsigned.apk`，并打印目录用的 tag 和标题。公开发布使用包名 `io.github.andys1114.ytmlyrics`。

首次发布前准备并离线备份专用发布密钥。没有密钥时，在终端执行一次下列命令并设置口令；已有密钥就沿用，不要重新生成。

```powershell
keytool -genkeypair -keystore .tooling/catalog-release.p12 -storetype PKCS12 -alias ytm-lyrics-release -keyalg RSA -keysize 3072 -validity 10000
```

目录版之后每次更新都使用同一密钥，不用本地或 CI 的调试密钥。下面以当前构建 `25-0.7.14` 为例，口令由签名工具在终端读取：

```powershell
$tools = Join-Path $env:ANDROID_HOME 'build-tools/36.0.0'
$tag = '25-0.7.14'
& "$tools/zipalign.exe" -f 4 "dist/ytm-lyrics-$tag-unsigned.apk" "dist/ytm-lyrics-$tag-aligned.apk"
& "$tools/apksigner.bat" sign --ks .tooling/catalog-release.p12 --ks-key-alias ytm-lyrics-release --out "dist/ytm-lyrics-$tag.apk" "dist/ytm-lyrics-$tag-aligned.apk"
& "$tools/apksigner.bat" verify --verbose --print-certs "dist/ytm-lyrics-$tag.apk"
& "$tools/aapt.exe" dump badging "dist/ytm-lyrics-$tag.apk" | Select-String '^package:'
```

签名与包名检查通过后，由使用者安装 APK，在 LSPosed 中启用模块并选择 YouTube Music，重启 YouTube Music 后确认歌词页能加载。

## 在目录仓发布 Release

- **Tag**：`versionCode-versionName`，如 `25-0.7.14`；**标题**只用版本名，如 `0.7.14`。
- **正文**：写对应版本的更新日志、支持的 YTM 版本和构建对应的公开源码提交链接。
- **附件**：创建 Release 时一并上传已签名的 `dist/ytm-lyrics-25-0.7.14.apk`，不要上传 Debug、未签名或中间 APK。只替换附件不会触发目录机器人。

目录收录要求仓库名等于包名、Description 非空，且至少有一个带正确 tag 和 APK 附件的 Release。发布后在 [模块目录](https://modules.lsposed.org/) 搜索包名确认展示。
