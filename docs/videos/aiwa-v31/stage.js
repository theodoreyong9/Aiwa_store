// ---------- the stage of the conductor (v31) ----------
// The places (videos of real places) behind the sculptures, the objects that take themselves apart into the points of their sculpture, and the hand-overs from one place to the next.
// Everything here follows PLAN (docs/videos/aiwa-v31/film.json through video-kit/conductor): the beat of the moment, its grade, its camera, its transition, its actors.
const PLAN=__PLAN__;
const WORLDS=__WORLDS__;      // id -> { dir, n, fps, from }
const SPRITES=__SPRITES__;    // id -> { uri }
const BEATS=PLAN.beats;
const beatAt=t=>{let k=0;for(let i=0;i<BEATS.length;i++)if(t>=BEATS[i].t0)k=i;return k};
const IMGC=new Map();
const loadImg=src=>{if(!IMGC.has(src))IMGC.set(src,new Promise(res=>{const im=new Image();im.onload=()=>res(im);im.onerror=()=>res(null);im.src=src}));return IMGC.get(src)};
function plateIndex(wid,t){const w=WORLDS[wid];const n=w.n,loc=(Math.max(0,t-w.from)+(w.skip||0))*w.fps;let i=Math.floor(loc);const m=n>1?i%(2*n-2):0;const f=loc-i;i=m>=n?2*n-2-m:m;return Math.max(0,Math.min(n-1,i))}
const plateSrc=(wid,i)=>`${WORLDS[wid].dir}/${String(i+1).padStart(5,'0')}.jpg`;
const CURPL={};      // the plate images of this frame: world id -> Image
async function prepare(t){
 const bi=beatAt(t),b=BEATS[bi],need=new Set([b.world]);
 if(b.transition&&b.transition.t0&&t>=b.transition.t0&&BEATS[bi+1])need.add(BEATS[bi+1].world);
 for(const k of Object.keys(CURPL))delete CURPL[k];
 await Promise.all([...need].map(async wid=>{CURPL[wid]=await loadImg(plateSrc(wid,plateIndex(wid,t)))}));
 // the next frames are asked for in advance (the browser keeps them)
 for(const wid of need)loadImg(plateSrc(wid,Math.min(WORLDS[wid].n-1,plateIndex(wid,t)+2)));
 if(IMGC.size>90){const ks=[...IMGC.keys()];for(let j=0;j<30;j++)IMGC.delete(ks[j])}
}
// the camera of the place follows the camera of the sculpture: the same yaw, pitch and distance, so that the place moves with the idea
function placeCam(t,b){const g=progress(t),mo=moveAt(t,g.k,g.u),c=b.camera;
 const flight=(g.k+1<NS&&SLOT[g.k+1]!==SLOT[g.k])?Math.sin(Math.PI*g.u):0;
 return{dx:-mo[0]*W*.15*c.parallax+Math.sin(t*.7)*6*c.shake*.4+flight*W*.03,dy:mo[1]*H*.2*c.parallax,z:1.07+c.zoom*(mo[2]-1)*2+(t-b.t0)*.0035+flight*.03,rot:c.roll*Math.sin(t*.5)+flight*.012}}
function plateDraw(im,cam,gr,alpha,ox,oy,zmul){
 if(!im)return;cx.save();cx.globalAlpha=alpha;
 cx.filter=`brightness(${gr.brightness}) contrast(${gr.contrast}) saturate(${gr.saturate}) hue-rotate(${gr.hue}deg)${gr.blur>.3?` blur(${gr.blur}px)`:''}`;
 const s=Math.max(W/im.width,H/im.height)*cam.z*(zmul||1),w=im.width*s,h=im.height*s;
 const px=ox==null?W/2:ox,py=oy==null?H/2:oy;
 cx.translate(px+cam.dx*(zmul||1),py+cam.dy*(zmul||1));cx.rotate(cam.rot);
 cx.drawImage(im,-(w*(px/W)),-(h*(py/H)),w,h);
 cx.restore();
 // the tint of the mood: a veil over the place, in the colour of its warmth
 cx.save();cx.globalAlpha=gr.tintAlpha*alpha;cx.globalCompositeOperation='soft-light';cx.fillStyle=`rgb(${gr.tint[0]},${gr.tint[1]},${gr.tint[2]})`;cx.fillRect(0,0,W,H);cx.restore()}
function throughPoint(spec,t){
 if(!spec||spec==='center')return[W/2,CY0];
 if(spec==='sculpture')return[CX0,CY0];
 if(spec.startsWith('point:')){const p=spec.slice(6).split(',').map(Number);return[p[0],p[1]]}
 if(spec.startsWith('actor:')){const a=ACTORS.find(x=>x.id===spec.slice(6));if(a&&a.last)return[a.last.x,a.last.y]}
 return[W/2,CY0]}
let STAGE_SCRIM=false;
function scrim(){const g=cx.createRadialGradient(CX0,CY0,60,CX0,CY0,820);g.addColorStop(0,'rgba(3,4,8,.46)');g.addColorStop(1,'rgba(3,4,8,0)');cx.save();cx.globalCompositeOperation='source-over';cx.fillStyle=g;cx.fillRect(0,0,W,H);cx.restore()}
function stagePlaces(t){const r=stagePlaces0(t);scrim();return r}
function stagePlaces0(t){
 const bi=beatAt(t),b=BEATS[bi],nb=BEATS[bi+1],tr=b.transition,cam=placeCam(t,b);
 cx.globalCompositeOperation='source-over';cx.globalAlpha=1;cx.fillStyle='#05060A';cx.fillRect(0,0,W,H);
 const A=CURPL[b.world];STAGE_SCRIM=true;
 if(!(tr&&nb&&tr.t0&&t>=tr.t0&&tr.kind!=='continue')){plateDraw(A,cam,b.grade,1);return{place:1}}
 const u=clamp((t-tr.t0)/(tr.t1-tr.t0)),e=sm(u),B=CURPL[nb.world],camB=placeCam(t,nb),[x0,y0]=throughPoint(tr.through,t);
 switch(tr.kind){
  case 'dissolve':plateDraw(A,cam,b.grade,1);plateDraw(B,camB,nb.grade,e,null,null,1+(1-e)*.06);break;
  case 'cut':plateDraw(e<.5?A:B,e<.5?cam:camB,e<.5?b.grade:nb.grade,1);break;
  case 'whip':{ // a turn of the camera: the place slides away blurred and the next one slides in
   const sh=e*W*1.05;plateDraw(A,{...cam,dx:cam.dx-sh},b.grade,1-sm(clamp((u-.35)/.4)));
   for(let q=0;q<5;q++)plateDraw(A,{...cam,dx:cam.dx-sh*(1+q*.05)},b.grade,.18*(1-u));
   plateDraw(B,{...camB,dx:camB.dx+(1-e)*W*1.05},nb.grade,sm(clamp((u-.25)/.5)));
   for(let q=0;q<5;q++)plateDraw(B,{...camB,dx:camB.dx+(1-e)*W*1.05*(1+q*.05)},nb.grade,.18*u);
   cx.save();cx.globalCompositeOperation='lighter';cx.globalAlpha=.35*Math.sin(Math.PI*u);const gr=cx.createLinearGradient(0,0,W,0);gr.addColorStop(0,'rgba(255,75,31,0)');gr.addColorStop(.5,'rgba(255,200,170,1)');gr.addColorStop(1,'rgba(255,75,31,0)');cx.fillStyle=gr;cx.fillRect(0,CY0-40,W,80);cx.restore();break}
  case 'zoom':{ // a fall into the picture: the place we leave swells past the frame, the next one opens from the same point
   plateDraw(A,cam,b.grade,1-sm(clamp((u-.15)/.6)),x0,y0,1+3.2*e*e);
   plateDraw(B,camB,nb.grade,sm(clamp(u/.7)),x0,y0,1.9-.9*e);break}
  default:{ // iris: the thing we go through opens like a lens onto the next place
   plateDraw(A,cam,b.grade,1,x0,y0,1+1.6*e*e);
   const R=Math.pow(e,1.5)*Math.hypot(W,H)*.75;
   if(R>2){cx.save();cx.beginPath();cx.arc(x0,y0,R,0,7);cx.clip();plateDraw(B,camB,nb.grade,1,x0,y0,.55+.45*e);cx.restore();
    cx.save();cx.globalCompositeOperation='lighter';cx.lineWidth=5+14*(1-e);cx.strokeStyle=`rgba(255,200,170,${.8*(1-e)})`;cx.beginPath();cx.arc(x0,y0,R,0,7);cx.stroke();cx.lineWidth=2;cx.strokeStyle=`rgba(255,75,31,${.9*(1-e)})`;cx.beginPath();cx.arc(x0,y0,R+10,0,7);cx.stroke();cx.restore()}}
 }
 return{place:1}}

// ---------- the objects: a picture of the object that takes itself apart into the points of its sculpture, and back ----------
const ACTORS=[];
let ACTOR_READY=null;
async function setupActors(){
 for(const b of BEATS)for(const a of (b.actors||[])){
  const sp=SPRITES[a.sprite];const im=await loadImg(sp.uri);
  const sc=document.createElement('canvas');const k=Math.min(1,420/Math.max(im.width,im.height));sc.width=Math.max(8,Math.round(im.width*k));sc.height=Math.max(8,Math.round(im.height*k));
  const sx=sc.getContext('2d');sx.drawImage(im,0,0,sc.width,sc.height);
  const d=sx.getImageData(0,0,sc.width,sc.height).data;const op=[];
  for(let y=0;y<sc.height;y++)for(let x=0;x<sc.width;x++){const o=(y*sc.width+x)*4;if(d[o+3]>150)op.push([x,y,d[o],d[o+1],d[o+2]])}
  // the points: N picked on the picture (deterministic); r = when each one leaves (a sweep from the top-left with noise)
  const pts=[];for(let i=0;i<N;i++){const p=op[Math.floor(hs(i,300+ACTORS.length)*op.length)%op.length];pts.push({nx:p[0]/sc.width-.5,ny:p[1]/sc.height-.5,c:[Math.min(255,p[2]*1.25+30),Math.min(255,p[3]*1.25+30),Math.min(255,p[4]*1.25+30)],r:clamp((p[0]/sc.width*.5+p[1]/sc.height*.5)*.6+hs(i,301)*.4)})}
  ACTORS.push({id:a.id,sprite:a.sprite,page:SCN.findIndex(x=>String(x.o)===String(a.page)),in:a.in,dissolve:a.dissolve,reform:a.reform||null,img:im,w:im.width,h:im.height,pts,er:document.createElement('canvas'),sc,sd:d,last:null,key:a.id+'@'+a.in})}
}
const actorAmt=(a,t)=>{let d=a.dissolve?sm(clamp((t-a.dissolve[0])/(a.dissolve[1]-a.dissolve[0]))):0;if(a.reform)d*=1-sm(clamp((t-a.reform[0])/(a.reform[1]-a.reform[0])));return d}
const ACTQ=[];
const OVR={};      // scene index -> { e: per-point amount of departure, px, py: where the point starts }
function actorsFrame(t,cm){
 for(const k of Object.keys(OVR))delete OVR[k];ACTQ.length=0;
 for(const a of ACTORS){
  if(t<a.in-.1||t>a.dissolve[1]+.6&&!a.reform)continue;
  // where the object is: the box of its sculpture, as the camera sees it (the engine's own projection, now)
  const k=a.page;let g=GOF[k]>=0?GR[GOF[k]]:null;const kk=g?g.a:k;
  const C=C3[kk],gi=GOF[kk];let psi,bx0=1e9,bx1=-1e9,by0=1e9,by1=-1e9,cnt=0;
  if(gi>=0){const gg=GR[gi];psi=YAW[kk]+.12*Math.sin(t*.6);const cs=Math.cos(psi),sn=Math.sin(psi);
   for(let i=0;i<N;i+=5){const q=morphPos(i,t,gg);project(cm,i,C[0]+q[0]*cs+q[2]*sn,C[1]+q[1],C[2]-q[0]*sn+q[2]*cs);if(SZ[i]<ZT)continue;bx0=Math.min(bx0,SX[i]);bx1=Math.max(bx1,SX[i]);by0=Math.min(by0,SY[i]);by1=Math.max(by1,SY[i]);cnt++}}
  else{const A=CLOUD[kk],s=SCN[kk].t;let sw=.95*(1-eo(clamp((t-s)/.9)));if(kk+1<NS){const ts=TS[kk];if(t>ts)sw=-.95*eio(clamp((t-ts)/TRAV[kk]))}sw+=.07*Math.sin(t*.8+kk);psi=YAW[kk]+sw;const cs=Math.cos(psi),sn=Math.sin(psi);
   for(let i=0;i<N;i+=5){const lx=A[i*3],ly=A[i*3+1],lz=A[i*3+2];project(cm,i,C[0]+lx*cs+lz*sn,C[1]+ly,C[2]-lx*sn+lz*cs);if(SZ[i]<ZT)continue;bx0=Math.min(bx0,SX[i]);bx1=Math.max(bx1,SX[i]);by0=Math.min(by0,SY[i]);by1=Math.max(by1,SY[i]);cnt++}}
  if(cnt<20)continue;
  const cxm=(bx0+bx1)/2,cym=(by0+by1)/2,bw=Math.max(60,bx1-bx0),bh=Math.max(60,by1-by0);
  const sc=Math.min(bw/a.w,bh/a.h)*.86,w=a.w*sc,h=a.h*sc,bob=Math.sin(t*2.1+a.page)*5;
  const d=actorAmt(a,t),fade=sm(clamp((t-a.in)/.45));
  a.last={x:cxm,y:cym+bob,w,h};
  // the picture, eroded by the amount that has left
  if(d<.995)ACTQ.push(()=>{
   const er=a.er;if(er.width!==a.sc.width){er.width=a.sc.width;er.height=a.sc.height}
   const ex=er.getContext('2d');ex.clearRect(0,0,er.width,er.height);ex.drawImage(a.sc,0,0);
   if(d>0){const id=ex.getImageData(0,0,er.width,er.height),dd=id.data,thr=d*1.15;for(let y=0;y<er.height;y++)for(let x=0;x<er.width;x++){const o=(y*er.width+x)*4+3;if(!dd[o])continue;const n=hs(x*131+y*17,310)*.5+(x/er.width*.5+y/er.height*.5)*.5;if(n<thr-.12)dd[o]=0;else if(n<thr)dd[o]=dd[o]*(n-(thr-.12))/.12}ex.putImageData(id,0,0)}
   cx.save();cx.globalAlpha=fade;cx.shadowColor=a.sprite==='phone_off'?'rgba(255,170,140,.55)':'rgba(0,0,0,.5)';cx.shadowBlur=a.sprite==='phone_off'?46:30;cx.drawImage(er,cxm-w/2,cym-h/2+bob,w,h);cx.restore()});
  // the points of the object: where each one starts (on the picture), and how far it has gone
  const e=new Float32Array(N),px=new Float32Array(N),py=new Float32Array(N);
  for(let i=0;i<N;i++){const p=a.pts[i];e[i]=sm(clamp((d*1.6-p.r*.6)/1));px[i]=cxm+p.nx*w;py[i]=cym+p.ny*h+bob}
  if(a.reform){const f=sm(clamp((t-a.reform[0])/(a.reform[1]-a.reform[0])));for(let i=0;i<N;i++)e[i]=sm(clamp(((1-f)*1.6-a.pts[i].r*.6)/1))*(d>0?1:0)}
  let m=0;for(let i=0;i<N;i++)m+=e[i];OVR[kk]={e,px,py,a,d,m:m/N};
 }}
