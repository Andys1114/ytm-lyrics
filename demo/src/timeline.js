const DemoTimeline = (() => {
  'use strict';
  const duration = 30000;
  const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
  function line(start, end, parts, translation) {
    const total = parts.reduce((sum, part) => sum + part[1], 0);
    let weight = 0;
    const words = parts.map(([text, amount]) => {
      const wordStart = start + Math.round((end - start) * weight / total);
      weight += amount;
      return Object.freeze({text, start: wordStart, end: start + Math.round((end - start) * weight / total)});
    });
    return Object.freeze({start, end, text: words.map(word => word.text).join(''), translation, words: Object.freeze(words)});
  }
  const lines = Object.freeze([
    line(0, 3500, [['让',1],['每',1],['一',.65],['个',.8],['字，',1.8],['慢',1.6],['慢',1.6],['亮',1.1],['起',1.2],['来。',1.8]], 'Let every word slowly find its light.'),
    line(4200, 8400, [['Let ',1],['every ',1.3],['little ',1.2],['word ',1.6],['find ',1],['its ',.6],['own ',1],['light.',3.8]], '让每个小小的词，都找到自己的光。'),
    line(9400, 13400, [['停',1],['一',.7],['拍，',1.8],['也',.8],['让',1],['呼',1.3],['吸',1.5],['留',1],['在',.7],['句',1.1],['子',1],['里。',1.4]], 'Leave a beat for the breath between the words.'),
    line(14200, 18800, [['Even ',1],['when ',.8],['the ',.6],['sentence ',1.6],['wraps, ',1.5],['every ',1.1],['word ',1.3],['keeps ',1],['its ',.6],['place ',1.1],['in ',.6],['time.',1.8]], '即使句子换行，每个词也有自己的时间。'),
    line(19800, 23800, [['时',1],['间',1],['往',1],['前，',1.6],['译',1.2],['文',1.2],['轻',1],['轻',1],['跟',1.2],['在',.8],['身',1.2],['旁。',1.8]], 'Time moves on; the translation stays close.'),
    line(24400, 28000, [['A ',.6],['little ',1.3],['pause, ',1.9],['then ',1],['we ',.7],['begin ',1.4],['again.',3.8]], '稍稍停顿，我们再重新开始。'),
    line(28500, 30000, [['把',1],['光，',1.4],['留',1.3],['在',.7],['这',1],['里。',1.6]], 'Keep a little light here.')
  ]);
  function activeLine(ms) {
    let low = 0, high = lines.length;
    while (low < high) { const mid = (low + high) >>> 1; if (lines[mid].start <= ms) low = mid + 1; else high = mid; }
    return low - 1;
  }
  function toAmllLines(withTranslation = true) {
    return lines.map(line => ({
      startTime: line.start, endTime: line.end, translatedLyric: withTranslation ? line.translation : '',
      romanLyric: '', isBG: false, isDuet: false,
      words: line.words.map(word => ({word: word.text, startTime: word.start, endTime: word.end}))
    }));
  }
  function positionAt(position, startedAt, now, playing) { return clamp(position + (playing ? Math.max(0, now - startedAt) : 0), 0, duration); }
  function format(ms) {
    const tenths = Math.floor(clamp(ms, 0, duration) / 100);
    return `${String(Math.floor(tenths / 600)).padStart(2,'0')}:${String(Math.floor(tenths / 10) % 60).padStart(2,'0')}.${tenths % 10}`;
  }
  return Object.freeze({duration, lines, clamp, activeLine, toAmllLines, positionAt, format});
})();
