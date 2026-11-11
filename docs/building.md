# 构建与签名指南

本文档介绍如何在不同操作系统下从源码编译 YTM Lyrics APK、调试 AMLL 离线渲染器，以及管理签名密钥。

## 环境要求

| 组件 | 要求版本 | 作用 | 必需性 |
| --- | --- | --- | --- |
| **JDK** | JDK 17（推荐 Eclipse Temurin 或 OpenJDK 17） | Gradle 与 Android 编译 | **必需** |
| **Android SDK Platform** | API 36（Android 16） | Android 平台依赖库 | **必需** |
| **Android Build Tools** | 36.0.0 | 打包、D8 编译与对齐签名 | **必需** |
| **Gradle** | 9.3.1（仓库内含 `gradlew` 包装器） | 构建编排 | **必需**（自动下载） |
| **Node.js & npm** | Node.js 24.x & npm 10+ | 修改 `demo/src` 时重新生成 AMLL 离线包 | 仅修改渲染前端时需要 |
| **Python** | Python 3.x | 运行本地静态服务器供浏览器预览检查 | 可选（仅 PC 预览用） |

> [!NOTE]
> 仓库已预置编译好的 `app/src/main/assets/amll/lyrics.html`。**若仅编译 Android APK 或修改 Java 代码，无需安装 Node.js**。

---

## 快速构建 APK

### 1. 配置环境变量

确保系统已配置环境变量，或在项目根目录创建未跟踪的 `local.properties` 文件：

```properties
## local.properties
sdk.dir=C:\\Users\\<YourUsername>\\AppData\\Local\\Android\\Sdk
```

### 2. 执行编译

#### Windows 环境

在仓库根目录直接运行内置 PowerShell 脚本：

```powershell
.\build.ps1
```

**该脚本自动执行**：
1. 检查 Java 与 Android SDK 环境变量。
2. 修复 Windows 下 JDK Unix-domain socket 路径过长问题（设置本地临时路径）。
3. 检查或生成 `.tooling/debug.keystore`（若已有维护者密钥则直接沿用）。
4. 运行 `core` 纯 Java 单元测试与 `app` 模块单元测试。
5. 执行 Android Lint 静态分析。
6. 打包 Debug APK，并将带版本号的安装包复制到根目录 `dist/`。

给同一版本另存试装包时，运行 `.\build.ps1 -ArtifactSuffix '-vertical-preview'`。后缀只加到 APK 文件名，不改变应用版本，也不覆盖默认安装包。

若同时修改了渲染前端源码，可添加 `-RebuildRenderer` 参数实现一站式重新打包与生成：

```powershell
.\build.ps1 -RebuildRenderer
```

#### Linux / macOS 环境

赋予 Gradle Wrapper 执行权限并运行：

```bash
chmod +x gradlew
./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console plain
```

编译产物位于：
- `app/build/outputs/apk/debug/app-debug.apk`

---

## 调试与发布签名

> [!IMPORTANT]
> 公开发布使用包名 `io.github.andys1114.ytmlyrics`。后续覆盖更新需保持包名和签名一致。歌词绑定、设置和偏移保存在 YouTube Music 的数据中。

### 密钥加载优先级

1. **本地调试密钥**：
   - 路径：`.tooling/debug.keystore`。
   - 若本地存在该文件，构建系统优先使用它签名 Debug APK。
   - 该目录与常见密钥文件（`.keystore`, `.jks`）已被 `.gitignore` 排除，**严禁提交至 Git 仓库**。
2. **标准 Android 调试密钥**：
   - 若本地没有 `.tooling/debug.keystore`（如全新的 Git Clone 或 GitHub CI 运行环境），构建系统自动使用 Android SDK 默认的标准 debug 签名。
   - 标准调试签名仅用于开发验证。

目录版用 `.\build.ps1 -Catalog` 生成未签名 APK，随后使用专用发布密钥签名。见[模块目录发布流程](publishing.md#构建并签名目录版)。

> [!WARNING]
> 切勿为了解决“签名不匹配”而清除 YouTube Music 的应用数据或卸载宿主应用！清除数据会导致所有已保存的手动歌词绑定、来源排序与本曲偏移全部丢失。维护者应妥善备份并使用原始专用密钥重新编译。

---

## 修改前端渲染源码 (AMLL)

前端歌词渲染与背景特效源码位于 `demo/src`。若需要调整动画效果、字号比例或通信桥接：

### 1. 安装前端依赖

在仓库根目录运行：

```bash
npm --prefix demo ci --legacy-peer-deps --ignore-scripts
```

> [!NOTE]
> 为避免上游网络问题，核心 AMLL 依赖已锁定为官方 0.6.0 tarball；`package-lock.json` 保证了所有间接依赖的版本一致性。

### 2. 打包与生成离线资产

```bash
npm --prefix demo run build
npm --prefix demo test
```

`demo/build.mjs` 会自动执行以下任务：
- 应用 3 秒手动滚动恢复跟随补丁（`AUTO_ALIGN_RESUME_DELAY_MS` 修改）。
- 提取并生成第三方开源许可清单（`demo/licenses/THIRD_PARTY.txt`）。
- 打包生成三份文件：
  1. `demo/lyrics-preview.html`：独立时间轴离线演示 Demo。
  2. `demo/host-preview.html`：带 PC 检查控制栏的生产渲染器预览。
  3. `app/src/main/assets/amll/lyrics.html`：打入 APK 的最终内嵌离线页面。
- 执行前端单元测试，验证字词解析、时间轴推算、封面生命周期与版本隔离。

---

## 电脑端浏览器预览与测试

在电脑端可无需 Android 设备直接测试歌词动效、封面流动与跳转逻辑：

```bash
# 启动本地轻量 HTTP 服务器
python -m http.server 63158 --bind 127.0.0.1 --directory demo
```

在浏览器中打开：
- [生产渲染器预览 (带控制面板)](http://127.0.0.1:63158/host-preview.html)：可模拟播放、暂停、倍速、手动偏移、自绘冷暖色封面以及 WebGL 上下文丢失容错。
- [独立 AMLL Demo](http://127.0.0.1:63158/lyrics-preview.html)：单文件纯净体验。

> [!TIP]
> 预览页面顶部的控制栏与标题衔接色条是专为电脑调试设计的辅助工具，构建时已被剥离，不会进入最终 APK。

---

## 检查测试报告与验证标准

编译完成后，可通过以下报告验证质量：

- **单元测试报告**：
  - Core 模块：`core/build/reports/tests/test/index.html`
  - App 模块：`app/build/reports/tests/testDebugUnitTest/index.html`
- **Lint 静态分析报告**：
  - `app/build/reports/lint-results-debug.html`
  - *注：由于目标锁定为 API 36，报告中可能包含一项 `OldTargetApi` 提示，属预期现象，无其他 Fatal 错误。*
- **APK 签名验证**：
  ```bash
  apksigner verify --verbose dist/ytm-lyrics-0.6.0-debug.apk
  ```

---

## 常见构建问题排查 (Troubleshooting)

### 1. 报错 `SDK location not found`
- 检查系统环境变量 `ANDROID_HOME` 是否指向 Android SDK 根目录。
- 或在项目根目录下新建 `local.properties` 文件并写入：
  `sdk.dir=你的AndroidSDK路径`（Windows 路径中使用双反斜杠 `\\`）。

### 2. 报错 `Unsupported class file major version`
- 说明当前 Java 版本与 Gradle 9.3.1 不兼容。请确认运行的是 **JDK 17**（通过 `java -version` 确认）。

### 3. Windows 下报错 `socket path too long`
- Windows 下 JDK 的 Unix-domain socket 路径长度受限。使用仓库根目录提供的 `.\build.ps1` 脚本运行，脚本会自动将临时目录重定向至短路径，解决此问题。

### 4. `npm run build` 报错 `patch did not match`
- 说明 AMLL 依赖版本发生变动，3 秒跟随的补丁无法精确应用。请勿手动忽略报错，检查 `demo/package-lock.json` 是否与主分支一致。
