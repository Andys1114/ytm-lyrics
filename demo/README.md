# 真正的 AMLL 逐字歌词预览

打开 `lyrics-preview.html`，点「播放」体验逐字填色、长音强调和弹簧滚动。拖动进度可比较同一个词在不同时间的效果；译文默认开启，点击歌词行可跳到原始行时间。停止手动滚动三秒后恢复跟随。

这是 30 秒自写中英文示例的模拟时间轴，不播放音频、不调用歌词接口。成品是单文件，所有脚本、样式和许可证都已内嵌，使用时无需 npm、网络或服务端。独立 Demo 不修改插件数据；用户确认后，0.3.0 已将同一 AMLL 核心接入插件。手机测试仍由用户负责。

## 真实组件与演示外壳分开

- 歌词渲染采用 [AMLL Core 0.6.0](https://github.com/amll-dev/applemusic-like-lyrics)，不是手写仿制的遮罩或弹簧算法。
- 播放按钮、锚点时钟、自写样例、抽象封面及 CSS 背景是本项目的演示外壳；无需 React 或 Vue。
- 单词起止时间直接转为 AMLL 的 `LyricLine[]`。浏览器动画帧只同步绝对播放位置并更新组件，不累加播放时间。
- 跳转通过 `setCurrentTime(ms, true)` 对齐；点击行使用原始样例时间，避免 AMLL 的显示预激活窗口改变跳转落点。
- 上游允许在滚动结束 500 ms 后、下次歌词焦点切换时恢复跟随。为保留本 Demo 的三秒约定，构建时仅将固定版本中的 `AUTO_ALIGN_RESUME_DELAY_MS` 改成 3000；三秒到期后调用公开的 `resetScroll()` 和 `calcLayout()`，使暂停时也能归位。补丁匹配失败会停止构建。
- AMLL 为 AGPL-3.0-only。页面「开源组件与许可」及 `licenses/THIRD_PARTY.txt` 包含实际打包依赖的完整许可；`@pixi/colord` 缺失的许可证从其原作者仓库补齐。

## 修改和重新生成

在仓库的 `demo/` 目录运行：

```powershell
npm.cmd ci --legacy-peer-deps --ignore-scripts
npm.cmd run build
npm.cmd test
```

`package-lock.json` 固定完整依赖。AMLL 的 npm 元数据路径曾出现连接重置，因此核心依赖固定为 npm 官方仓库的 0.6.0 tarball；构建后的 HTML 不访问它。

- `src/preview.template.html`：布局与演示背景。
- `src/timeline.js`：自写歌词、原始字词时间与 AMLL 数据适配。
- `src/preview.js`：AMLL 接入、播放控制与手动跟随。
- `build.mjs`：本地打包、三秒跟随补丁、许可证内嵌。
- `lyrics-preview.html`：独立模拟 Demo，生成文件，不要直接编辑。
- `src/host-renderer.js`、`src/host-clock.js`、`src/host.template.html`：插件生产渲染页和播放桥接。
- `host-preview.html`：以生产 HTML 加入 PC 测试控制条，不包含在 APK 中。
- `app/src/main/assets/amll/lyrics.html`：与上述资源一起生成，内嵌依赖、禁止网络和文件访问。
- `vendor/amll-core-0.6.0.mjs.map`：上游发行包的原始 source map，`sourcesContent` 保留对应 TypeScript 源码；许可另见同目录。

## 验证范围

自动测试覆盖全部字词时间、译文映射、原始数据不被组件改写、锚点时钟与跳转、真实组件打包、脚本语法和无外部资源依赖。浏览器交互检查通过本地 HTTP 预览进行；内置浏览器禁止 `file://`，因此未实测双击文件打开。

已检查初始暂停与译文开启、播放／暂停、结束／重新开始、向前／向后跳转、译文隐藏与恢复、点击歌词按原始行时间定位，以及手动滚动后三秒恢复跟随。暂停时除了进度值，还核对了实际字词遮罩和上浮变换保持不变。检查了 1280 像素桌面、320 像素窄屏及正常浏览器窗口的换行与布局。

当前预览以 AMLL 取代早期 A/B 面板，0.3.0 已接入插件。0.3.2 将插件和独立预览的译文字号统一放大至原文约 62%，最小 18 CSS 像素；插件另按歌曲匹配、同步等级及译文完整度自动择优。旧绑定仍先显示缓存，再按原来源和歌曲 ID 补取增强数据。

0.3.3 中，当前行译文随 AMLL 的原文活动行一起整行亮起；其余译文保持低亮度并平滑淡出。只使用已有活动行状态，不生成译文逐字时间。

## 0.5.0：电脑检查手动偏移

`host-preview.html` 的“偏移”提供不偏移、提前／延后 0.5 秒及正负 10 秒，控制的是 APK 实际使用的桥接渲染器。媒体时钟保持不变，显示时钟 = 媒体时钟 + 偏移；译文重新布局时保存媒体时钟，避免重复叠加。独立封面 Demo 不写手机设置，也没有增加手机菜单的模拟图。

电脑实测：暂停在媒体 3.80 秒时，提前 0.5 秒亮第二行，延后 0.5 秒亮第一行；切换译文不改变高亮或媒体位置。曲首延后 0.5 秒无活动行；此时点击首行，媒体跳到 0.50 秒。控制台无警告／错误。原生标题菜单和播放器按钮仍需用户在手机验收。

## 0.6.0：实际 AMLL 背景

打开 `host-preview.html` 检查 APK 同源的 `MeshGradientRenderer`。独立 `lyrics-preview.html` 保留原有演示外壳；新的真实背景在桥接检查页。顶部“模拟封面”使用 Canvas 自绘 JPEG，无外部资源或真实专辑素材；“背景故障”通过 `WEBGL_lose_context` 触发真实上下文丢失。控制栏、故障按钮和标题衔接色条均不在 APK 中。

`album-background.js` 独立管理封面版本和生命周期；同一次 native 请求的空占位与最终图片可共用 revision，不接受旧 revision 或晚到的旧解码结果。用已解码 `HTMLImageElement` 调用 AMLL，避开其 URL 入口中的 fetch，生产 CSP 继续 `connect-src 'none'`，仅为封面增加 `img-src data:`。

暂停时网格速度设为 0，静态模式完成换图淡入后停止 RAF；隐藏直接 pause，返回可见时遵守原生可见状态与系统减少动态效果设置。图片损坏或 WebGL 失败只释放背景，保留底层模糊封面及歌词实例。重建 WebView 时重新创建背景并重放最新封面。

电脑证据：冷暖封面均真实绘制；暂停后的两次截图中，正文区域像素完全相同；隐藏状态为 hidden，恢复暂停时为 static。无封面回到中性暗底；损坏 JPEG 与真实 WebGL 上下文丢失均进入 fallback，歌词仍可播放，控制台无 error。桌面软件 WebGL 报一条 `EXT_color_buffer_float not supported` 上游提示，采用现有兼容路径后显示正常。360px 视口的歌词／译文与工具栏检查完成。`npm test` 新增背景生命周期、异步取消和故障隔离检查。
