import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import vm from 'node:vm';
import {positionAt,mediaPositionAt,isSeek} from './src/host-clock.js';
const base={serial:1,epoch:1,positionMs:1000,durationMs:30000,receivedAt:100,playing:false,speed:1};
assert.equal(positionAt(base,5100),1000);
assert.equal(positionAt({...base,playing:true},1100),2000);
assert.equal(positionAt({...base,playing:true,speed:2},1100),3000);
assert.equal(positionAt({...base,playing:true,speed:1.5},1100),2500);
assert.equal(positionAt({...base,playing:true},99999),30000);
const playing={...base,playing:true};
assert.equal(isSeek(playing,{...playing,positionMs:1150,receivedAt:250},250),false);
assert.equal(isSeek(playing,{...playing,positionMs:9000,receivedAt:250},250),true);
assert.equal(isSeek(playing,{...playing,positionMs:500,receivedAt:250},250),true);
assert.equal(isSeek(playing,{...playing,epoch:2},100),true);
assert.equal(isSeek(playing,{...playing,serial:2},100),true);
assert.equal(isSeek(playing,{...playing,playing:false},100),true);
assert.equal(isSeek(playing,{...playing,speed:2},100),true);
assert.equal(positionAt({...base,offsetMs:500},5100),1500); // Paused media stays fixed.
assert.equal(mediaPositionAt({...base,offsetMs:500},5100),1000);
assert.equal(positionAt({...base,positionMs:0,offsetMs:-500},5100),-500);
assert.equal(positionAt({...playing,offsetMs:500,speed:2},1100),3500);
assert.equal(positionAt({...playing,offsetMs:-500},99999),29500);
assert.equal(isSeek(base,{...base,offsetMs:100},100),true); // Even a 100 ms adjustment applies immediately.
const translated={...playing,offsetMs:500};
const refreshed={...translated,positionMs:mediaPositionAt(translated,400),receivedAt:400};
assert.equal(positionAt(refreshed,600),positionAt(translated,600)); // Translation rebuild must not apply offset twice.
const html=readFileSync(new URL('../app/src/main/assets/amll/lyrics.html',import.meta.url),'utf8');
assert.ok(html.includes('YtmLyricsHost') && html.includes('YtmLyricsBridge'));
assert.ok(!html.includes('DemoTimeline') && !html.includes('test-controls'));
assert.ok(html.includes("connect-src 'none'"));
assert.ok(html.includes('img-src data:'));
assert.ok(html.includes('MeshGradientRenderer') && html.includes('setArtwork'));
assert.ok(html.includes('font-size:max(18px,.62em)'));
const preview=readFileSync(new URL('./lyrics-preview.html',import.meta.url),'utf8');
assert.ok(preview.includes('font-size:max(18px,.62em)'));
// Follow AMLL's existing active line, without inventing translated word timing.
const translatedHighlight='.amll-lyric-player .FmKaba_lyricLine.FmKaba_active > .FmKaba_lyricSubLine{opacity:1;transition:opacity .16s ease-out}';
assert.ok(html.includes(translatedHighlight));
assert.ok(preview.includes(translatedHighlight));
for(const script of html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))new vm.Script(script[1]);
const harness=readFileSync(new URL('./host-preview.html',import.meta.url),'utf8');
for(const script of harness.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g))new vm.Script(script[1]);
assert.ok(harness.includes('test-cover') && harness.includes('WEBGL_lose_context'));
console.log('PASS: native bridge anchors, paused state, 1.5x/2x speed, duration bounds, song generations and seek detection');
