# 构建与签名

## 构建 APK

安装 JDK 17、Android SDK Platform 36 和 Build Tools 36.0.0。设置 `JAVA_HOME`、`ANDROID_HOME`，或在不提交的 `local.properties` 中指定 `sdk.dir`。仓库固定 Android Gradle Plugin 9.1.0 和 Gradle Wrapper 9.3.1。

### Windows

在仓库根目录运行：

```powershell
.\build.ps1
```

脚本运行 core／app 单元测试、Android Lint 和 debug APK 编译，将带版本名的 APK 复制到 `dist/`。它仅在本机没有 `.tooling/debug.keystore` 时创建调试密钥；已有密钥继续使用。Windows JDK 的 Unix-domain socket 临时路径也由脚本设置，不需要修改全局环境。

### Linux / macOS

```sh
bash ./gradlew :core:check :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console plain
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。不需要宿主 APK、反编译工具、ADB 或签名私钥即可编译。

## 签名决定能否覆盖更新

- 若存在 `.tooling/debug.keystore`，Gradle 沿用它，供维护者保持原有安装的更新链。
- 干净克隆与 GitHub CI 没有该文件时，使用 Android 标准调试签名。CI APK 用于验证构建，不作为维护者同签名发行包。
- 请单独备份维护者密钥，不提交到 Git、源码 ZIP 或 GitHub Artifacts。Git 忽略规则已排除该目录及常见签名格式。
- 签名不同的 APK 无法直接覆盖已有模块。不要为解决签名问题清除 YTM 数据；维护者应使用原密钥重新构建。

## 修改渲染源码

需要 Node.js 24 和 npm，在仓库根目录运行：

```sh
npm --prefix demo ci --legacy-peer-deps --ignore-scripts
npm --prefix demo run build
npm --prefix demo test
```

然后再运行 Android 构建；Windows 也可用 `.\build.ps1 -RebuildRenderer` 连同生成与检查一起执行。AMLL 固定为官方 0.6.0 tarball，完整依赖由 `demo/package-lock.json` 固定。

`demo/build.mjs` 会更新：

- `demo/lyrics-preview.html`：独立模拟时间轴 Demo。
- `demo/host-preview.html`：实际生产渲染器加电脑检查控件。
- `app/src/main/assets/amll/lyrics.html`：APK 实际加载的离线页。
- `demo/licenses/THIRD_PARTY.txt` 与 APK 对应的 `AMLL-NOTICES.txt`。

这些生成文件随源码提交，不要手改。修改输入后重新生成；CI 检查生成结果与提交内容一致。上游三秒跟随补丁不再匹配时，构建会明确失败，而不是静默丢失行为。

## 浏览器预览

在仓库根目录运行：

```sh
python -m http.server 63158 --bind 127.0.0.1 --directory demo
```

打开 [生产渲染预览](http://127.0.0.1:63158/host-preview.html) 或 [独立 Demo](http://127.0.0.1:63158/lyrics-preview.html)。控件使用自写歌词与自绘封面，不播放音频、不调用歌词接口；这些检查控件不进入 APK。APK 自带资源，使用时不需要电脑服务器。

## 检查结果

JUnit 报告在 `core/build/reports/tests/test/` 和 `app/build/reports/tests/testDebugUnitTest/`；Lint 报告在 `app/build/reports/lint-results-debug.html`。当前固定 targetSdk 36 有一项 OldTargetApi 提示。是否通过应看本次运行结果，历史检查记录不能代替当前候选的验证。

GitHub Actions 仅手动触发：在仓库 Actions → Build and check → Run workflow 中选择分支。运行时初始化 Android SDK，重建渲染器，运行 Node／JUnit／Lint／APK 构建，并提供 14 天的测试报告与临时调试 APK。提交和 PR 不会自动构建，工作流不含维护者签名和自动发布步骤。
