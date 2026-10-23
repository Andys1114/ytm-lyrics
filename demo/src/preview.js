import { LyricPlayer, LayoutReason } from '@applemusic-like-lyrics/core';

// The only playback authority is this anchored simulation clock. AMLL owns all
// word masks, emphasis, line layout and spring motion; no audio is created.
const model = DemoTimeline;
const play = document.querySelector('#play');
const playLabel = document.querySelector('#play-label');
const seek = document.querySelector('#seek');
const currentTime = document.querySelector('#current-time');
const status = document.querySelector('#play-status');
const statusText = document.querySelector('#status-text');
const translationToggle = document.querySelector('#show-translation');
const host = document.querySelector('.viewport');
const followLabel = document.querySelector('.follow');
const phrase = document.querySelector('.phrase-status');
const ambient = document.querySelector('.ambient');
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const player = new LyricPlayer();
host.append(player.getElement());
player.setAlignAnchor('top');
player.setAlignPosition(.25);
player.setWordFadeWidth(.8);
player.setEnableBlur(true);
player.setEnableScale(true);
player.setEnableSpring(!reducedMotion.matches);

let anchor = 0, startedAt = performance.now(), playing = false;
let heldUntil = 0, holdTimer = 0, lastFrame = performance.now();
const position = (now = performance.now()) => model.positionAt(anchor, startedAt, now, playing);

function releaseHold() {
  clearTimeout(holdTimer);
  heldUntil = 0;
  followLabel.textContent = '自动跟随';
  followLabel.classList.remove('is-free');
}
function hold() {
  clearTimeout(holdTimer);
  heldUntil = performance.now() + 3000;
  followLabel.classList.add('is-free');
  function tick() {
    const left = heldUntil - performance.now();
    if (left > 0) {
      followLabel.textContent = `自由浏览 · ${Math.ceil(left / 1000)}s`;
      holdTimer = setTimeout(tick, Math.min(250, left));
    } else {
      releaseHold();
      player.resetScroll();
      // resetScroll clears the interaction offset but does not itself lay out.
      // A paused timeline will not produce a new focus change to do that for us.
      player.calcLayout(LayoutReason.ConfigChange);
    }
  }
  tick();
}
function updateControls(ms) {
  play.setAttribute('aria-pressed', String(playing));
  playLabel.textContent = playing ? '暂停' : ms >= model.duration ? '重播' : '播放';
  status.classList.toggle('is-playing', playing);
  const active = model.activeLine(ms), line = model.lines[active];
  const text = ms >= model.duration ? '已结束' : !playing ? '已暂停' : ms >= line.end ? '句间停顿' : '播放中';
  if (statusText.textContent !== text) statusText.textContent = text;
  currentTime.value = model.format(ms);
  seek.value = String(ms / 1000);
  seek.style.setProperty('--progress', `${ms / model.duration * 100}%`);
  seek.setAttribute('aria-valuetext', `${(ms / 1000).toFixed(1)} 秒，共 30 秒`);
  phrase.textContent = `${String(active + 1).padStart(2, '0')} / 07`;
  // The decorative backdrop freezes with the simulation, including while paused.
  ambient.style.setProperty('--ambient-x', `${Math.sin(ms / 7000) * 55}px`);
  ambient.style.setProperty('--ambient-y', `${Math.cos(ms / 8500) * 35}px`);
  ambient.style.setProperty('--ambient-angle', `${Math.sin(ms / 9500) * 12}deg`);
}
function seekTo(ms) {
  anchor = model.clamp(ms, 0, model.duration);
  startedAt = performance.now();
  releaseHold();
  player.resetScroll();
  if (anchor >= model.duration) { playing = false; player.pause(); }
  player.setCurrentTime(anchor, true);
  player.update(0);
  updateControls(anchor);
}
function setPlaying(value) {
  const now = performance.now();
  anchor = position(now); startedAt = now;
  if (value && anchor >= model.duration) seekTo(0);
  playing = value;
  if (playing) player.resume(); else player.pause();
  player.setCurrentTime(anchor, true);
  player.update(0);
  updateControls(anchor);
}
function loadLines(ms) {
  player.setLyricLines(model.toAmllLines(translationToggle.checked), ms);
  player.setCurrentTime(ms, true);
  if (playing) player.resume(); else player.pause();
  player.update(0);
}

play.addEventListener('click', () => setPlaying(!playing));
document.querySelector('#restart').addEventListener('click', () => seekTo(0));
seek.addEventListener('input', () => seekTo(Number(seek.value) * 1000));
translationToggle.addEventListener('change', () => {
  // Rebuild through the public API so AMLL recalculates wrapping and row heights.
  const ms = position(); anchor = ms; startedAt = performance.now();
  loadLines(ms); updateControls(ms);
});
host.addEventListener('wheel', hold, {passive: true, capture: true});
host.addEventListener('touchmove', hold, {passive: true, capture: true});
host.addEventListener('touchend', () => { if (heldUntil) hold(); }, {passive: true});
host.addEventListener('keydown', event => {
  const distances = {ArrowDown: 55, ArrowUp: -55, PageDown: host.clientHeight * .65, PageUp: -host.clientHeight * .65};
  if (!(event.key in distances)) return;
  event.preventDefault();
  player.getElement().dispatchEvent(new WheelEvent('wheel', {deltaY: distances[event.key], bubbles: true, cancelable: true}));
});
// Clicking an actual AMLL lyric row seeks the shared simulation.
player.addEventListener('line-click', event => {
  // AMLL may extend its visual focus window into the preceding gap. Seek to
  // our original media timestamp, not that normalized display window.
  const line = model.lines[event.lineIndex];
  if (line) seekTo(line.start);
});
reducedMotion.addEventListener('change', () => player.setEnableSpring(!reducedMotion.matches));
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) {
    lastFrame = performance.now();
    player.setCurrentTime(position(lastFrame), true);
  }
});
window.addEventListener('pagehide', event => {
  if (event.persisted) return; // A back/forward-cache entry will resume this view.
  clearTimeout(holdTimer);
  player.dispose();
});

loadLines(0);
updateControls(0);
// update(delta) advances AMLL's springs; elapsed media time is never accumulated.
function frame(now) {
  const ms = position(now);
  if (playing && ms >= model.duration) {
    anchor = model.duration; startedAt = now; playing = false; player.pause();
  }
  player.setCurrentTime(ms);
  player.update(Math.min(50, Math.max(0, now - lastFrame)));
  lastFrame = now;
  updateControls(ms);
  requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
