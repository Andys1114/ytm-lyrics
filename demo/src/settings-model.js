export const LIMIT_MS = 10_000;
export const STEP_MS = 500;
// Preserve the earlier command/readout precision; only new UI gestures snap to half seconds.
export const clampOffset = value => Math.max(-LIMIT_MS, Math.min(LIMIT_MS, Math.round(Number(value) / 100) * 100)) || 0;
export const sliderOffset = value => Math.max(-LIMIT_MS, Math.min(LIMIT_MS, Math.round(Number(value) / STEP_MS) * STEP_MS)) || 0;
export function nudgeOffset(value, direction) {
  const current = clampOffset(value);
  return clampOffset(direction > 0 ? (Math.floor(current / STEP_MS) + 1) * STEP_MS
    : direction < 0 ? (Math.ceil(current / STEP_MS) - 1) * STEP_MS : current);
}
export function stepByOffset(value, detents) {
  const direction = Math.sign(detents);
  return clampOffset(nudgeOffset(value, direction) + (detents - direction) * STEP_MS);
}
export function offsetLabel(value) {
  const offset = clampOffset(value);
  return offset === 0 ? '与播放同步' : offset > 0 ? '歌词提前' : '歌词延后';
}
export function offsetAmount(value) { return (Math.abs(clampOffset(value)) / 1000).toFixed(1); }
export function compactOffsetLabel(value) {
  const offset=clampOffset(value);
  return offset===0 ? '0s' : `${offset>0?'+':'−'}${Number(offsetAmount(offset))}s`;
}

export function durationLabel(ms) { return ms > 0 ? `${Math.floor(ms / 60000)}:${String(Math.floor(ms / 1000) % 60).padStart(2, '0')}` : '时长未知'; }
export const sameCandidate = (left, right) => left?.sourceId === right?.sourceId && left?.id === right?.id;
// Browser harness only. The APK always receives snapshots from the native runtime.
export function previewState() {
  return {lyricsVisible: true, syncExpanded: false, sheetOpen: false, route: 'choose', songKey: 'preview-song', query: 'ユリイカ ロクデナシ', busy: false, bound: false, candidates: previewCandidates(), hasSong: true, title: 'ユリイカ - Eureka', artist: 'ロクデナシ',
    message: '找到候选但匹配不确定，请选择对应的演唱版本', selectedSource: '', quality: '', rendererError: '',
    translation: true, preload: true, preloadLabel: '队列预载 3/3', offsetMs: 0,
    sources: [{id: 'netease', name: '网易云音乐', enabled: true}, {id: 'qqmusic', name: 'QQ 音乐', enabled: true},
      {id: 'lrclib', name: 'LRCLIB', enabled: false}]};
}
export function previewCandidates() {
  return [
    {sourceId: 'netease', id: 'ne-1', title: 'ユリイカ', artist: 'ロクデナシ', album: 'ユリイカ', durationMs: 217000, source: '网易云音乐', words: true, translation: true, selected: false},
    {sourceId: 'qqmusic', id: 'qq-1', title: 'ユリイカ - Eureka (Live Acoustic Version)', artist: 'ロクデナシ feat. Another Artist', album: 'A Long Album Name / 现场录音精选', durationMs: 219000, source: 'QQ 音乐', words: true, translation: false, selected: false},
    {sourceId: 'netease', id: 'ne-2', title: 'ユリイカ (Instrumental)', artist: 'ロクデナシ', album: 'ユリイカ', durationMs: 217000, source: '网易云音乐', words: false, translation: true, selected: false}
  ];
}
export function previewCommand(state, command) {
  if (command.songKey && command.songKey !== state.songKey && ['search', 'select', 'offset', 'step', 'clearBinding', 'confirmClear'].includes(command.action)) return state;
  const next = {...state, sources: state.sources.map(source => ({...source}))};
  switch (command.action) {
    case 'enterLyrics': next.lyricsVisible = true; break;
    case 'leaveLyrics': next.lyricsVisible = false; next.syncExpanded = false; break;
    case 'sync': if (state.hasSong && state.lyricsVisible) { next.syncExpanded = !state.syncExpanded; next.sheetOpen = false; } break;
    case 'revealSync': if (state.hasSong && state.lyricsVisible && !state.sheetOpen) next.syncExpanded = true; break;
    case 'collapseSync': next.syncExpanded = false; break;
    case 'close': next.sheetOpen = false; break;
    case 'choose': case 'settings': case 'confirmClear': next.route = command.action; next.syncExpanded = false; next.sheetOpen = true; break;
    case 'search': next.query = command.value; next.candidates = /无结果|none/i.test(command.value) ? [] : previewCandidates(); next.message = next.candidates.length ? '请选择对应的演唱版本' : '没有搜索到带时间轴的歌词'; break;
    case 'clearBinding': next.bound = false; next.route = 'choose'; next.selectedSource = ''; next.message = '已清除本曲选择，恢复自动匹配'; next.candidates = next.candidates.map(c => ({...c, selected: false})); break;
    case 'select': { const selected = state.candidates.find(c => sameCandidate(c, {sourceId: command.sourceId, id: command.value})); if (!selected) break;
      next.bound = true; next.selectedSource = selected.source; next.quality = (selected.words ? '逐字同步' : '逐行同步') + (selected.translation ? ' · 含译文' : '');
      next.candidates = state.candidates.map(c => ({...c, selected: sameCandidate(c, selected)})); next.message = '已记住的选择'; next.sheetOpen = false; break; }
    case 'offset': if (state.hasSong) next.offsetMs = clampOffset(command.value); break;
    case 'step': if (state.hasSong) next.offsetMs = Math.abs(command.value) === STEP_MS
      ? nudgeOffset(state.offsetMs, command.value) : clampOffset(state.offsetMs + command.value); break;
    case 'translation': next.translation = command.value; break;
    case 'preload': next.preload = command.value; next.preloadLabel = command.value ? '队列预载 3/3' : '预载已关闭'; break;
    case 'source': { const source = next.sources.find(row => row.id === command.sourceId); if (source) source.enabled = command.value; break; }
    case 'moveUp': { const index = next.sources.findIndex(row => row.id === command.sourceId);
      if (index > 0) [next.sources[index - 1], next.sources[index]] = [next.sources[index], next.sources[index - 1]]; break; }
    case 'retry': next.message = '已重新比较可用来源'; next.selectedSource = '网易云音乐'; next.quality = '逐字同步 · 含译文'; break;
    default: break;
  }
  return next;
}
