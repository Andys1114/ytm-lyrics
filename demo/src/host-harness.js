// PC-only harness; this file and its controls are not included in the APK.
let serial=1,epoch=1,anchor=0,started=performance.now(),playing=false,speed=1,kind='word',offsetMs=0;
let artworkRevision=0;
const $=selector=>document.querySelector(selector);
const position=()=>Math.min(30000,Math.max(0,anchor+(playing?(performance.now()-started)*speed:0)));
function push(){
  const time=position();
  window.YtmLyricsHost.sync({serial,epoch,positionMs:time,playing,speed,offsetMs});
  $('#test-time').textContent=(time/1000).toFixed(2)+' s';$('#test-seek').value=String(time/1000);
  $('#test-play').textContent=playing?'暂停':'播放';
  $('#test-background').textContent='背景：'+(document.querySelector('#ambient')?.dataset.state??'');
}
function cover() {
  const kind=$('#test-cover').value;
  let image='',color='#24242b';
  if(kind==='warm' || kind==='cool') {
    const canvas=document.createElement('canvas');canvas.width=canvas.height=256;
    const ctx=canvas.getContext('2d');const warm=kind==='warm';
    const gradient=ctx.createLinearGradient(0,0,256,256);
    gradient.addColorStop(0,warm?'#5d1937':'#102c70');gradient.addColorStop(.5,warm?'#df6038':'#147b98');gradient.addColorStop(1,warm?'#e6b06b':'#889ec6');
    ctx.fillStyle=gradient;ctx.fillRect(0,0,256,256);
    ctx.fillStyle=warm?'#efba6c':'#84cfc9';ctx.beginPath();ctx.ellipse(175,75,100,65,.6,0,Math.PI*2);ctx.fill();
    ctx.fillStyle=warm?'#431b43':'#25214d';ctx.beginPath();ctx.ellipse(75,235,115,70,-.4,0,Math.PI*2);ctx.fill();
    image=canvas.toDataURL('image/jpeg',.82);color=warm?'#392121':'#152c3b';
  } else if(kind==='broken')image='data:image/jpeg;base64,YQ==';
  window.YtmLyricsHost.setArtwork({revision:++artworkRevision,image,color});
}
function load(){
  const rows=DemoTimeline.toAmllLines().map(line=>({...line,text:line.words.map(word=>word.word).join(''),words:kind==='word'?line.words:[]}));
  if(kind==='next')rows.splice(0,rows.length,{startTime:1000,endTime:10000,text:'换歌之后，只显示新歌词。',translatedLyric:'Only the new song stays.',words:[]});
  if(kind==='empty')rows.length=0;
  window.YtmLyricsHost.setData({serial,lines:rows,durationMs:30000,wordTimed:kind==='word',translation:$('#test-trans').checked});
  push();$('#test-status').textContent='电脑桥接检查 · serial '+serial;
}
window.YtmLyricsBridge={
  ready(){load();cover();},failed(reason){$('#test-status').textContent='ERROR: '+reason;},
  backgroundFailed(reason){$('#test-status').textContent='背景静态回退：'+reason;},
  seek(id,time){if(id!==serial)return;anchor=Math.max(0,Math.min(30000,time-offsetMs));started=performance.now();epoch++;push();$('#test-status').textContent='歌词跳转 '+time+' ms';}
};
$('#test-play').onclick=()=>{anchor=position();started=performance.now();playing=!playing;push();};
$('#test-seek').oninput=()=>{anchor=Number($('#test-seek').value)*1000;started=performance.now();epoch++;push();};
$('#test-offset').onchange=()=>{offsetMs=Number($('#test-offset').value);epoch++;push();};
$('#test-trans').onchange=()=>window.YtmLyricsHost.setTranslation($('#test-trans').checked);
$('#test-rate').onchange=()=>{anchor=position();started=performance.now();speed=Number($('#test-rate').value);epoch++;push();};
$('#test-mode').onchange=()=>{kind=$('#test-mode').value;anchor=0;playing=false;started=performance.now();serial++;epoch++;load();};
$('#test-cover').onchange=cover;
$('#test-visible').onchange=()=>window.YtmLyricsHost.setVisible($('#test-visible').checked);
$('#test-context-loss').onclick=()=>document.querySelector('#ambient canvas')?.getContext('webgl')?.getExtension('WEBGL_lose_context')?.loseContext();
$('#test-stale').onclick=()=>{window.YtmLyricsHost.sync({serial:serial-1,epoch:999,positionMs:29000,playing:true,speed:1});$('#test-status').textContent='已发送旧 serial；应保持当前歌词';};
setInterval(push,150);
