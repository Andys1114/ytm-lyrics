import {build, transform} from 'esbuild';
import {readFile, writeFile, readdir, mkdir} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root = path.dirname(fileURLToPath(import.meta.url));
const read = file => readFile(path.join(root, file), 'utf8');
const escapeHtml = text => text.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
const options = {
  absWorkingDir: root, entryPoints: ['src/preview.js'], bundle: true,
  format: 'iife', target: ['chrome120'], write: false, metafile: true,
  legalComments: 'inline', treeShaking: true,
  plugins: [{
    name: 'amll-preview-scroll-delay',
    setup(builder) {
      builder.onLoad({filter: /amll-core\.mjs$/}, async ({path: file}) => {
        const source = await readFile(file, 'utf8');
        const original = 'const AUTO_ALIGN_RESUME_DELAY_MS = 500;';
        if (source.split(original).length !== 2) throw new Error('AMLL scroll-delay patch no longer matches the pinned release');
        return {contents: source.replace(original,
          '// Modified for this preview: respect three seconds of manual browsing.\nconst AUTO_ALIGN_RESUME_DELAY_MS = 3000;'), loader: 'js'};
      });
    }
  }]
};
const result = await build(options);
const hostResult = await build({...options,entryPoints:['src/host-renderer.js']});
const css = await transform(await read('node_modules/@applemusic-like-lyrics/core/dist/style.css'), {
  loader: 'css', target: 'chrome105', supported: {'nesting': false}, legalComments: 'inline'
});

// Embed licenses for the packages actually used by the generated bundle.
const packages = new Map();
const usedInputs = new Set([...Object.values(result.metafile.outputs),...Object.values(hostResult.metafile.outputs)].flatMap(output =>
  Object.entries(output.inputs).filter(([, value]) => value.bytesInOutput > 0).map(([file]) => file)));
for (const input of usedInputs) {
  if (input.startsWith('(disabled):')) continue; // esbuild's empty browser shims
  if (!input.includes('node_modules/')) continue;
  let directory = path.dirname(path.resolve(root, input));
  while (directory.startsWith(root)) {
    try {
      const info = JSON.parse(await readFile(path.join(directory, 'package.json'), 'utf8'));
      if (info.name) {
        if (directory === root) throw new Error(`Cannot locate dependency package metadata for ${input}`);
        packages.set(info.name, {directory, info}); break;
      }
    } catch (error) { if (error.code !== 'ENOENT') throw error; }
    directory = path.dirname(directory);
  }
}
let notices = 'AMLL Core 0.6.0 — AGPL-3.0-only\nUpstream: https://github.com/amll-dev/applemusic-like-lyrics\nLocal modification: manual-scroll resume delay 500 ms → 3000 ms.\n\n';
for (const [name, {directory, info}] of [...packages].sort(([a], [b]) => a.localeCompare(b))) {
  notices += `\n=== ${name} ${info.version} (${info.license ?? 'see license'}) ===\n`;
  const files = (await readdir(directory)).filter(file => /^(licen[sc]e|copying|notice)(\.|$|-)/i.test(file));
  if (!files.length && name === '@pixi/colord') {
    // Its npm tarball omits LICENSE.md; retain the original author's license.
    notices += await read('licenses/upstream/colord-MIT.txt');
    notices += '\nSource: https://github.com/omgovich/colord/blob/master/LICENSE.md\n';
    continue;
  }
  if (!files.length) throw new Error(`Missing bundled dependency license: ${name}`);
  for (const file of files) notices += `${await readFile(path.join(directory, file), 'utf8')}\n`;
}
const template = await read('src/preview.template.html');
const model = await read('src/timeline.js');
const bundle = result.outputFiles[0].text.replaceAll('</script', '<\\/script');
const html = template
  .replace('/* __AMLL_CSS__ */', () => css.code)
  .replace('__THIRD_PARTY_LICENSES__', () => escapeHtml(notices))
  .replace('/* __TIMELINE_MODEL__ */', () => model)
  .replace('/* __AMLL_BUNDLE__ */', () => bundle);
if (/__AMLL_|__TIMELINE_|__THIRD_PARTY_/.test(html)) throw new Error('Unresolved build marker');
await writeFile(path.join(root, 'lyrics-preview.html'), html);
await mkdir(path.join(root, 'licenses'), {recursive: true});
await writeFile(path.join(root, 'licenses/THIRD_PARTY.txt'), notices);
const hostHtml = (await read('src/host.template.html'))
  .replace('/* __AMLL_CSS__ */',() => css.code)
  .replace('/* __AMLL_BUNDLE__ */',() => hostResult.outputFiles[0].text.replaceAll('</script','<\\/script'));
const assets = path.resolve(root,'../app/src/main/assets');
await mkdir(path.join(assets,'amll'),{recursive:true});
await mkdir(path.join(assets,'third_party'),{recursive:true});
await writeFile(path.join(assets,'amll/lyrics.html'),hostHtml);
await writeFile(path.join(assets,'third_party/AMLL-NOTICES.txt'),notices);
// Exercise the exact production HTML/renderer in a browser, with explicit PC-only controls.
const controls=`<style>#test-controls{position:fixed;z-index:10;top:0;left:0;right:0;height:180px;background:#17171c;color:#eee;padding:10px 14px;font:13px system-ui;display:flex;align-content:start;align-items:center;gap:8px;flex-wrap:wrap}#test-controls button,#test-controls select{min-height:36px;color:#fff;background:#343440;border:1px solid #555;border-radius:7px;padding:0 10px}#test-status{width:100%;font-size:12px;color:#bbb}#test-seek{width:140px}#lyrics,#ambient{position:fixed;top:180px;bottom:0;height:auto}#test-edge{position:absolute;top:160px;left:0;right:0;height:20px;background:var(--album-edge);z-index:11;font:11px system-ui;color:#ddd;padding:2px 14px}@media(max-width:400px){#test-controls{height:230px}#lyrics,#ambient{top:230px}#test-edge{top:210px}}</style>
<div id="test-controls"><button id="test-play">播放</button><select id="test-mode" aria-label="歌词类型"><option value="word">逐字示例</option><option value="line">逐行示例</option><option value="next">切换新歌</option><option value="empty">空数据</option></select><select id="test-rate" aria-label="播放速度"><option value="1">1x</option><option value="1.5">1.5x</option><option value="2">2x</option></select><label>偏移 <select id="test-offset" aria-label="歌词偏移"><option value="0">不偏移</option><option value="500">提前 0.5 秒</option><option value="-500">延后 0.5 秒</option><option value="10000">提前 10 秒</option><option value="-10000">延后 10 秒</option></select></label><label><input id="test-trans" type="checkbox" checked>译文</label><button id="test-stale">旧进度</button><input id="test-seek" type="range" aria-label="测试进度" min="0" max="30" step="0.01" value="0"><output id="test-time">0 s</output><select id="test-cover" aria-label="模拟封面"><option value="warm">暖色封面</option><option value="cool">冷色封面</option><option value="none">无封面</option><option value="broken">损坏封面</option></select><label><input id="test-visible" type="checkbox" checked>页面可见</label><button id="test-context-loss">背景故障</button><output id="test-background">背景</output><span id="test-status">电脑桥接检查 · 非手机实测</span></div><div id="test-edge">原生标题衔接色示意 · 非手机标题</div>`;
const harnessCode=await read('src/host-harness.js');
const harness=hostHtml.replace('<body>',()=>'<body>'+controls)
  .replace('<script>',()=>`<script>${model}</script><script>${harnessCode}</script><script>`);
await writeFile(path.join(root,'host-preview.html'),harness);
console.log(`Built offline AMLL preview: ${Math.round(Buffer.byteLength(html) / 1024)} KiB, ${packages.size} licensed packages`);
console.log(`Built Android local renderer: ${Math.round(Buffer.byteLength(hostHtml) / 1024)} KiB`);
