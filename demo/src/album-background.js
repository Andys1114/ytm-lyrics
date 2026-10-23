/** Coordinates AMLL independently of the lyric timeline. Images are local data only. */
export class AlbumBackground {
  constructor({createRenderer,loadImage,paint,state=()=>{},report=()=>{}}) {
    Object.assign(this,{createRenderer,loadImage,paint,state,report});
    this.revision=-1;this.image='';this.request=0;
    this.visible=true;this.playing=false;this.reduced=false;
    this.renderer=null;this.disabled=false;this.disposed=false;this.mode='';
  }
  async setArtwork(frame) {
    if(this.disposed || frame.revision<this.revision)return;
    if(frame.revision===this.revision && frame.image===this.image)return;
    const request=++this.request;
    this.revision=frame.revision;this.image=frame.image;
    this.paint(frame);
    this.state('loading');
    // Clearing also invalidates AMLL's own pending texture work, before image decode.
    try {if(this.renderer)await this.renderer.setAlbum('');}
    catch(error){this.fail(error);}
    if(request!==this.request || this.disposed)return;
    if(!frame.image) {this.apply(true);this.state('empty');return;}
    if(this.disabled){this.state('fallback');return;}
    try {
      // Pass a decoded element, not a URL: AMLL's URL path uses fetch(), while
      // the production document intentionally keeps connect-src 'none'.
      const image=await this.loadImage(frame.image);
      if(request!==this.request || this.disposed)return;
      if(!this.renderer) {
        this.renderer=this.createRenderer();
        this.renderer.setRenderScale(.35);
        this.renderer.setFPS(30);
        this.renderer.pause();
      }
      await this.renderer.setAlbum(image);
      if(request!==this.request || this.disposed)return;
      this.apply(true);
    } catch(error) {if(request===this.request && !this.disposed)this.fail(error);}
  }
  setPlayback(visible,playing,reduced) {
    this.visible=visible;this.playing=playing;this.reduced=reduced;
    this.apply();
  }
  apply(force=false) {
    if(!this.renderer || this.disabled || this.disposed)return;
    const mode=!this.visible?'hidden':this.playing && !this.reduced && !!this.image?'flowing':'static';
    if(!force && this.mode===mode)return;
    this.mode=mode;
    // Static mode still settles the album crossfade; flowSpeed 0 freezes the
    // mesh immediately. AMLL then stops requesting frames once the fade ends.
    this.renderer.setFlowSpeed(mode==='flowing'?2:0);
    this.renderer.setStaticMode(mode!=='flowing');
    if(mode==='hidden')this.renderer.pause();else this.renderer.resume();
    this.state(mode);
  }
  fail(error) {
    if(this.disposed || this.disabled)return;
    this.disabled=true;
    this.renderer?.dispose();this.renderer=null;
    this.state('fallback');this.report(error);
  }
  dispose() {
    this.disposed=true;this.request++;
    this.renderer?.dispose();this.renderer=null;
  }
}
