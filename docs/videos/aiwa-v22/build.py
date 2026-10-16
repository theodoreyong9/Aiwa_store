# v22: v21's 3D flight, with a different camera move on every page and one super agent that evolves (instead of the same sculpture three times)
s=open('docs/videos/aiwa-v21/video.html').read()
s=s.replace('<title>Aiwa v21</title>','<title>Aiwa v22</title>')
for a,b in (('"o": 9,','"o": "sa0",'),('"o": 40,','"o": "sa1",'),('"o": 41,','"o": "sa2",')):
    assert a in s; s=s.replace(a,b,1)
a=s.index("const F=1400");b=s.index("function drawPts")
eng=r'''const F=1400,CX0=960,CY0=420,SP=1150,ZT=110;
const GA=SCN.findIndex(x=>x.o==='sa0'),GB=GA+2;           // the super agent: three pages, one evolving sculpture
const SLOT=SCN.map((x,k)=>k<=GA?k:k<=GB?GA:k-2);
const C3=SCN.map((x,k)=>{const m=SLOT[k];return[1700*Math.sin(.9*m+.3),650*Math.sin(.6*m+.5),-m*SP]});
const P3=SCN.map((x,k)=>{const m=SLOT[k];let ox=420*Math.sin(1.9*m+.6),oy=190*Math.cos(1.3*m);let n=Math.hypot(ox,oy,F);const q=F/n;return[C3[k][0]+ox*q,C3[k][1]+oy*q,C3[k][2]+F*q]});
const YAW=SCN.map((x,k)=>Math.atan2(P3[k][0]-C3[k][0],P3[k][2]-C3[k][2]));
const TRAV=SCN.map((x,k)=>k+1<NS?Math.min(1.25,.72*(SCN[k+1].t-x.t)):1);
const TS=SCN.map((x,k)=>k+1<NS?SCN[k+1].t-TRAV[k]:SCN[k].t+1e3); // when the camera leaves page k
function cr(A,u){const n=A.length;u=Math.max(0,Math.min(n-1,u));let i=Math.min(n-2,Math.floor(u)),f=u-i;const g=j=>A[Math.max(0,Math.min(n-1,j))];const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);
 return[0,1,2].map(a=>{const f2=f*f,f3=f2*f;return .5*((2*p1[a])+(-p0[a]+p2[a])*f+(2*p0[a]-5*p1[a]+4*p2[a]-p3[a])*f2+(-p0[a]+3*p1[a]-3*p2[a]+p3[a])*f3)})}
const sm=u=>u*u*u*(u*(u*6-15)+10);
function progress(t){let k=0;for(let i=0;i<NS;i++)if(t>=SCN[i].t)k=i;if(k>=NS-1)return{p:NS-1,k:NS-1,u:0};const u=t<TS[k]?0:sm(clamp((t-TS[k])/TRAV[k]));return{p:k+u,k,u}}
// a different camera move for every page: [yaw, pitch, distance] at the start and at the end of the page (relative to the framing)
const MV=[[0,0,1.18, 0,0,1],            // cover: settles
 [-.25,.1,1.2, .2,-.05,1],              // imagine: orbits the sphere
 [0,.15,1.1,0,0,.95],[.18,0,1,-.12,0,1.05],[0,-.14,.95,0,.05,1.05],[-.2,.05,1.05,.15,0,.95],[.1,.12,1.05,-.1,0,1],[0,0,1,0,.08,.9],   // the six kinds
 [.05,0,1.02,-.04,0,.99],               // widget (flat UI on top: barely moves)
 [.3,-.08,1.15,-.25,.06,1],             // lock screen
 [.05,0,1.02,-.05,0,1],                 // glasses (flat UI on top)
 [-.35,.1,1.2,.3,-.1,1],                // campaigns
 [.35,-.12,1,-.2,.05,1.1],              // personalised
 [0,.18,1.25,0,0,1],                    // multichannel: craning down
 [-.1,0,.92,.1,0,1.12],                 // one click
 [.4,.05,1.15,-.3,-.05,1],              // conversations
 null,null,null,                         // the super agent has its own long orbit (below)
 [-.3,.12,1.2,.25,0,1],                 // vote
 [.2,-.12,1,-.2,.1,1.1],                // updates
 [0,0,1.3,0,0,.9],                      // the web idea: push in
 [-.25,.08,1.1,.3,-.06,1.05],           // interplanetary
 [.15,0,.95,-.1,0,1.1],                 // without permission
 [0,0,1,0,0,1]];                        // end
const SAK=[[-.55,.05,1.35],[-.15,.12,1.15],[.3,-.12,1.1],[.85,.2,1.45]];   // the camera circles the super agent while it evolves
const ENDK=NS-1,PB0=SCN[ENDK].t+2.7;
function moveAt(t,k,u){ // returns the [yaw,pitch,dist] offset at time t
 if(k>=GA&&k<GB+1&&t<TS[GB]){const T=[SCN[GA].t,SCN[GA+1].t,SCN[GA+2].t,TS[GB]];let j=0;for(let i=0;i<3;i++)if(t>=T[i])j=i;const f=clamp((t-T[j])/(T[j+1]-T[j]));return cr(SAK,j+f)}
 const m1=k>=GA&&k<=GB?[SAK[3]]:null,A=k>=GA&&k<=GB?SAK[3]:MV[k].slice(3);
 if(t<TS[k]){const m=k>=GA&&k<=GB?null:MV[k];const f=clamp((t-SCN[k].t)/Math.max(.3,TS[k]-SCN[k].t));return[0,1,2].map(a=>mix(m[a],m[a+3],sm(f)))}
 const B=k+1<NS?(k+1>=GA&&k+1<=GB?SAK[0]:MV[k+1].slice(0,3)):A;return[0,1,2].map(a=>mix(A[a],B[a],u))}
function camera(t){const g=progress(t),p=g.p;const P=cr(P3,p),T=cr(C3,p);
 const mo=moveAt(t,g.k,g.u);
 {const v=[P[0]-T[0],P[1]-T[1],P[2]-T[2]],cy=Math.cos(mo[0]),sy=Math.sin(mo[0]);let x=v[0]*cy+v[2]*sy,z=-v[0]*sy+v[2]*cy,y=v[1];
  const cp=Math.cos(mo[1]),sp=Math.sin(mo[1]);const y2=y*cp-z*sp,z2=y*sp+z*cp;P[0]=T[0]+x*mo[2];P[1]=T[1]+y2*mo[2];P[2]=T[2]+z2*mo[2]}
 let pb=0;if(t>PB0){pb=clamp((t-PB0)/(TOTAL-PB0));pb=pb*pb*(3-2*pb)}
 if(pb>0){const E=C3[ENDK],V=[E[0]+2400,E[1]+3600,E[2]+5200],M=[E[0]*.3,E[1]*.3,E[2]+SP*7];for(let a=0;a<3;a++){P[a]=mix(P[a],V[a],pb);T[a]=mix(T[a],M[a],pb)}}
 P[0]+=Math.sin(t*1.1)*16;P[1]+=Math.cos(t*.9)*12;
 const roll=.03*Math.sin(t*.55+1)+.1*Math.sin(Math.PI*(p%1))*Math.sin(p*2.1),
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
// the super agent: a core, six agents around it, links; then it speaks (waves), then it pushes you to create (agents become new things, sparks)
function sa(i,st,t){
 const R=(a,b)=>a+(b-a)*hs(i,300+st*0+b);
 if(i<650){const g=Math.acos(1-2*(i+.5)/650),th=i*2.399963,r=(i%3==0?90:150)*(1+(st==1?.1*Math.sin(t*7):0)+(st==2?.05*Math.sin(t*4):0));return[r*Math.sin(g)*Math.cos(th),r*Math.sin(g)*Math.sin(th),r*Math.cos(g)]}
 if(i<1550){const q=Math.floor((i-650)/150),w=(i-650)%150,a=q*Math.PI/3+t*.35,rad=st==2?560+60*Math.sin(t+q):500,cxl=Math.cos(a)*rad,czl=Math.sin(a)*rad,cyl=Math.sin(a*1.7+q)*160*(st==2?1.5:1);
  const g=Math.acos(1-2*(w+.5)/150),th=w*2.399963;
  if(st==2){const e=62,fx=((w%5)/4-.5)*2,fy=(((w/5|0)%5)/4-.5)*2,fz=w%2?1:-1;const face=w%3;let x=fx,y=fy,z=fz;if(face==1){x=fz;z=fx}else if(face==2){y=fz;z=fy}return[cxl+x*e,cyl+y*e,czl+z*e]}
  const r=st==1?52:55;return[cxl+r*Math.sin(g)*Math.cos(th),cyl+r*Math.sin(g)*Math.sin(th),czl+r*Math.cos(g)]}
 if(i<1950){const j=i-1550,q=Math.floor(j/66),w=(j%66)/66,a=q*Math.PI/3+t*.35,rad=st==2?560+60*Math.sin(t+q):500,cyl=Math.sin(a*1.7+q)*160*(st==2?1.5:1);const f=st==2?Math.min(1,w*1.15):w;
  return[Math.cos(a)*rad*f+(hs(i,5)-.5)*6,cyl*f+(hs(i,6)-.5)*6,Math.sin(a)*rad*f+(hs(i,7)-.5)*6]}
 const j=i-1950,n=N-1950;
 if(st==0){const a=j*2.399963+t*.4,r=240+(hs(i,8)-.5)*40;return[r*Math.cos(a),(hs(i,9)-.5)*14,r*Math.sin(a)]}
 if(st==1){const ring=j%4,a=hs(i,10)*6.2832,r=300+ring*130+((t*140)%130);return[r*Math.cos(a)*.98,r*Math.sin(a)*.98,(hs(i,11)-.5)*10]}
 const u=((t*.5+hs(i,12))%1),a=hs(i,13)*6.2832,b=Math.acos(1-2*hs(i,14)),r=200+u*820;return[r*Math.sin(b)*Math.cos(a),r*Math.sin(b)*Math.sin(a),r*Math.cos(b)]}
function saPos(i,t){const s1=SCN[GA+1].t,s2=SCN[GA+2].t;let f;
 if(t<s1-.25)f=0;else if(t<s1+.7)f=sm(clamp((t-(s1-.25))/.95));else if(t<s2-.25)f=1;else f=1+sm(clamp((t-(s2-.25))/.95));
 const a=Math.floor(f),b=Math.min(2,a+1),m=f-a;const A=sa(i,a,t);if(m<=0)return A;const B=sa(i,b,t);return[mix(A[0],B[0],m),mix(A[1],B[1],m),mix(A[2],B[2],m)]}
// ambient dust + the thread
const DUST=[];for(let i=0;i<2600;i++){const u=hs(i,201)*(NS-3+1.2)-.1,c=cr(C3.filter((x,k)=>k===0||SLOT[k]!==SLOT[k-1]),u);DUST.push([c[0]+(hs(i,202)-.5)*2800,c[1]+(hs(i,203)-.5)*1900,c[2]+(hs(i,204)-.5)*SP*1.1,.6+hs(i,205)*1.4])}
const THREAD=[];{const PU=P3.filter((x,k)=>k===0||SLOT[k]!==SLOT[k-1]);for(let i=0;i<900;i++){const u=i/899*(PU.length-1),c=cr(PU,u);THREAD.push([c[0]+(hs(i,211)-.5)*10,c[1]-70+(hs(i,212)-.5)*10,c[2]+(hs(i,213)-.5)*10])}}
'''
s=s[:a]+eng+s[b:]
# drawScene: group handling
a=s.index("function drawScene");b=s.index("function drawCaption")
ds=r'''function drawScene(cm,k,t){const o=SCN[k].o,C=C3[k],s=SCN[k].t,nxt=k+1<NS?SCN[k+1].t:1e9;
 const fog=cm.pb>0?mix(3300,16000,cm.pb):3300;
 if(k>=GA&&k<=GB){if(k!==GA)return;const ps=t*.3,cs=Math.cos(ps),sn=Math.sin(ps);
  drawPts(cm,null,i=>{const q=saPos(i,t);return[C[0]+q[0]*cs+q[2]*sn,C[1]+q[1],C[2]-q[0]*sn+q[2]*cs]},N,
   i=>(i%9===0?3:1.5)+hs(i,8)*1.4,i=>(i%9===0||(i>=1950&&i%2==0))?VERM:'#0D0D0C',fog,clamp((t-(SCN[GA].t-.9))/.9),0);return}
 const A=CLOUD[k];const lt=t-s;let sw=.95*(1-eo(clamp(lt/.9)));
 if(k+1<NS){const ts=TS[k];if(t>ts)sw=-.95*eio(clamp((t-ts)/TRAV[k]))}
 sw+=.07*Math.sin(t*.8+k);
 const ps=(o==='imag'?t*.5:0),psi=YAW[k]+sw+ps,cs=Math.cos(psi),sn=Math.sin(psi);
 drawPts(cm,null,i=>{const lx=A[i*3],ly=A[i*3+1],lz=A[i*3+2];return[C[0]+lx*cs+lz*sn,C[1]+ly,C[2]-lx*sn+lz*cs]},N,
  i=>(i%9===0?3:1.5)+hs(i,8)*1.4,i=>i%9===0?VERM:'#0D0D0C',fog,1,0)}
'''
s=s[:a]+ds+s[b:]
s=s.replace("for(let k=0;k<NS;k++){if(Math.abs(k-p)>(cm.pb>0?30:3.4))continue;drawScene(cm,k,t)}","for(let k=0;k<NS;k++){if(Math.abs((k>=GA&&k<=GB?GA+1:k)-p)>(cm.pb>0?30:3.4))continue;drawScene(cm,k,t)}")
open('docs/videos/aiwa-v22/video.html','w').write(s)
print(len(s))
