import assert from 'node:assert/strict';
import {AlbumBackground} from './src/album-background.js';
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};
function fixture(loadImage=async image=>({decoded:image})) {
  const calls=[],states=[],painted=[],errors=[];
  const renderer=Object.fromEntries(['setAlbum','setFPS','setRenderScale','setFlowSpeed','setStaticMode','pause','resume','dispose'].map(name=>[name,(...args)=>{calls.push([name,...args]);}]));
  const background=new AlbumBackground({createRenderer:()=>renderer,loadImage,paint:frame=>painted.push(frame),state:s=>states.push(s),report:e=>errors.push(e)});
  return {background,renderer,calls,states,painted,errors};
}
const frame=(revision,image='cover-a')=>({revision,image,color:'#223344'});
{
  const f=fixture();await f.background.setArtwork(frame(1));
  assert.ok(f.calls.some(c=>c[0]==='setRenderScale' && c[1]===.35));
  assert.ok(f.calls.some(c=>c[0]==='setFPS' && c[1]===30));
  assert.deepEqual(f.calls.find(c=>c[0]==='setAlbum')[1],{decoded:'cover-a'});
  assert.equal(f.states.at(-1),'static');
  f.background.setPlayback(true,true,false);assert.equal(f.states.at(-1),'flowing');
  f.background.setPlayback(true,false,false);assert.equal(f.states.at(-1),'static');
  assert.ok(f.calls.some(c=>c[0]==='setFlowSpeed' && c[1]===0));
  const count=f.calls.length;f.background.setPlayback(true,false,false);assert.equal(f.calls.length,count);
  f.background.setPlayback(false,true,false);assert.equal(f.states.at(-1),'hidden');assert.equal(f.calls.at(-1)[0],'pause');
  f.background.setPlayback(true,true,true);assert.equal(f.states.at(-1),'static');
  f.background.setPlayback(true,true,false);assert.equal(f.states.at(-1),'flowing');
  const repeat=f.calls.length;await f.background.setArtwork(frame(1));assert.equal(f.calls.length,repeat);
  await f.background.setArtwork(frame(2,''));assert.equal(f.states.at(-1),'empty');assert.ok(f.calls.some(c=>c[0]==='setAlbum' && c[1]===''));
}
{
  const first=deferred(),second=deferred();const f=fixture(image=>image==='old'?first.promise:second.promise);
  const a=f.background.setArtwork(frame(1,'old')),b=f.background.setArtwork(frame(2,'new'));
  second.resolve({decoded:'new'});await b;first.resolve({decoded:'old'});await a;
  assert.deepEqual(f.calls.filter(c=>c[0]==='setAlbum').map(c=>c[1]),[{decoded:'new'}]);
  await f.background.setArtwork(frame(1,'old'));assert.equal(f.painted.length,2);
}
{
  const pending=deferred();const f=fixture(()=>pending.promise);
  const a=f.background.setArtwork(frame(1));await f.background.setArtwork(frame(1,''));
  pending.resolve({decoded:'old'});await a;assert.equal(f.calls.length,0);
  // Empty placeholder and encoded result legitimately share one native revision.
  await f.background.setArtwork(frame(1,'new'));assert.ok(f.calls.some(c=>c[0]==='setAlbum'));
}
{
  const f=fixture(async()=>{throw new Error('decode failed');});await f.background.setArtwork(frame(1));
  assert.equal(f.states.at(-1),'fallback');assert.equal(f.errors.length,1);
  await f.background.setArtwork(frame(2,'other'));assert.equal(f.states.at(-1),'fallback');assert.equal(f.painted.at(-1).image,'other');
  assert.equal(f.errors.length,1);
}
{
  const f=fixture();await f.background.setArtwork(frame(1));f.background.fail(new Error('context lost'));
  assert.equal(f.calls.at(-1)[0],'dispose');assert.equal(f.states.at(-1),'fallback');
  f.background.setPlayback(true,true,false);assert.equal(f.states.at(-1),'fallback');
}
{
  const pending=deferred();const f=fixture(()=>pending.promise);const a=f.background.setArtwork(frame(1));
  f.background.dispose();pending.resolve({decoded:'gone'});await a;assert.equal(f.calls.length,0);
}
console.log('PASS: AMLL background local-element input, pause/visibility/reduced motion, cover clear, stale async results, static fallback and disposal');
