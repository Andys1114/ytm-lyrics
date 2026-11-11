import {build, transform} from 'esbuild';
import {readFile, writeFile, readdir, mkdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const read = file => readFile(path.join(root, file), 'utf8');
const escapeHtml = value => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const bundled = await build({absWorkingDir: root, entryPoints: ['src/settings.js'], bundle: true,
  format: 'iife', target: ['chrome120'], minify: true, write: false, metafile: true, legalComments: 'inline'});
const css = await transform(await read('node_modules/mdui/mdui.css'), {loader: 'css', minify: true, target: 'chrome120'});
const packages = new Map();
for (const [input, usage] of Object.entries(bundled.metafile.outputs).flatMap(([, output]) => Object.entries(output.inputs))) {
  if (!usage.bytesInOutput || !input.includes('node_modules/')) continue;
  let directory = path.dirname(path.resolve(root, input));
  while (directory.startsWith(root)) {
    try {
      const info = JSON.parse(await readFile(path.join(directory, 'package.json'), 'utf8'));
      if (info.name) { packages.set(info.name, {directory, info}); break; }
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
    directory = path.dirname(directory);
  }
}
let notices = 'MDUI 2.1.5 settings components — MIT\nUpstream: https://github.com/zdhxiong/mdui\nUnmodified dependency; local styles and native bridge are YTM Lyrics code.\n';
notices += '\n=== Google Material Icons: replay (Apache-2.0) ===\nSource: https://github.com/google/material-design-icons/blob/master/src/av/replay/materialicons/24px.svg\nOriginal geometry; local size/color and native Android Path adaptation.\n'
  + await read('../app/src/main/assets/third_party/Apache-2.0.txt');
for (const [name, {directory, info}] of [...packages].sort(([a], [b]) => a.localeCompare(b))) {
  notices += `\n=== ${name} ${info.version} (${info.license ?? 'see license'}) ===\n`;
  const files = (await readdir(directory)).filter(file => /^(licen[sc]e|copying|notice)(\.|$|-)/i.test(file));
  if (!files.length) throw new Error(`Missing settings dependency license: ${name}`);
  for (const file of files) notices += `${await readFile(path.join(directory, file), 'utf8')}\n`;
}
const script = bundled.outputFiles[0].text.replaceAll('</script', '<\\/script');
const settingsCss = await read('src/settings.css');
// Keep each replacement literal and deterministic, including legal text containing dollar signs.
const page = (await read('src/settings.template.html'))
  .replace('/* __MDUI_CSS__ */', () => css.code)
  .replace('/* __SETTINGS_CSS__ */', () => settingsCss)
  .replace('__SETTINGS_LICENSES__', () => escapeHtml(notices))
  .replace('/* __SETTINGS_BUNDLE__ */', () => script);
const assets = path.resolve(root, '../app/src/main/assets');
await mkdir(path.join(assets, 'settings'), {recursive: true});
await mkdir(path.join(assets, 'third_party'), {recursive: true});
await writeFile(path.join(assets, 'settings/settings.html'), page);
await writeFile(path.join(assets, 'third_party/SETTINGS-NOTICES.txt'), notices);
await writeFile(path.join(root, 'licenses/SETTINGS-THIRD-PARTY.txt'), notices);
const harness = await build({absWorkingDir: root, entryPoints: ['src/settings-harness.js'], bundle: true,
  format: 'iife', target: ['chrome120'], write: false});
const controls = `<div id="preview-controls"><strong>歌词设置</strong><span>完整 MDUI 流程 · 模拟歌曲状态</span><label>预览状态 <select id="preview-mode"><option value="default">待选歌词</option><option value="selected">已选歌词</option><option value="empty">没有歌曲</option><option value="long">长标题与错误</option><option value="error">渲染器恢复耗尽</option></select></label><p id="preview-feedback">电脑交互预览 · 不写入手机设置</p></div>`;
const previewCss = `<style>body{background:#0b0d12;display:flex;justify-content:center;align-items:center;padding:28px;gap:64px}#preview-controls{width:280px;flex-shrink:0;color:#adb3c3;font:14px/1.6 system-ui}#preview-controls strong{display:block;color:#f4f5fa;font-size:28px;margin-bottom:12px}#preview-controls span{display:block;margin-bottom:30px}#preview-controls label{display:block}#preview-controls select{display:block;background:#20232c;border:1px solid #434b60;border-radius:10px;color:#f4f5fa;padding:10px;width:100%;font:inherit;margin:8px 0 24px}#preview-feedback{font-size:12px}#settings{max-width:412px;height:min(870px,94vh);box-shadow:0 0 0 1px #343a48;border-radius:28px}@media(max-width:760px){body{padding:0;display:block}#preview-controls{width:100%;padding:10px 16px;height:84px;display:flex;gap:12px;align-items:center;flex-wrap:wrap}#preview-controls strong,#preview-controls span,#preview-feedback{display:none}#preview-controls label{display:flex;align-items:center;gap:12px;width:100%;font-size:13px}#preview-controls select{display:inline-block;flex:1;width:auto;min-width:0;margin:0;font-size:13px}#settings{height:calc(100dvh - 84px);max-width:560px;margin:auto;border-radius:24px 24px 0 0;box-shadow:none}}</style>`;
const resetIcon = (await read('vendor/material-icons/replay.svg')).trim().replace('<svg ', '<svg aria-hidden="true" ');
const player = `<div id="preview-player" aria-label="歌词页布局示意"><div class="player-mini"><span>ユリイカ</span><small>ロクデナシ</small></div><div class="player-header"><strong>歌词</strong>
<div class="quick-actions" id="inline-sync">
  <button id="quick-choose">选词</button>
  <button id="quick-settings">设置</button>
  <button id="quick-sync" aria-controls="offset-popup" aria-expanded="false" aria-label="展开歌词偏移，不偏移"><span id="inline-value" aria-hidden="true">偏移</span></button>
  <section id="offset-popup" aria-label="本曲歌词偏移" aria-hidden="true" inert>
    <div id="inline-offset" role="slider" tabindex="0" aria-orientation="vertical" aria-label="本曲歌词偏移滚轮" aria-valuemin="-10" aria-valuemax="10" aria-valuenow="0">
      <div class="wheel-marks" aria-hidden="true"><span class="wheel-detent"></span><span class="wheel-detent"></span><span class="wheel-detent"></span><span class="wheel-detent"></span></div>
      <span id="wheel-value" aria-hidden="true">偏移</span>
    </div>
    <button id="inline-reset" aria-label="归零本曲歌词偏移" title="归零本曲歌词偏移">${resetIcon}</button>
  </section>
</div><button id="preview-close-lyrics" class="header-close" aria-label="关闭歌词预览">×</button></div>
<div class="player-lines"><p>风把回声<br>留在了窗边</p><p>この歌を届けたい<small>想把这首歌传达给你</small></p><p>等下一次日出<br>再唱给你听</p></div><button id="preview-open-lyrics" hidden>打开歌词</button><small class="layout-note">背景与原生标题为布局示意</small></div>`;
const playerCss = `<style>
#inline-sync{--action-width:54px;--action-height:48px;--pill-x:2px;--pill-y:9px;position:relative;display:flex;align-items:center;flex:1;min-width:calc(3 * var(--action-width));height:var(--action-height)}
#inline-sync button{position:relative;isolation:isolate;width:var(--action-width);height:var(--action-height);flex-shrink:0;padding:0;border:0;border-radius:12px;background:transparent;color:#e8ecff;font:13px system-ui;cursor:pointer;display:grid;place-items:center;white-space:nowrap}
#inline-sync button::before{content:"";position:absolute;inset:var(--pill-y) var(--pill-x);border-radius:12px;background:#3d4d6540;z-index:-1}
#inline-sync button:disabled{color:#8390a9;cursor:default}
#inline-sync #quick-sync{margin-left:auto;touch-action:none;user-select:none}
#inline-sync #quick-sync::before{content:none}
#inline-value,#wheel-value{display:grid;place-items:center;width:calc(var(--action-width) - 2 * var(--pill-x));height:calc(var(--action-height) - 2 * var(--pill-y));border-radius:12px;background:#35415e;color:#eff2ff;font:500 14px system-ui;font-variant-numeric:tabular-nums;white-space:nowrap}
#offset-popup{position:absolute;z-index:1;right:0;top:calc(50% - 44px);width:var(--action-width);display:flex;flex-direction:column;align-items:center;visibility:hidden;opacity:0;pointer-events:none}
#inline-sync[data-open] #offset-popup{visibility:visible;opacity:1;pointer-events:auto}
#offset-popup button{width:48px;height:40px}
#offset-popup button::before{background:#303747}
#inline-sync #inline-reset svg{width:20px;height:20px;fill:currentColor;stroke:none}
#inline-offset{position:relative;isolation:isolate;display:grid;place-items:center;width:100%;height:88px;flex-shrink:0;overflow:hidden;touch-action:none;user-select:none;cursor:ns-resize;border-radius:16px}
#inline-offset::before{content:"";position:absolute;inset:4px 0;border-radius:16px;background:#171b23;box-shadow:inset 0 0 0 1px #3d4d6540;z-index:-1}
#inline-offset:focus-visible,#inline-sync button:focus-visible{outline:1px solid #c1ccff;outline-offset:-1px}
.wheel-marks{position:absolute;inset:0;pointer-events:none}
.wheel-detent{position:absolute;top:50%;left:50%;transform:translate(-50%,-50%);opacity:var(--detent-alpha,0);font:12px system-ui;color:#aab6d4;white-space:nowrap}
#wheel-value{position:relative;pointer-events:none}
@media(prefers-reduced-motion:no-preference){
  #inline-sync{--wheel-ease:180ms cubic-bezier(.2,.75,.25,1)}
  #offset-popup{transition:opacity 95ms ease-in,visibility 0s linear 95ms}
  #inline-sync[data-open] #offset-popup{transition:none}
  #inline-offset::before{transform:scaleY(.375);transition:transform var(--wheel-ease)}
  #inline-sync[data-open] #inline-offset::before{transform:scaleY(1)}
  .wheel-marks{opacity:0;transition:opacity var(--wheel-ease)}
  #inline-sync[data-open] .wheel-marks{opacity:1}
  .wheel-detent{transform:translate(-50%,calc(-50% + var(--towards,-7px)));transition:transform var(--wheel-ease)}
  .wheel-detent:nth-child(-n+2){--towards:7px}
  #inline-sync[data-open] .wheel-detent{transform:translate(-50%,-50%)}
  #offset-popup button{opacity:0;transform:translateY(-6px);transition:opacity var(--wheel-ease),transform var(--wheel-ease)}
  #inline-sync[data-open] #offset-popup button{opacity:1;transform:translateY(0)}
}
#preview-player[data-lyrics="closed"] :is(.player-header,.player-lines,.layout-note){display:none}
#preview-open-lyrics{align-self:center;margin-top:20px;padding:12px 20px;border:0;border-radius:20px;background:#35415e;color:#f4f5fa;font:inherit;cursor:pointer}
.route-nav{grid-template-columns:repeat(2,1fr)}#preview-player{position:relative;width:412px;height:min(870px,94vh);background:#152c3b;border-radius:28px;overflow:hidden;flex-shrink:0;display:flex;flex-direction:column}.player-mini{height:100px;flex-shrink:0;background:#08090d;padding:25px 24px;color:white}.player-mini span,.player-mini small{display:block}.player-mini small{color:#adb3c3}.player-header{height:56px;flex-shrink:0;display:flex;align-items:center;gap:8px;padding:0 0 0 16px;background:#152c3b;color:#f4f5fa;white-space:nowrap}.player-header strong{font-size:20px}.header-close{border:0;background:transparent;color:inherit;cursor:pointer;font-size:32px;width:48px;height:48px;display:grid;place-items:center;margin-left:auto;flex-shrink:0}.player-lines{flex:1;min-height:0;overflow:hidden;padding:40px 24px;background:radial-gradient(ellipse at 90% 35%,#287b79aa,transparent 65%),linear-gradient(#152c3b,#034f68)}.player-lines p{font-size:30px;font-weight:700;line-height:1.5;color:#e3eaf944;margin:0 0 42px}.player-lines p:nth-child(2){color:#fff4ee}.player-lines small{display:block;font-size:18px;font-weight:400;margin-top:8px}.layout-note{position:absolute;bottom:16px;left:24px;color:#bbcbd3;font-size:11px}#settings{position:absolute;right:calc(50% - 140px - 32px - 206px);bottom:max(3vh,28px);height:90dvh;z-index:2;box-shadow:0 -8px 60px #0009}body{position:relative}@media(max-width:760px){#preview-player{width:100%;height:calc(100dvh - 84px);max-width:560px;margin:auto;border-radius:24px 24px 0 0}#settings{position:fixed;left:50%;right:auto;transform:translateX(-50%);bottom:0;height:90dvh;max-width:560px}.player-lines p{font-size:28px}}</style>`;
const preview = page.replace('</head>', () => previewCss + playerCss + '</head>').replace('<body>', () => '<body>' + controls + player)
  .replace('<script>', () => `<script>${harness.outputFiles[0].text.replaceAll('</script', '<\\/script')}</script><script>`);
await writeFile(path.join(root, 'settings-preview.html'), preview);
console.log(`Built offline MDUI settings: ${Math.round(Buffer.byteLength(page) / 1024)} KiB, ${packages.size} licensed packages`);
