import {LIMIT_MS, STEP_MS, stepByOffset, offsetAmount, compactOffsetLabel} from './settings-model.js';

export function offsetWheel(chip, wheel, {commit, reveal, toggle}) {
  let value = 0, open = false, enabled = false, press, scroll = 0, fraction = 0;
  let settling = 0;
  const reducedMotion = matchMedia('(prefers-reduced-motion: reduce)');
  const detents = [...wheel.querySelectorAll('.wheel-detent')];
  const chipLabel = chip.querySelector('#inline-value');
  const wheelLabel = wheel.querySelector('#wheel-value');
  const slot = 80 / 3;
  const change = target => { if (enabled && target !== value) commit(target); };
  const step = direction => change(stepByOffset(value, direction));
  const draw = () => {
    if (!open) return;
    const pillHalf = wheelLabel.offsetHeight / 2;
    detents.forEach((label, index) => {
      const i = [-2, -1, 1, 2][index], next = stepByOffset(value, i), y = (i - fraction) * slot;
      const visible = next !== value && (Math.abs(i) === 1 || next !== stepByOffset(value, Math.sign(i)));
      label.style.setProperty('--detent-alpha', visible ? Math.max(0, Math.min(1, (Math.abs(y) - pillHalf) / 8, (40 - Math.abs(y)) / 12)) : 0);
      label.style.translate = `0 ${y}px`;
      label.textContent = (next < 0 ? '−' : next > 0 ? '+' : '') + Math.abs(next) / 1000;
    });
  };
  const stopSettle = () => { cancelAnimationFrame(settling); settling = 0; fraction = 0; };
  const settleFrom = start => {
    stopSettle();
    if (Math.abs(start) < .01 || reducedMotion.matches) { draw(); return; }
    fraction = start;
    const began = performance.now();
    const frame = now => {
      const remaining = Math.max(0, 1 - (now - began) / 135);
      fraction = start * remaining * remaining;
      draw();
      settling = remaining > 0 ? requestAnimationFrame(frame) : 0;
    };
    draw();
    settling = requestAnimationFrame(frame);
  };
  const finish = () => {
    const current = press;
    press = undefined; fraction = 0;
    if (current?.target.hasPointerCapture(current.id)) current.target.releasePointerCapture(current.id);
    draw();
  };
  for (const target of [chip, wheel]) {
    target.addEventListener('pointerdown', event => {
      if (!enabled || event.button !== 0 || press) return;
      stopSettle(); draw();
      press = {target, id: event.pointerId, y: event.clientY, origin: value, wasOpen: open, dragged: false};
      target.setPointerCapture(event.pointerId);
      if (!open) reveal();
    });
    target.addEventListener('pointermove', event => {
      if (press?.id !== event.pointerId || press.target !== target) return;
      const dy = press.y - event.clientY;
      if (Math.abs(dy) > 8) press.dragged = true;
      if (!press.dragged) return;
      const lower = Math.floor((-LIMIT_MS - press.origin) / STEP_MS);
      const upper = Math.ceil((LIMIT_MS - press.origin) / STEP_MS);
      const travel = Math.max(lower, Math.min(upper, dy / slot));
      const ticks = Math.sign(travel) * Math.floor(Math.abs(travel) + .5);
      fraction = travel - ticks;
      change(stepByOffset(press.origin, ticks));
      draw();
    });
    target.addEventListener('pointerup', event => {
      if (press?.id !== event.pointerId || press.target !== target) return;
      const {wasOpen, dragged} = press, remainder = fraction;
      finish();
      if (dragged) { settleFrom(remainder); return; }
      if (!wasOpen) return;
      if (target === wheel) {
        const bounds = wheel.getBoundingClientRect(), dy = event.clientY - bounds.top - bounds.height / 2;
        if (Math.abs(dy) > wheelLabel.offsetHeight / 2) { step(Math.sign(dy)); return; }
      }
      toggle();
    });
    for (const type of ['pointercancel','lostpointercapture']) target.addEventListener(type, event => {
      if (press?.id === event.pointerId && press.target === target) finish();
    });
    target.addEventListener('wheel', event => {
      if (!enabled || event.ctrlKey || press || !event.deltaY) return;
      if (!open) reveal();
      event.preventDefault();
      if (Math.sign(event.deltaY) !== Math.sign(scroll)) scroll = 0;
      scroll += event.deltaMode === 0 ? event.deltaY : Math.sign(event.deltaY) * 36;
      if (Math.abs(scroll) >= 36) { step(Math.sign(scroll)); scroll = 0; }
    }, {passive: false});
    target.addEventListener('pointerleave', () => { scroll = 0; });
    target.addEventListener('keydown', event => {
      if (target === wheel && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); toggle(); return; }
      if (!open || !enabled) return;
      switch (event.key) {
        case 'ArrowUp': case 'ArrowRight': step(1); break;
        case 'ArrowDown': case 'ArrowLeft': step(-1); break;
        case 'Home': change(-LIMIT_MS); break;
        case 'End': change(LIMIT_MS); break;
        default: return;
      }
      event.preventDefault();
    });
  }
  chip.addEventListener('click', event => { if (event.detail === 0 || !enabled) toggle(); });
  return (offset, expanded, hasSong) => {
    const stepped = expanded && open && !press && value !== offset;
    const direction = Math.sign(value - offset);
    value = offset; open = expanded; enabled = hasSong;
    if (!open || !enabled) { stopSettle(); if (press) finish(); }
    chipLabel.textContent = wheelLabel.textContent = value === 0 ? '偏移' : compactOffsetLabel(value);
    const text = value ? `${value > 0 ? '提前' : '延后'} ${offsetAmount(value)} 秒` : '不偏移';
    chip.setAttribute('aria-expanded', String(open));
    chip.setAttribute('aria-label', `${open ? '收起' : '展开'}歌词偏移，${text}`);
    wheel.setAttribute('aria-valuenow', value/1000);
    wheel.setAttribute('aria-valuetext', text);
    if (stepped) settleFrom(direction * .23); else draw();
  };
}
