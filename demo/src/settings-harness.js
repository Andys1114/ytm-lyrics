import {previewState, previewCommand} from './settings-model.js';
import {offsetWheel} from './settings-offset-wheel.js';
let state = previewState();
const $ = id => document.getElementById(id);
const sync = $('quick-sync');
const toolbar = $('inline-sync');
const popup = $('offset-popup');
const toggle = () => state.hasSong ? command({action: 'sync'}) : notice('请先播放歌曲');
const updateWheel = offsetWheel(sync, $('inline-offset'), {
  commit: value => adjust({action: 'offset', value}),
  reveal: () => command({action: 'revealSync'}),
  toggle
});
window.settingsCommands = [];
const notice = message => { $('preview-feedback').textContent = message; };
const refresh = () => {
  window.YtmSettingsHost.update(state);
  $('settings').hidden = !state.sheetOpen;
  $('preview-player').dataset.lyrics = state.lyricsVisible ? 'open' : 'closed';
  $('preview-open-lyrics').hidden = state.lyricsVisible;
  toolbar.toggleAttribute('data-open', state.syncExpanded);
  popup.inert = !state.syncExpanded;
  popup.setAttribute('aria-hidden', String(!state.syncExpanded));
  updateWheel(state.offsetMs, state.syncExpanded, state.hasSong);
  $('inline-reset').disabled = !state.hasSong || state.offsetMs === 0;
};
const command = value => {
  const encoded = {...value, songKey: state.songKey}; window.settingsCommands.push(encoded);
  state = previewCommand(state, encoded); refresh();
};
window.YtmSettingsBridge = {
  ready() { refresh(); },
  failed() { notice('设置组件初始化失败'); },
  command(encoded) {
    const value = JSON.parse(encoded); window.settingsCommands.push(value);
    if (value.action === 'search') {
      const key = state.songKey;
      state = {...state, busy: true}; refresh();
      setTimeout(() => { if(state.songKey !== key) return; state = previewCommand({...state, busy: false}, value); refresh(); }, 600);
      return;
    }
    state = previewCommand(state, value); refresh();
    notice('电脑交互预览 · 不写入手机设置');
  }
};
$('preview-mode').addEventListener('change', event => {
  state = previewState();
  if (event.target.value === 'selected') { state.selectedSource = '网易云音乐'; state.quality = '逐字同步 · 含译文'; state.message = '已记住的选择'; state.bound = true; state.candidates[0].selected = true; }
  if (event.target.value === 'empty') { state.hasSong = false; state.songKey = ''; state.candidates = []; state.title = '还没有播放歌曲'; state.artist = '播放后可选择歌词、调整同步'; state.message = '播放歌曲后查询歌词'; }
  if (event.target.value === 'long') { state.title = '一首有很长很长名字的歌 / A Very Long Song Name (Live Acoustic Version)'; state.artist = '歌手甲、歌手乙 feat. Another Artist'; state.message = '来源暂时不可用，保留原生歌词。可以重新获取，或选择其他已启用的来源。'; }
  if (event.target.value === 'error') state.rendererError = '逐字显示恢复已达上限，点“重新获取”再试';
  state = previewCommand(state, {action: 'choose'}); refresh(); notice('电脑交互预览 · 不写入手机设置');
});
$('preview-close-lyrics').addEventListener('click', () => command({action: 'leaveLyrics'}));
$('preview-open-lyrics').addEventListener('click', () => command({action: 'enterLyrics'}));
for (const route of ['choose', 'settings']) $('quick-' + route).addEventListener('click', () => command({action: route}));
const adjust = value => { if(state.syncExpanded && state.hasSong) command(value); };
document.addEventListener('pointerdown', event => {
  if (state.syncExpanded && !toolbar.contains(event.target))
    command({action: 'collapseSync'});
});
document.addEventListener('keydown', event => {
  if (event.key !== 'Escape' || !state.syncExpanded) return;
  command({action: 'collapseSync'}); sync.focus();
});
$('inline-reset').addEventListener('click', () => adjust({action: 'offset', value: 0}));
