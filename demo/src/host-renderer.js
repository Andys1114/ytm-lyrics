import {LyricPlayer, LayoutReason, MeshGradientRenderer} from '@applemusic-like-lyrics/core';
import {positionAt, mediaPositionAt, isSeek} from './host-clock.js';
import {AlbumBackground} from './album-background.js';

const host = document.querySelector('#lyrics');
function failure(message) {
  document.querySelector('#render-error').textContent = 'AMLL 渲染失败';
  window.YtmLyricsBridge?.failed(String(message).slice(0,200));
}
window.addEventListener('error', event => failure(event.message));
window.addEventListener('unhandledrejection', event => failure(event.reason?.message ?? '组件异常'));
const player = new LyricPlayer();
host.append(player.getElement());
player.setAlignAnchor('top');
player.setAlignPosition(.26);
player.setWordFadeWidth(1);
const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
const ambient=document.querySelector('#ambient');
const background=new AlbumBackground({
  createRenderer() {
    const canvas=document.createElement('canvas');
    ambient.append(canvas);
    try {
      const renderer=new MeshGradientRenderer(canvas);
      canvas.addEventListener('webglcontextlost',()=>background.fail(new Error('背景图形上下文丢失')),{once:true});
      return renderer;
    } catch(error) {canvas.remove();throw error;}
  },
  async loadImage(uri) {
    if(!/^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(uri))throw new Error('封面不是本地 JPEG');
    const image=new Image();image.src=uri;await image.decode();return image;
  },
  paint(frame) {
    const color=/^#[a-f0-9]{6}$/i.test(frame.color)?frame.color:'#24242b';
    document.documentElement.style.setProperty('--album-edge',color);
    // This blurred image remains underneath the WebGL canvas, so losing the
    // background context never takes away the lyric player or its controls.
    const image=/^data:image\/jpeg;base64,[A-Za-z0-9+/=]+$/.test(frame.image)?frame.image:'';
    ambient.style.setProperty('--album-image',image?`url("${image}")`:'none');
  },
  state(value) {ambient.dataset.state=value;},
  report(error) {window.YtmLyricsBridge?.backgroundFailed?.(String(error.message??error).slice(0,160));}
});
player.setEnableSpring(!reducedMotion.matches);
// The source's millisecond timestamps remain the media authority.
player.setOptimizeOptions({tryAdvanceStartTime:false});
let sample = {serial:0,positionMs:0,receivedAt:performance.now(),playing:false,speed:1,epoch:0,durationMs:0};
let lines = [], showTranslation = true, visible = !document.hidden, requestedVisible = true, frameId = 0, lastFrame = performance.now();
let holdTimer = 0, heldUntil = 0;
function syncBackground() {background.setPlayback(visible && !document.hidden,sample.playing,reducedMotion.matches);}
function loadLines(ms) {
  const rows = lines.map(line => ({startTime:line.startTime,endTime:line.endTime,
    words:line.words.length ? line.words : [{word:line.text,startTime:line.startTime,endTime:line.endTime}],
    translatedLyric:showTranslation ? line.translatedLyric : '',romanLyric:'',isBG:false,isDuet:false}));
  player.setLyricLines(rows, ms);
  player.setCurrentTime(ms,true);
  if (sample.playing && visible) player.resume(); else player.pause();
  syncBackground();
  player.update(0);
}
function sync(next) {
  if (next.serial !== sample.serial) return;
  const now = performance.now();
  const aligned = {...next,durationMs:sample.durationMs,receivedAt:now};
  const seek = isSeek(sample,aligned,now);
  sample = aligned;
  player.setCurrentTime(positionAt(sample,now),seek);
  if (sample.playing && visible) player.resume(); else player.pause();
  syncBackground();
  player.update(0);
  schedule();
}
function resumeFollow() {
  heldUntil = 0;
  player.resetScroll();
  player.calcLayout(LayoutReason.ConfigChange);
}
function hold() {
  clearTimeout(holdTimer); heldUntil = performance.now() + 3000;
  holdTimer = setTimeout(resumeFollow,3000);
}
host.addEventListener('wheel',hold,{capture:true,passive:true});
host.addEventListener('touchmove',hold,{capture:true,passive:true});
host.addEventListener('touchend',() => {if (heldUntil) hold();},{passive:true});
host.addEventListener('keydown',event => {
  const amount = {ArrowUp:-55,ArrowDown:55,PageUp:-host.clientHeight*.65,PageDown:host.clientHeight*.65}[event.key];
  if (amount === undefined) return;
  event.preventDefault();
  player.getElement().dispatchEvent(new WheelEvent('wheel',{deltaY:amount,bubbles:true,cancelable:true}));
});
player.addEventListener('line-click',event => {
  const line = lines[event.lineIndex];
  if (line) window.YtmLyricsBridge?.seek(sample.serial,line.startTime);
});
function frame(now) {
  frameId = 0;
  if (!visible) return;
  player.setCurrentTime(positionAt(sample,now));
  player.update(Math.min(50,Math.max(0,now-lastFrame))); lastFrame = now;
  // AMLL's word animations default to 1x. Keep their Web Animations playback
  // rates aligned with YTM; never substitute invented word timestamps.
  const rate = sample.speed > 0 ? sample.speed : 1;
  for (const animation of player.getElement().getAnimations({subtree:true})) {
    if (animation.id && animation.playbackRate !== rate) animation.playbackRate = rate;
  }
  schedule();
}
function schedule() { if (visible && !frameId) frameId = requestAnimationFrame(frame); }
function applyVisibility() {
  visible=requestedVisible && !document.hidden;
  if (visible) {lastFrame=performance.now(); if(sample.playing)player.resume();schedule();}
  else {if(frameId)cancelAnimationFrame(frameId);frameId=0;player.pause();}
  syncBackground();
}
window.YtmLyricsHost = Object.freeze({
  setArtwork:frame=>background.setArtwork(frame),
  setData(data) {
    clearTimeout(holdTimer); resumeFollow();
    lines = data.lines; showTranslation = data.translation;
    sample = {serial:data.serial,positionMs:0,receivedAt:performance.now(),playing:false,speed:1,epoch:0,durationMs:data.durationMs};
    host.classList.toggle('line-only',!data.wordTimed);
    loadLines(0); schedule();
  },
  sync,
  setTranslation(enabled) {
    const now = performance.now(); sample = {...sample,positionMs:mediaPositionAt(sample,now),receivedAt:now};
    showTranslation = enabled; loadLines(positionAt(sample,now));
  },
  setVisible(value) {
    requestedVisible = value;applyVisibility();
  }
});
reducedMotion.addEventListener('change',() => {player.setEnableSpring(!reducedMotion.matches);syncBackground();});
document.addEventListener('visibilitychange',applyVisibility);
window.addEventListener('pagehide',()=>background.dispose(),{once:true});
window.YtmLyricsBridge?.ready();
schedule();
