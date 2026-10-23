export function mediaPositionAt(sample, now) {
  const time = sample.positionMs + (sample.playing ? Math.max(0, now - sample.receivedAt) * sample.speed : 0);
  return Math.max(0, sample.durationMs > 0 ? Math.min(sample.durationMs, time) : time);
}
export function positionAt(sample, now) {
  return mediaPositionAt(sample,now) + (sample.offsetMs ?? 0);
}
export function isSeek(previous, next, now) {
  return previous.serial !== next.serial || previous.epoch !== next.epoch
    || previous.playing !== next.playing || previous.speed !== next.speed
    || (previous.offsetMs ?? 0) !== (next.offsetMs ?? 0)
    || Math.abs(positionAt(previous, now) - positionAt(next, now)) > 350;
}
