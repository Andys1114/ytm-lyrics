import 'mdui/components/button.js';
import 'mdui/components/button-icon.js';
import 'mdui/components/switch.js';
import 'mdui/components/text-field.js';
import 'mdui/components/list.js';
import 'mdui/components/list-item.js';
import {durationLabel} from './settings-model.js';

const $ = id => document.getElementById(id);
let state;
let lastSong, lastRoute, candidateSignature = '';
let sourceOrder = '';
const bridge = command => window.YtmSettingsBridge?.command(JSON.stringify({...command, songKey: state?.songKey ?? ''}));
const action = (name, value) => bridge({action: name, ...(value === undefined ? {} : {value})});
const text = (id, value) => { if ($(id).textContent !== value) $(id).textContent = value; };
const arrow = '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m6 12 6-6 6 6M12 6v13"/></svg>';
// MDUI 2.1.5 does not forward host aria-label to its shadow input/button.
// Label the real focusable control without changing the upstream component.
function labelControl(element, label) {
  element.setAttribute('aria-label', label);
  element.updateComplete.then(() => element.shadowRoot.querySelector('input,button')?.setAttribute('aria-label', label));
}
function activateFromLabel(label, toggle) {
  label.addEventListener('click', event => { event.preventDefault(); toggle.click(); });
}
function renderSources() {
  const order = state.sources.map(source => source.id).join(',');
  if (order !== sourceOrder) {
    const focused = document.activeElement?.id;
    const rows = new Map([...$('sources').children].map(row => [row.dataset.id, row]));
    for (const source of state.sources) {
      let row = rows.get(source.id);
      if (!row) {
        row = document.createElement('div'); row.className = 'source-row'; row.dataset.id = source.id;
        const rank = document.createElement('span'); rank.className = 'source-rank'; rank.setAttribute('aria-hidden', 'true');
        const label = document.createElement('label'); label.className = 'source-name'; label.htmlFor = `source-${source.id}`;
        const name = document.createElement('span'); name.className = 'name';
        const hint = document.createElement('small'); hint.className = 'source-hint'; label.append(name, hint);
        const up = document.createElement('mdui-button-icon'); up.innerHTML = arrow; up.id = `up-${source.id}`;
        up.addEventListener('click', () => bridge({action: 'moveUp', sourceId: source.id}));
        const toggle = document.createElement('mdui-switch'); toggle.id = `source-${source.id}`;
        toggle.addEventListener('change', () => bridge({action: 'source', sourceId: source.id, value: toggle.checked}));
        activateFromLabel(label, toggle);
        row.append(rank, label, up, toggle);
      }
      rows.delete(source.id); $('sources').append(row);
    }
    for (const row of rows.values()) row.remove();
    sourceOrder = order;
    if (focused) document.getElementById(focused)?.focus({preventScroll: true});
  }
  for (const [index, source] of state.sources.entries()) {
    const row = [...$('sources').children].find(item => item.dataset.id === source.id);
    row.dataset.enabled = String(source.enabled);
    row.querySelector('.source-rank').textContent = String(index + 1).padStart(2, '0');
    row.querySelector('.name').textContent = source.name;
    row.querySelector('.source-hint').textContent = source.enabled ? (index === 0 ? '优先查询' : '已启用') : '已关闭';
    const up = row.querySelector('mdui-button-icon'); up.disabled = index === 0;
    labelControl(up, `上移${source.name}`); up.title = `上移${source.name}`;
    const toggle = row.querySelector('mdui-switch'); toggle.checked = source.enabled;
    labelControl(toggle, `启用${source.name}`);
  }
}
function renderCandidates() {
  const signature = JSON.stringify([state.songKey, state.candidates, state.busy]);
  if (signature === candidateSignature) return;
  candidateSignature = signature;
  const list = $('candidates'); list.replaceChildren();
  for (const candidate of state.candidates ?? []) {
    const row = document.createElement('mdui-list-item'); row.className = 'candidate';
    row.active = candidate.selected; row.disabled = state.busy;
    row.setAttribute('role', 'button'); row.tabIndex = 0;
    row.setAttribute('aria-pressed', String(candidate.selected));
    row.setAttribute('aria-disabled', String(state.busy));
    row.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); row.click(); } });
    const content = document.createElement('div'); content.slot = 'custom'; content.className = 'candidate-copy';
    const title = document.createElement('h3'); title.textContent = candidate.title;
    const artist = document.createElement('p'); artist.className = 'candidate-artist'; artist.textContent = candidate.artist || '歌手未知';
    const album = document.createElement('p'); album.className = 'candidate-album'; album.textContent = candidate.album || '专辑未知';
    const meta = document.createElement('div'); meta.className = 'candidate-meta';
    for (const value of [candidate.source, durationLabel(candidate.durationMs), candidate.words ? '逐字' : '逐行', ...(candidate.translation ? ['译文'] : [])]) {
      const chip = document.createElement('span'); chip.textContent = value; meta.append(chip);
    }
    if (candidate.selected) { const check = document.createElement('span'); check.className = 'selected-label'; check.textContent = '正在使用'; content.append(check); }
    content.append(title, artist, album, meta); row.append(content);
    row.addEventListener('click', () => { if (!state.busy) bridge({action: 'select', sourceId: candidate.sourceId, value: candidate.id}); });
    list.append(row);
  }
}
function update(next) {
  state = next;
  const route = ['choose', 'confirmClear'].includes(next.route) ? next.route : 'settings';
  $('settings').dataset.route = route;
  text('sheet-title', {choose: '选择歌词', settings: '歌词设置', confirmClear: '清除选择'}[route]);
  for (const name of ['choose', 'settings', 'confirm']) $(name + '-page').hidden = (name === 'confirm' ? route !== 'confirmClear' : route !== name);
  for (const name of ['choose', 'settings']) {
    $('nav-' + name).variant = route === name || (name === 'choose' && route === 'confirmClear') ? 'tonal' : 'text';
    $('nav-' + name).setAttribute('aria-current', route === name ? 'page' : 'false');
  }
  if (lastSong !== next.songKey) { $('query').value = next.query ?? ''; lastSong = next.songKey; }
  if (lastRoute !== route) { document.querySelector('.content').scrollTop = 0; lastRoute = route; }
  $('query').disabled = !next.hasSong;
  $('search').disabled = !next.hasSong || next.busy;
  $('search').loading = next.busy;
  text('result-count', next.busy ? '查询中…' : `${next.candidates?.length ?? 0} 个版本`);
  text('search-note', next.busy ? '正在查询已启用的来源…' : '按匹配度排列，点选后记住本曲。');
  $('empty-results').hidden = Boolean(next.candidates?.length);
  text('empty-title', next.busy ? '正在寻找可同步的歌词' : next.hasSong ? '还没有可用的歌词' : '请先播放一首歌');
  text('empty-help', next.busy ? '查询完成后，版本会显示在这里。' : '换一个歌名或歌手搜索，只显示可同步的版本。');
  $('clear-binding').hidden = !next.bound;
  text('saved-note', route === 'choose' ? '只显示逐字或逐行歌词' : route === 'confirmClear' ? '' : '更改即时保存');
  renderCandidates();
  text('song-title', next.title); text('song-artist', next.artist);
  const notice = next.busy || (route === 'choose' && !next.candidates?.length) || /失败|正在|没有|不可用|错误/.test(next.message);
  text('status', next.selectedSource ? `${next.selectedSource} · ${next.quality}${notice ? `\n${next.message}` : ''}` : next.message);
  $('status').parentElement.dataset.selected = String(Boolean(next.selectedSource));
  text('renderer-error', next.rendererError); $('renderer-error').hidden = !next.rendererError;
  $('retry').disabled = !next.hasSong;
  $('translation').checked = next.translation; $('preload').checked = next.preload;
  text('preload-status', next.preloadLabel);
  renderSources();
}
$('close').addEventListener('click', () => action('close'));
for (const name of ['choose', 'settings']) $('nav-' + name).addEventListener('click', () => action(name));
const search = () => { const value = $('query').value.trim(); if (value && !state.busy) action('search', value); else if (!value) { $('query').setCustomValidity('请输入歌名或歌手'); $('query').reportValidity(); } };
$('search').addEventListener('click', search);
$('query').addEventListener('input', () => $('query').setCustomValidity(''));
$('query').addEventListener('keydown', event => { if (event.key === 'Enter') { event.preventDefault(); search(); } });
$('clear-binding').addEventListener('click', () => action('confirmClear'));
$('confirm-clear').addEventListener('click', () => action('clearBinding'));
$('cancel-clear').addEventListener('click', () => action('choose'));
$('retry').addEventListener('click', () => action('retry'));
$('translation').addEventListener('change', () => action('translation', $('translation').checked));
$('preload').addEventListener('change', () => action('preload', $('preload').checked));
document.addEventListener('keydown', event => { if (event.key === 'Escape') action('close'); });
window.YtmSettingsHost = {update};
for (const [id, label] of [['close', '完成并关闭设置'], ['query', '歌名或歌手'], ['translation', '显示来源译文'],
  ['preload', '预载接下来三首歌词']]) labelControl($(id), label);
for (const id of ['translation', 'preload']) activateFromLabel(document.querySelector(`label[for="${id}"]`), $(id));
Promise.all(['mdui-button', 'mdui-button-icon', 'mdui-switch', 'mdui-text-field', 'mdui-list-item'].map(name => customElements.whenDefined(name)))
  .then(() => window.YtmSettingsBridge?.ready()).catch(() => window.YtmSettingsBridge?.failed());
