# v21: same script, voices and timings as v20, but the pages become one continuous 3D camera flight.
import re,sys
src=open('docs/videos/aiwa-v20/video.html').read()
s=src
s=s.replace('<title>Aiwa v20</title>','<title>Aiwa v21</title>')
s=s.replace("const N=2000;","const N=2400;")
s=s.replace("const cv=document.getElementById('c'),cx=cv.getContext('2d');","const cv=document.getElementById('c'),MAIN=cv.getContext('2d');let cx=MAIN;const ovc=document.createElement('canvas');ovc.width=1920;ovc.height=1080;const OVX=ovc.getContext('2d');")
a=s.index("const WIN=SCN.map");b=s.index("function drawCaption")
eng=r'''
// ---------- the 3D world: every scene is a particle sculpture along one flight path ----------
const F=1400,CX0=960,CY0=420,SP=1150,ZT=110;
const C3=SCN.map((x,k)=>[1700*Math.sin(.9*k+.3),650*Math.sin(.6*k+.5),-k*SP]);
const P3=SCN.map((x,k)=>{let ox=420*Math.sin(1.9*k+.6),oy=190*Math.cos(1.3*k),oz=1;let n=Math.hypot(ox,oy,F);const q=F/n;return[C3[k][0]+ox*q,C3[k][1]+oy*q,C3[k][2]+F*q]});
const YAW=SCN.map((x,k)=>Math.atan2(P3[k][0]-C3[k][0],P3[k][2]-C3[k][2]));
const TRAV=SCN.map((x,k)=>k+1<NS?Math.min(1.25,.72*(SCN[k+1].t-x.t)):1);
function cr(A,u){const n=A.length;u=Math.max(0,Math.min(n-1,u));let i=Math.min(n-2,Math.floor(u)),f=u-i;const g=j=>A[Math.max(0,Math.min(n-1,j))];const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);
 return[0,1,2].map(a=>{const f2=f*f,f3=f2*f;return .5*((2*p1[a])+(-p0[a]+p2[a])*f+(2*p0[a]-5*p1[a]+4*p2[a]-p3[a])*f2+(-p0[a]+3*p1[a]-3*p2[a]+p3[a])*f3)})}
function progress(t){let k=0;for(let i=0;i<NS;i++)if(t>=SCN[i].t)k=i;if(k>=NS-1)return NS-1;const ts=SCN[k+1].t-TRAV[k];if(t<ts)return k;const u=clamp((t-ts)/TRAV[k]);return k+u*u*u*(u*(u*6-15)+10)}
const ENDK=NS-1,PB0=SCN[ENDK].t+2.7;
function camera(t){const p=progress(t);const P=cr(P3,p),T=cr(C3,p);
 let pb=0;if(t>PB0){pb=clamp((t-PB0)/(TOTAL-PB0));pb=pb*pb*(3-2*pb)}
 if(pb>0){const E=C3[ENDK],V=[E[0]+2400,E[1]+3600,E[2]+5200],M=[E[0]*.3,E[1]*.3,E[2]+SP*7];for(let a=0;a<3;a++){P[a]=mix(P[a],V[a],pb);T[a]=mix(T[a],M[a],pb)}}
 P[0]+=Math.sin(t*1.1)*16;P[1]+=Math.cos(t*.9)*12;
 const roll=.03*Math.sin(t*.55+1)+(p%1>0?.0:0)+.12*Math.sin(Math.PI*Math.min(1,(p%1)))*(Math.sin(p*2.1)),
 fw=T.map((v,a)=>v-P[a]),fl=Math.hypot(...fw);fw[0]/=fl;fw[1]/=fl;fw[2]/=fl;
 const up=[Math.sin(roll),Math.cos(roll),0];let rt=[fw[1]*up[2]-fw[2]*up[1],fw[2]*up[0]-fw[0]*up[2],fw[0]*up[1]-fw[1]*up[0]];const rl=Math.hypot(...rt);rt=rt.map(v=>v/rl);
 const u2=[rt[1]*fw[2]-rt[2]*fw[1],rt[2]*fw[0]-rt[0]*fw[2],rt[0]*fw[1]-rt[1]*fw[0]];
 return{P,fw,rt,u2,p,pb}}
// 3D clouds: each scene's 2D targets get depth (extrusion), the Imagine scene is a sphere
const CLOUD=[];
function cloud3(){for(let k=0;k<NS;k++){const T=TG[k+1],A=new Float32Array(N*3),o=SCN[k].o;
 for(let i=0;i<N;i++){let lx=T[i*2]-960,ly=T[i*2+1]-420,lz=(hs(i,40+k)-.5)*(o==='cover'||o==='end'?190:110);
  if(o==='imag'){const g=Math.acos(1-2*(i+.5)/N),th=i*2.399963,R=300+(i%7==0?90:0);lx=R*Math.sin(g)*Math.cos(th);ly=R*Math.sin(g)*Math.sin(th);lz=R*Math.cos(g)}
  A[i*3]=lx;A[i*3+1]=-ly;A[i*3+2]=lz}
 CLOUD.push(A)}}
// ambient dust + the thread
const DUST=[];for(let i=0;i<2600;i++){const u=hs(i,201)*(NS-1+1.2)-.1,c=cr(C3,u);DUST.push([c[0]+(hs(i,202)-.5)*2800,c[1]+(hs(i,203)-.5)*1900,c[2]+(hs(i,204)-.5)*SP*1.1,.6+hs(i,205)*1.4])}
const THREAD=[];for(let i=0;i<900;i++){const u=i/899*(NS-1),c=cr(P3,u);THREAD.push([c[0]+(hs(i,211)-.5)*10,c[1]-70+(hs(i,212)-.5)*10,c[2]+(hs(i,213)-.5)*10])}
function drawPts(cm,pts,get,n,size,color,fogEnd,aMul,minA){
 const{P,fw,rt,u2}=cm;
 for(let i=0;i<n;i++){const q=get(i);const dx=q[0]-P[0],dy=q[1]-P[1],dz=q[2]-P[2];
  const zc=dx*fw[0]+dy*fw[1]+dz*fw[2];if(zc<ZT)continue;
  const xc=dx*rt[0]+dy*rt[1]+dz*rt[2],yc=dx*u2[0]+dy*u2[1]+dz*u2[2];
  const sx=CX0+F*xc/zc,sy=CY0-F*yc/zc;if(sx<-40||sx>1960||sy<-40||sy>1120)continue;
  let a=(1-clamp((zc-F*.9)/(fogEnd-F*.9)))*clamp((zc-ZT)/380)*aMul;if(a<=.02)continue;
  const r=Math.max(.55,Math.min(9,size(i)*F/zc));
  cx.globalAlpha=Math.max(a,minA||0);cx.fillStyle=color(i);cx.beginPath();cx.arc(sx,sy,r,0,7);cx.fill()}}
function drawScene(cm,k,t){const A=CLOUD[k],C=C3[k],s=SCN[k].t,nxt=k+1<NS?SCN[k+1].t:1e9;
 const lt=t-s;let sw=.95*(1-eo(clamp(lt/.9)));
 if(k+1<NS){const ts=nxt-TRAV[k];if(t>ts)sw=-.95*eio(clamp((t-ts)/TRAV[k]))}
 sw+=.07*Math.sin(t*.8+k);
 const o=SCN[k].o,ps=(o==='imag'?t*.5:0),psi=YAW[k]+sw+ps,cs=Math.cos(psi),sn=Math.sin(psi);
 const fog=cm.pb>0?mix(3300,16000,cm.pb):3300;
 drawPts(cm,null,i=>{const lx=A[i*3],ly=A[i*3+1],lz=A[i*3+2];return[C[0]+lx*cs+lz*sn,C[1]+ly,C[2]-lx*sn+lz*cs]},N,
  i=>(i%9===0?3:1.5)+hs(i,8)*1.4,i=>i%9===0?VERM:'#0D0D0C',fog,1,0)}
'''
s=s[:a]+eng+s[b:]
# frame
a=s.index("function frame(t){");b=s.index("window.READY=")
fr=r'''function frame(t){
 const inv=0;
 cx=MAIN;cx.globalAlpha=1;
 cx.fillStyle='rgb(241,237,228)';cx.fillRect(0,0,W,H);
 const cm=camera(t),p=cm.p;
 // dust, then the thread, then the sculptures near the camera
 const fogD=cm.pb>0?mix(5200,15000,cm.pb):5200;
 drawPts(cm,null,i=>DUST[i],DUST.length,i=>DUST[i][3],i=>'#0D0D0C',fogD,.38,0);
 drawPts(cm,null,i=>THREAD[i],THREAD.length,i=>1.7,i=>VERM,fogD,.9,0);
 for(let k=0;k<NS;k++){if(Math.abs(k-p)>(cm.pb>0?30:3.4))continue;drawScene(cm,k,t)}
 cx.globalAlpha=1;
 cx.save();cx.font='500 22px JB';cx.fillStyle='rgb(13,13,12)';cx.globalAlpha=.8;cx.textBaseline='middle';cx.textAlign='left';cx.fillText('AIWA',120,64);cx.restore();
 // flat overlays (UI mock-ups, labels) fade in once the sculpture has settled in front of the camera
 let kk=0;for(let i=0;i<NS;i++)if(t>=SCN[i].t)kk=i;
 const lt=t-SCN[kk].t,nx=kk+1<NS?SCN[kk+1].t:1e9;
 const ao=SCN[kk].o==='end'?clamp((lt-.1)/.3):clamp((lt-.12)/.3)*(1-clamp((t-(nx-.3))/.25));
 if(ao>0){OVX.clearRect(0,0,W,H);cx=OVX;overlay(t,kk,inv);cx=MAIN;cx.globalAlpha=ao;cx.drawImage(ovc,0,0);cx.globalAlpha=1}
 drawCaption(t,inv);
 cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6)}
'''
s=s[:a]+fr+s[b:]
s=s.replace("build();layout()})()","build();cloud3();layout()})()")
open('docs/videos/aiwa-v21/video.html','w').write(s)
print(len(s))
