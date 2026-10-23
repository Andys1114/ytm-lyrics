// Tests the timing data/adapter actually embedded in the built offline page.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const html = fs.readFileSync(path.join(__dirname, 'lyrics-preview.html'), 'utf8');
const source = html.match(/<script id="timeline-model">([\s\S]*?)<\/script>/)[1];
const context = vm.createContext({});
vm.runInContext(source + '\nthis.model = DemoTimeline;', context);
const m = context.model;
assert.equal(m.duration, 30000);
assert.equal(m.lines.length, 7);
assert.ok(m.lines.flatMap(line => line.words).some(word => word.end - word.start >= 1000), 'Fixture must exercise AMLL sustained-word emphasis');
const mapped = m.toAmllLines(true), untranslated = m.toAmllLines(false);
for (const [index, line] of m.lines.entries()) {
  assert.equal(line.words.map(w => w.text).join(''), line.text);
  assert.ok(line.translation.length > 0);
  assert.equal(line.words[0].start, line.start);
  assert.equal(line.words.at(-1).end, line.end);
  assert.ok(line.end <= (m.lines[index + 1]?.start ?? m.duration));
  assert.equal(mapped[index].startTime, line.start);
  assert.equal(mapped[index].endTime, line.end);
  assert.equal(mapped[index].translatedLyric, line.translation);
  assert.equal(untranslated[index].translatedLyric, '');
  assert.equal(mapped[index].isBG, false);
  assert.equal(mapped[index].isDuet, false);
  assert.equal(mapped[index].romanLyric, '');
  for (const [wordIndex, word] of line.words.entries()) {
    assert.ok(word.end > word.start);
    assert.ok(word.start >= (line.words[wordIndex - 1]?.end ?? line.start));
    assert.equal(mapped[index].words[wordIndex].word, word.text);
    assert.equal(mapped[index].words[wordIndex].startTime, word.start);
    assert.equal(mapped[index].words[wordIndex].endTime, word.end);
  }
}
// The renderer receives fresh objects and cannot mutate the reusable fixture.
mapped[0].words[0].word = 'changed';
assert.equal(m.lines[0].words[0].text, '让');
assert.equal(m.toAmllLines()[0].words[0].word, '让');
assert.equal(m.activeLine(-1), -1);
assert.equal(m.activeLine(0), 0);
assert.equal(m.activeLine(4000), 0);
assert.equal(m.activeLine(4200), 1);
assert.equal(m.activeLine(18000), 3);
assert.equal(m.activeLine(100), 0);
assert.equal(m.activeLine(30000), 6);
assert.equal(m.positionAt(5000, 100, 3600, false), 5000);
assert.equal(m.positionAt(5000, 100, 3600, true), 8500);
assert.equal(m.positionAt(5000, 100, 4000, true), 8900);
assert.equal(m.positionAt(29000, 100, 2100, true), 30000);
assert.equal(m.positionAt(-200, 0, 0, false), 0);
assert.equal(m.format(0), '00:00.0');
assert.equal(m.format(14850), '00:14.8');
assert.equal(m.format(30000), '00:30.0');
for (const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)) new vm.Script(script[1]);
assert.ok(/DomLyricPlayer = class extends LyricPlayerBase/.test(html));
assert.equal(Number(html.match(/AUTO_ALIGN_RESUME_DELAY_MS = ([\de]+)/)[1]), 3000);
assert.ok(/connect-src 'none'/.test(html));
const markup = html.replace(/<script[^>]*>[\s\S]*?<\/script>/g, '<script></script>');
assert.ok(!/<script[^>]+src=|<link[^>]+href=|<img[^>]+src=|<audio|<video/i.test(markup));
assert.ok(!/\b(fetch\s*\(|XMLHttpRequest|WebSocket)/.test(fs.readFileSync(path.join(__dirname,'src/preview.js'),'utf8')));
assert.ok(/GNU AFFERO GENERAL PUBLIC LICENSE/.test(html));
assert.ok(/Copyright \(c\) 2020 Vlad Shilov/.test(html));
console.log('PASS: all word timestamps, AMLL adapter, translations, immutable fixture, pause/seek clock, real renderer, JS syntax and offline packaging');
