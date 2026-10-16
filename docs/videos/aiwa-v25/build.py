# v25: a new visual language on the v24 structure (same words, voices, groups and camera): a black space where the sculptures are
# constellations — glowing nodes joined by light threads, with depth of field, bloom, anamorphic flares between passages and grain.
s=open('docs/videos/aiwa-v24/video.html').read()
s=s.replace('<title>Aiwa v24</title>','<title>Aiwa v25</title>').replace("html,body{margin:0;background:#F1EDE4}","html,body{margin:0;background:#05060A}")
# 1) the flat overlays (mock-ups, labels) were drawn dark-on-bone: swap the two inks from `label` to the end of the file
a=s.index("function label");b=s.index("window.READY=")
seg=s[a:b].replace('#0D0D0C','@@A').replace('#F1EDE4','#0D0D0C').replace('@@A','#F1EDE4').replace("o.c||'#000'","o.c||'#F1EDE4'")
s=s[:a]+seg+s[b:]
# 2) the clouds: two layers (a slab) with twin points, and the node graph (threads) of every shape
a=s.index("const CLOUD=[];");b=s.index("// the super agent")
cl=r'''const CLOUD=[],PAIRS=[];
function cloud3(){for(let k=0;k<NS;k++){const T=TG[k+1],A=new Float32Array(N*3),o=SCN[k].o,th=(o==='cover'||o==='end')?85:55;
 for(let i=0;i<N;i++){const j=i&~1,tw=(i&1)?-1:1;let lx=T[j*2]-960,ly=T[j*2+1]-420,lz=tw*th+(hs(i,40+k)-.5)*10;
  if(o==='imag'){const g=Math.acos(1-2*(i+.5)/N),t2=i*2.399963,R=300+(i%7==0?90:0);lx=R*Math.sin(g)*Math.cos(t2);ly=R*Math.sin(g)*Math.sin(t2);lz=R*Math.cos(g)}
  A[i*3]=lx;A[i*3+1]=-ly;A[i*3+2]=lz}
 CLOUD.push(A)}}
// the threads: every sixth point is a node, joined to its nearest neighbours
function pairsOf(P,maxD,K,twin){const cs=maxD,m=new Map(),key=(x,y,z)=>(Math.floor(x/cs)+512)*1048576+(Math.floor(y/cs)+512)*1024+(Math.floor(z/cs)+512),L=[];
 const n=P.length/3;for(let i=0;i<n;i+=6){const k=key(P[i*3],P[i*3+1],P[i*3+2]);(m.get(k)||m.set(k,[]).get(k)).push(i)}
 for(let i=0;i<n;i+=6){let c=0;const x=P[i*3],y=P[i*3+1],z=P[i*3+2],cx_=Math.floor(x/cs),cy_=Math.floor(y/cs),cz_=Math.floor(z/cs);
  for(let dx=-1;dx<=1&&c<K;dx++)for(let dy=-1;dy<=1&&c<K;dy++)for(let dz=-1;dz<=1&&c<K;dz++){const l=m.get((cx_+dx+512)*1048576+(cy_+dy+512)*1024+(cz_+dz+512));if(!l)continue;
   for(const j of l){if(j<=i||c>=K)continue;const d=Math.hypot(P[j*3]-x,P[j*3+1]-y,P[j*3+2]-z);if(d<maxD&&d>2){L.push(i,j);c++}}}}
 if(twin)for(let i=0;i<n;i+=12)L.push(i,i+1);
 return Uint16Array.from(L)}
const PG=[];
function buildPairs(){CLOUD.forEach((P,k)=>PAIRS.push(pairsOf(P,SCN[k].o==='imag'?170:34,3,SCN[k].o!=='imag')));
 GR.forEach((g,gi)=>{const st=[];
  if(g.sa){for(let s=0;s<3;s++){const P=new Float32Array(N*3);for(let i=0;i<N;i++){const q=sa(i,s,0);P[i*3]=q[0];P[i*3+1]=q[1];P[i*3+2]=q[2]}st.push(pairsOf(P,170,3,false))}}
  else if(g.world){for(let s=0;s<5;s++){const P=new Float32Array(N*3);for(let i=0;i<N;i++){const q=world(i,s,0);P[i*3]=q[0];P[i*3+1]=q[1];P[i*3+2]=q[2]}st.push(pairsOf(P,175,3,false))}}
  else for(let k=g.a;k<=g.b;k++)st.push(PAIRS[k]);
  PG.push(st)})}
'''
s=s[:a]+cl+s[b:]
s=s.replace("build();cloud3();layout()})()","build();cloud3();buildPairs();layout()})()")
# 3) drawing: glow sprites, threads, depth of field
a=s.index("function drawPts");b=s.index("function drawCaption")
dr=r'''const mkSpr=(r,g,bl)=>{const c=document.createElement('canvas');c.width=c.height=64;const x=c.getContext('2d'),gr=x.createRadialGradient(32,32,0,32,32,32);
 gr.addColorStop(0,`rgba(${r},${g},${bl},1)`);gr.addColorStop(.18,`rgba(${r},${g},${bl},.8)`);gr.addColorStop(.45,`rgba(${r},${g},${bl},.18)`);gr.addColorStop(1,`rgba(${r},${g},${bl},0)`);x.fillStyle=gr;x.fillRect(0,0,64,64);return c};
const SPB=mkSpr(241,237,228),SPV=mkSpr(255,75,31);
const SX=new Float32Array(N),SY=new Float32Array(N),SZ=new Float32Array(N);
function project(cm,i,x,y,z){const{P,fw,rt,u2}=cm,dx=x-P[0],dy=y-P[1],dz=z-P[2],zc=dx*fw[0]+dy*fw[1]+dz*fw[2];
 SZ[i]=zc;if(zc<ZT){return}SX[i]=CX0+F*(dx*rt[0]+dy*rt[1]+dz*rt[2])/zc;SY[i]=CY0-F*(dx*u2[0]+dy*u2[1]+dz*u2[2])/zc}
function fogA(zc,fog){return(1-clamp((zc-F*.9)/(fog-F*.9)))*clamp((zc-ZT)/380)}
function glow(sx,sy,zc,base,verm,a,fd){if(a<=.01||zc<ZT)return;const b=Math.abs(zc-fd)/fd,r=Math.max(1.2,Math.min(46,base*F/zc*(1+1.5*b)));
 if(sx<-r||sx>W+r||sy<-r||sy>H+r)return;cx.globalAlpha=Math.min(1,a/(1+2.4*b*b));cx.drawImage(verm?SPV:SPB,sx-r,sy-r,2*r,2*r)}
function threads(PA,al,col,lw){if(al<.015||!PA)return;cx.globalAlpha=al;cx.strokeStyle=col;cx.lineWidth=lw;cx.beginPath();
 for(let q=0;q<PA.length;q+=2){const a=PA[q],b=PA[q+1];if(SZ[a]<ZT||SZ[b]<ZT)continue;const dx=SX[a]-SX[b],dy=SX[a]===SX[a]?SY[a]-SY[b]:0;if(dx*dx+dy*dy>90000)continue;cx.moveTo(SX[a],SY[a]);cx.lineTo(SX[b],SY[b])}cx.stroke()}
function drawScene(cm,k,t,fd){const o=SCN[k].o,C=C3[k],s=SCN[k].t,fog=cm.pb>0?mix(4300,16000,cm.pb):4300,gi=GOF[k];
 const zcC=Math.hypot(C[0]-cm.P[0],C[1]-cm.P[1],C[2]-cm.P[2]),fa=fogA(zcC,fog);
 let psi,al=1,PA=null,PB=null,mB=0,gp,verm;
 if(gi>=0){const g=GR[gi];if(k!==g.a)return;
  psi=YAW[k]+.12*Math.sin(t*.6)+(g.sa||g.world?t*.3:0);al=clamp((t-(s-.9))/.9);
  const f=stageF(g,t),n=g.b-g.a,ia=Math.min(Math.floor(f),g.sa?1:g.world?3:n-1),m=f-ia;
  gp=g.sa?saPos:g.world?worldPos:morphPos;PA=PG[gi][ia];PB=m>0?PG[gi][ia+1]:null;mB=sm(clamp(m));
  verm=g.sa?(i=>i%9===0||(i>=1950&&i%2==0)):g.world?(i=>i%9===0||(i>=1450&&i%3==0)):(i=>i%9===0);
  var cs=Math.cos(psi),sn=Math.sin(psi);
  for(let i=0;i<N;i++){const q=gp(i,t,g);project(cm,i,C[0]+q[0]*cs+q[2]*sn,C[1]+q[1],C[2]-q[0]*sn+q[2]*cs)}}
 else{const A=CLOUD[k],lt=t-s;let sw=.95*(1-eo(clamp(lt/.9)));if(k+1<NS){const ts=TS[k];if(t>ts)sw=-.95*eio(clamp((t-ts)/TRAV[k]))}sw+=.07*Math.sin(t*.8+k);
  psi=YAW[k]+sw;PA=PAIRS[k];verm=i=>i%9===0;var cs=Math.cos(psi),sn=Math.sin(psi);
  for(let i=0;i<N;i++){const lx=A[i*3],ly=A[i*3+1],lz=A[i*3+2];project(cm,i,C[0]+lx*cs+lz*sn,C[1]+ly,C[2]-lx*sn+lz*cs)}}
 cx.globalCompositeOperation='lighter';
 const lw=Math.max(.7,1.3*F/zcC);
 threads(PA,.5*fa*al*(PB?1-mB:1),'#F1EDE4',lw);if(PB)threads(PB,.5*fa*al*mB,'#F1EDE4',lw);
 for(let i=0;i<N;i++){const zc=SZ[i];if(zc<ZT)continue;const node=i%6===0,v=verm(i);
  glow(SX[i],SY[i],zc,node?(v?6.5:5):(v?3.4:2.1)+hs(i,8)*1.2,v,fogA(zc,fog)*al*(node?1:.62),fd)}
 cx.globalCompositeOperation='source-over'}
'''
s=s[:a]+dr+s[b:]
# 4) the frame: black space, dust and thread as light, flares between passages, bloom, vignette, grain
a=s.index("function frame(t){");b=s.index("window.READY=")
fr=r'''const BL=document.createElement('canvas');BL.width=480;BL.height=270;const BX=BL.getContext('2d');
const VG=(()=>{const c=document.createElement('canvas');c.width=W/4;c.height=H/4;const x=c.getContext('2d'),g=x.createRadialGradient(c.width/2,c.height/2,c.height*.25,c.width/2,c.height/2,c.height*.95);g.addColorStop(0,'rgba(0,0,0,0)');g.addColorStop(1,'rgba(0,0,0,.72)');x.fillStyle=g;x.fillRect(0,0,c.width,c.height);return c})();
const GRN=(()=>{const c=document.createElement('canvas');c.width=c.height=256;const x=c.getContext('2d'),d=x.createImageData(256,256);for(let i=0;i<65536;i++){const v=hs(i,77)*255;d.data[i*4]=d.data[i*4+1]=d.data[i*4+2]=v;d.data[i*4+3]=255}x.putImageData(d,0,0);return c})();
function frame(t){
 cx=MAIN;cx.globalAlpha=1;cx.globalCompositeOperation='source-over';
 const bg=cx.createRadialGradient(960,470,80,960,540,1250);bg.addColorStop(0,'#0F1118');bg.addColorStop(1,'#05060A');cx.fillStyle=bg;cx.fillRect(0,0,W,H);
 const cm=camera(t),p=cm.p,g0=progress(t),Tc=cr(C3,g0.p),fd=Math.max(300,Math.hypot(cm.P[0]-Tc[0],cm.P[1]-Tc[1],cm.P[2]-Tc[2]));
 const fogD=cm.pb>0?mix(6200,15000,cm.pb):6200;
 cx.globalCompositeOperation='lighter';
 for(let i=0;i<DUST.length;i++){const d=DUST[i];project(cm,0,d[0],d[1],d[2]);const zc=SZ[0];if(zc<ZT)continue;glow(SX[0],SY[0],zc,d[3]*1.6,i%17===0,fogA(zc,fogD)*.5,fd)}
 for(let i=0;i<THREAD.length;i++){const d=THREAD[i];project(cm,0,d[0],d[1],d[2]);const zc=SZ[0];if(zc<ZT)continue;glow(SX[0],SY[0],zc,2.4,true,fogA(zc,fogD)*.8,fd)}
 cx.globalCompositeOperation='source-over';
 for(let k=0;k<NS;k++){const gi=GOF[k],lo=gi>=0?GR[gi].a:k,hi=gi>=0?GR[gi].b:k;if(cm.pb<=0&&(p<lo-3.4||p>hi+3.4))continue;drawScene(cm,k,t,fd)}
 // an anamorphic flare while the camera travels from one passage to the next
 if(g0.k+1<NS&&SLOT[g0.k+1]!==SLOT[g0.k]&&g0.u>0&&g0.u<1){const a=Math.sin(Math.PI*g0.u);cx.save();cx.globalCompositeOperation='lighter';
  const gr=cx.createLinearGradient(0,0,W,0);gr.addColorStop(0,'rgba(255,75,31,0)');gr.addColorStop(.5,`rgba(255,200,170,${.55*a})`);gr.addColorStop(1,'rgba(255,75,31,0)');cx.fillStyle=gr;cx.fillRect(0,CY0-2,W,4);
  const g2=cx.createLinearGradient(0,0,W,0);g2.addColorStop(0,'rgba(255,75,31,0)');g2.addColorStop(.5,`rgba(255,75,31,${.16*a})`);g2.addColorStop(1,'rgba(255,75,31,0)');cx.fillStyle=g2;cx.fillRect(0,CY0-26,W,52);cx.restore()}
 cx.globalAlpha=1;
 cx.save();cx.font='500 22px JB';cx.fillStyle='#F1EDE4';cx.globalAlpha=.8;cx.textBaseline='middle';cx.textAlign='left';cx.fillText('AIWA',120,64);cx.restore();
 let kk=0;for(let i=0;i<NS;i++)if(t>=SCN[i].t)kk=i;
 const lt=t-SCN[kk].t,nx=kk+1<NS?SCN[kk+1].t:1e9;
 const ao=SCN[kk].o==='end'?clamp((lt-.1)/.3):clamp((lt-.12)/.3)*(1-clamp((t-(nx-.3))/.25));
 if(ao>0){OVX.clearRect(0,0,W,H);cx=OVX;overlay(t,kk,1);cx=MAIN;cx.globalAlpha=ao;cx.drawImage(ovc,0,0);cx.globalAlpha=1}
 drawCaption(t,1);
 // bloom, vignette, grain
 BX.clearRect(0,0,480,270);BX.drawImage(MAIN.canvas,0,0,480,270);
 cx.save();cx.globalCompositeOperation='lighter';cx.filter='blur(16px)';cx.globalAlpha=.7;cx.drawImage(BL,0,0,W,H);cx.filter='blur(5px)';cx.globalAlpha=.32;cx.drawImage(BL,0,0,W,H);cx.restore();
 cx.drawImage(VG,0,0,W,H);
 {const ox=Math.floor(hs(Math.floor(t*30),90)*256),oy=Math.floor(hs(Math.floor(t*30),91)*256);cx.save();cx.globalAlpha=.055;cx.globalCompositeOperation='overlay';cx.translate(-ox,-oy);cx.fillStyle=cx.createPattern(GRN,'repeat');cx.fillRect(0,0,W+256,H+256);cx.restore()}
 cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6)}
'''
s=s[:a]+fr+s[b:]
open('docs/videos/aiwa-v25/video.html','w').write(s)
print(len(s))
