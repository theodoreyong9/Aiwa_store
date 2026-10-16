# v24: the last passage is one evolving world (no more identical morphs), and the film ends on AIWA, not on a wide view. (v23: the film as a few evolving 3D sculptures, each filmed by its own camera move; the six kinds are named aloud.
# Pipeline (run from the repo root): the v20 schedule (with the six names spoken) -> the v21 3D patch -> this file's group engine.
import re,json,math,sys
def run(path,edits):
    src=open(path).read()
    for a,b in edits:
        assert a in src,(path,a[:50]);src=src.replace(a,b)
    exec(compile(src,path,'exec'),{'__name__':'__main__','__file__':path})
# 1) schedule + base page (v20 code, paths redirected, type pages as long as the spoken name)
run('docs/videos/aiwa-v20/build.py',[
 ('/tmp/v20','/tmp/v24'),
 ("open('/home/user/aiwa_store/docs/videos/aiwa-v20/video.html','w')","open('/tmp/v24/base20.html','w')"),
 ("""for q,(d,o) in enumerate(TYPES):
    scn.append(dict(t=round(t,3),d=d,o=o,c='',v=0))
    t+=beat*1.5
""","""TN=["Documents.","Vidéos.","Jeux.","Sites internet.","Applications Android.","Smart contracts."];TD=[0.88,0.73,0.73,1.3,1.6,1.18]
for q,(d,o) in enumerate(TYPES):
    scn.append(dict(t=round(t,3),d=d,o=o,c='',v=TD[q]))
    cues.append((round(t,3),TN[q]))
    t=math.ceil((t+TD[q]*1.12+0.08+0.22)/half)*half
""")])
# 2) the 3D page (v21 code on the new base)
run('docs/videos/aiwa-v21/build.py',[("docs/videos/aiwa-v20/video.html",'/tmp/v24/base20.html'),("docs/videos/aiwa-v21/video.html",'/tmp/v24/b21.html')])
s=open('/tmp/v24/b21.html').read()
s=s.replace('<title>Aiwa v21</title>','<title>Aiwa v24</title>')
s=s.replace('L(n,960,700,.05','L(n,960,745,.05')
# super agent pages: own overlays (none)
for a,b in (('"o": 14,','"o": "w0",'),('"o": 43,','"o": "w1",'),('"o": 44,','"o": "w2",'),('"o": 16,','"o": "w3",'),('"o": 46,','"o": "w4",'),('"o": 9,','"o": "sa0",'),('"o": 40,','"o": "sa1",'),('"o": 41,','"o": "sa2",')):
    assert a in s; s=s.replace(a,b,1)
a=s.index("const F=1400");b=s.index("function drawPts")
eng=r'''const F=1400,CX0=960,CY0=420,SP=1800,ZT=110;
const ix=o=>SCN.findIndex(x=>x.o===o);
const GA=ix('sa0');
// the pages are grouped: pages of one group are ONE sculpture that evolves, filmed by one continuous camera move
// keys = [yaw, pitch, distance] at the start of each page of the group, then when the camera leaves
const GR=[
 {a:ix('imag'),b:ix('t5'),keys:[[-.85,.12,1.3],[-.55,.14,1.15],[-.15,.06,1.05],[.35,-.06,1],[.65,-.12,1.05],[.3,0,1],[-.3,.12,.98],[-.6,.06,1.0],[-.3,0,1]]},
 {a:ix('widget'),b:ix('glasses'),keys:[[.04,0,1.03],[.07,.02,1.02],[-.06,0,1.02],[-.04,0,1]]},
 {a:ix(10),b:ix(33),keys:[[-.14,.08,1.15],[.1,-.05,1.08],[-.08,.08,1.1],[.06,0,1.0],[0,0,1]]},
 {a:GA,b:GA+2,keys:[[-.55,.05,1.35],[-.15,.12,1.15],[.3,-.12,1.1],[.85,.2,1.45]],sa:1},
 {a:ix('w0'),b:ix('w4'),keys:[[-.9,.25,1.55],[-.35,.35,1.35],[.45,.15,1.25],[1.2,-.1,1.7],[.55,.05,1.3],[0,0,1.05]],world:1}];
const GOF=SCN.map(()=>-1);GR.forEach((g,gi)=>{for(let k=g.a;k<=g.b;k++)GOF[k]=gi});
// one 3D slot per group or lone page, along the winding path
const SLOT=[];{let m=-1;SCN.forEach((x,k)=>{if(k===0||GOF[k]<0||GOF[k]!==GOF[k-1])m++;SLOT.push(m)})}
const NSLOT=SLOT[NS-1]+1;
const C3=SCN.map((x,k)=>{const m=SLOT[k];return[1700*Math.sin(.9*m+.3),650*Math.sin(.6*m+.5),-m*SP]});
const P3=SCN.map((x,k)=>{const m=SLOT[k];let ox=420*Math.sin(1.9*m+.6),oy=190*Math.cos(1.3*m);let n=Math.hypot(ox,oy,F);const q=F/n;return[C3[k][0]+ox*q,C3[k][1]+oy*q,C3[k][2]+F*q]});
const YAW=SCN.map((x,k)=>Math.atan2(P3[k][0]-C3[k][0],P3[k][2]-C3[k][2]));
const TRAV=SCN.map((x,k)=>k+1<NS?Math.min(1.25,.72*(SCN[k+1].t-x.t)):1);
const TS=SCN.map((x,k)=>k+1<NS?SCN[k+1].t-(SLOT[k+1]===SLOT[k]?.01:TRAV[k]):SCN[k].t+1e3); // when the camera leaves page k
GR.forEach(g=>{g.times=[];for(let k=g.a;k<=g.b;k++)g.times.push(SCN[k].t);g.times.push(TS[g.b])});
function cr(A,u){const n=A.length;u=Math.max(0,Math.min(n-1,u));let i=Math.min(n-2,Math.floor(u)),f=u-i;const g=j=>A[Math.max(0,Math.min(n-1,j))];const p0=g(i-1),p1=g(i),p2=g(i+1),p3=g(i+2);
 return[0,1,2].map(a=>{const f2=f*f,f3=f2*f;return .5*((2*p1[a])+(-p0[a]+p2[a])*f+(2*p0[a]-5*p1[a]+4*p2[a]-p3[a])*f2+(-p0[a]+3*p1[a]-3*p2[a]+p3[a])*f3)})}
const sm=u=>u*u*u*(u*(u*6-15)+10);
const mix3=(A,B,f)=>[0,1,2].map(a=>mix(A[a],B[a],f));
function progress(t){let k=0;for(let i=0;i<NS;i++)if(t>=SCN[i].t)k=i;if(k>=NS-1)return{p:NS-1,k:NS-1,u:0};const u=t<TS[k]?0:sm(clamp((t-TS[k])/TRAV[k]));return{p:k+u,k,u}}
// the lone pages: [yaw, pitch, dist] at the start and at the end of the page
const MV=SCN.map(()=>[0,0,1,0,0,1]);MV[0]=[0,0,1.18,0,0,1];MV[ix(7)]=[.4,.05,1.15,-.3,-.05,1];MV[SCN.length-1]=[0,0,1.14,0,0,1];
const startOff=k=>GOF[k]>=0?GR[GOF[k]].keys[0]:MV[k].slice(0,3);
function moveAt(t,k,u){const gi=GOF[k];
 if(gi>=0){const g=GR[gi],T=g.times;if(t<T[T.length-1]){let j=0;for(let i=0;i<T.length-1;i++)if(t>=T[i])j=i;return cr(g.keys,j+clamp((t-T[j])/(T[j+1]-T[j])))}
  const A=g.keys[g.keys.length-1];return mix3(A,k+1<NS?startOff(k+1):A,u)}
 if(t<TS[k]){const m=MV[k],f=clamp((t-SCN[k].t)/Math.max(.3,TS[k]-SCN[k].t));return mix3(m.slice(0,3),m.slice(3),sm(f))}
 const A=MV[k].slice(3);return mix3(A,k+1<NS?startOff(k+1):A,u)}
const ENDK=NS-1,PB0=SCN[ENDK].t+2.7;
function camera(t){const g=progress(t),p=g.p;const P=cr(P3,p),T=cr(C3,p);
 const mo=moveAt(t,g.k,g.u);
 {const v=[P[0]-T[0],P[1]-T[1],P[2]-T[2]],cy=Math.cos(mo[0]),sy=Math.sin(mo[0]);let x=v[0]*cy+v[2]*sy,z=-v[0]*sy+v[2]*cy,y=v[1];
  const cp=Math.cos(mo[1]),sp=Math.sin(mo[1]);const y2=y*cp-z*sp,z2=y*sp+z*cp;P[0]=T[0]+x*mo[2];P[1]=T[1]+y2*mo[2];P[2]=T[2]+z2*mo[2]}
 let pb=0;if(false){pb=clamp((t-PB0)/(TOTAL-PB0));pb=pb*pb*(3-2*pb)}
 if(pb>0){const E=C3[ENDK],V=[E[0]+2400,E[1]+3600,E[2]+5200],M=[E[0]*.3,E[1]*.3,E[2]+SP*3.5];for(let a=0;a<3;a++){P[a]=mix(P[a],V[a],pb);T[a]=mix(T[a],M[a],pb)}}
 P[0]+=Math.sin(t*1.1)*16;P[1]+=Math.cos(t*.9)*12;
 const trav=(g.k+1<NS&&SLOT[g.k+1]!==SLOT[g.k])?Math.sin(Math.PI*g.u)*.1*Math.sin(SLOT[g.k]*2.1):0;
 const roll=.03*Math.sin(t*.55+1)+trav,
 fw=T.map((v,a)=>v-P[a]),fl=Math.hypot(...fw);fw[0]/=fl;fw[1]/=fl;fw[2]/=fl;
 const up=[Math.sin(roll),Math.cos(roll),0];let rt=[fw[1]*up[2]-fw[2]*up[1],fw[2]*up[0]-fw[0]*up[2],fw[0]*up[1]-fw[1]*up[0]];const rl=Math.hypot(...rt);rt=rt.map(v=>v/rl);
 const u2=[rt[1]*fw[2]-rt[2]*fw[1],rt[2]*fw[0]-rt[0]*fw[2],rt[0]*fw[1]-rt[1]*fw[0]];
 return{P,fw,rt,u2,p,pb}}
// 3D clouds: each scene's 2D targets get depth, the Imagine scene is a sphere
const CLOUD=[];
function cloud3(){for(let k=0;k<NS;k++){const T=TG[k+1],A=new Float32Array(N*3),o=SCN[k].o;
 for(let i=0;i<N;i++){let lx=T[i*2]-960,ly=T[i*2+1]-420,lz=(hs(i,40+k)-.5)*(o==='cover'||o==='end'?190:240);
  if(o==='imag'){const g=Math.acos(1-2*(i+.5)/N),th=i*2.399963,R=300+(i%7==0?90:0);lx=R*Math.sin(g)*Math.cos(th);ly=R*Math.sin(g)*Math.sin(th);lz=R*Math.cos(g)}
  A[i*3]=lx;A[i*3+1]=-ly;A[i*3+2]=lz}
 CLOUD.push(A)}}
// the super agent: a core, six agents around it, links; then it speaks (waves), then it pushes you to create (agents become new things, sparks)
function sa(i,st,t){
 if(i<650){const g=Math.acos(1-2*(i+.5)/650),th=i*2.399963,r=(i%3==0?90:150)*(1+(st==1?.1*Math.sin(t*7):0)+(st==2?.05*Math.sin(t*4):0));return[r*Math.sin(g)*Math.cos(th),r*Math.sin(g)*Math.sin(th),r*Math.cos(g)]}
 if(i<1550){const q=Math.floor((i-650)/150),w=(i-650)%150,a=q*Math.PI/3+t*.35,rad=st==2?560+60*Math.sin(t+q):500,cxl=Math.cos(a)*rad,czl=Math.sin(a)*rad,cyl=Math.sin(a*1.7+q)*160*(st==2?1.5:1);
  const g=Math.acos(1-2*(w+.5)/150),th=w*2.399963;
  if(st==2){const e=62,fx=((w%5)/4-.5)*2,fy=(((w/5|0)%5)/4-.5)*2,fz=w%2?1:-1;const face=w%3;let x=fx,y=fy,z=fz;if(face==1){x=fz;z=fx}else if(face==2){y=fz;z=fy}return[cxl+x*e,cyl+y*e,czl+z*e]}
  const r=st==1?52:55;return[cxl+r*Math.sin(g)*Math.cos(th),cyl+r*Math.sin(g)*Math.sin(th),czl+r*Math.cos(g)]}
 if(i<1950){const j=i-1550,q=Math.floor(j/66),w=(j%66)/66,a=q*Math.PI/3+t*.35,rad=st==2?560+60*Math.sin(t+q):500,cyl=Math.sin(a*1.7+q)*160*(st==2?1.5:1);const f=st==2?Math.min(1,w*1.15):w;
  return[Math.cos(a)*rad*f+(hs(i,5)-.5)*6,cyl*f+(hs(i,6)-.5)*6,Math.sin(a)*rad*f+(hs(i,7)-.5)*6]}
 const j=i-1950;
 if(st==0){const a=j*2.399963+t*.4,r=240+(hs(i,8)-.5)*40;return[r*Math.cos(a),(hs(i,9)-.5)*14,r*Math.sin(a)]}
 if(st==1){const ring=j%4,a=hs(i,10)*6.2832,r=300+ring*130+((t*140)%130);return[r*Math.cos(a)*.98,r*Math.sin(a)*.98,(hs(i,11)-.5)*10]}
 const u=((t*.5+hs(i,12))%1),a=hs(i,13)*6.2832,b=Math.acos(1-2*hs(i,14)),r=200+u*820;return[r*Math.sin(b)*Math.cos(a),r*Math.sin(b)*Math.sin(a),r*Math.cos(b)]}
function stageF(g,t){let f=0;for(let j=g.a+1;j<=g.b;j++){const w=Math.min(.8,.9*(SCN[j].t-SCN[j-1].t));f+=sm(clamp((t-(SCN[j].t-.55*w))/w))}return f}
function saPos(i,t,g){const f=stageF(g,t),a=Math.min(Math.floor(f),1),m=f-a;const A=sa(i,a,t);if(m<=0)return A;const B=sa(i,a+1,t);return[mix(A[0],B[0],m),mix(A[1],B[1],m),mix(A[2],B[2],m)]}
// the world: one Earth that votes, updates, links up, reaches Mars, and ends with the open padlock
const WL=ix('w4');
function world(i,st,t){const R=330;
 if(i<1000){const gg=Math.acos(1-2*(i+.5)/1000),th=i*2.399963;return[R*Math.sin(gg)*Math.cos(th),R*Math.cos(gg),R*Math.sin(gg)*Math.sin(th)]}
 if(i<1450){const j=i-1000,q=j%3,a=(j/3|0)/150*6.2832,x=R*Math.cos(a),y=R*Math.sin(a),tl=q*1.0472,Y=y*Math.cos(tl),Z=y*Math.sin(tl),ry=q*.9,cs=Math.cos(ry),sn=Math.sin(ry);return[x*cs+Z*sn,Y,-x*sn+Z*cs]}
 const j=i-1450;
 if(st==0){const u=((t*.45+hs(i,20))%1),a=hs(i,21)*6.2832,b=Math.acos(1-2*hs(i,22)),r=R+30+(1-u)*(1-u)*900;return[r*Math.sin(b)*Math.cos(a),r*Math.cos(b),r*Math.sin(b)*Math.sin(a)]}
 if(st==1){const band=j%3,r=R*(1.16+.16*band),a=hs(i,23)*6.2832+t*(1.4-.4*band),tl=.45;return[r*Math.cos(a),r*Math.sin(a)*Math.sin(tl)+(hs(i,24)-.5)*14,r*Math.sin(a)*Math.cos(tl)]}
 if(st==2){const arc=j%19,u=(j/19|0)/49,sp=k=>{const a=hs(arc,k)*6.2832,b=Math.acos(1-2*hs(arc,k+1));return[Math.sin(b)*Math.cos(a),Math.cos(b),Math.sin(b)*Math.sin(a)]};
  const P1=sp(30),P2=sp(32),A=P1.map(v=>v*R),B=P2.map(v=>v*R),d=Math.hypot(P1[0]-P2[0],P1[1]-P2[1],P1[2]-P2[2]),mid=A.map((v,q)=>(v+B[q])/2),ml=Math.hypot(...mid)||1,apex=R*(1+.35*d),c=mid.map((v,q)=>2*(v/ml*apex)-v);
  return A.map((v,q)=>(1-u)*(1-u)*v+2*(1-u)*u*c[q]+u*u*B[q])}
 if(st==3){if(j<520){const g2=Math.acos(1-2*(j+.5)/520),th=j*2.399963;return[1150+150*Math.sin(g2)*Math.cos(th),300+150*Math.cos(g2),-200+150*Math.sin(g2)*Math.sin(th)]}
  const u=((t*.3+(j-520)/430)%1),A=[R*.8,R*.6,0],M=[1150,300,-200],c=[500,1000,0];return A.map((v,q)=>(1-u)*(1-u)*v+2*(1-u)*u*c[q]+u*u*M[q])}
 const P=CLOUD[WL];return[P[i*3]*.85,P[i*3+1]*.85,P[i*3+2]*.85+R+170]}
function worldPos(i,t,g){const f=stageF(g,t),a=Math.min(Math.floor(f),3),m=f-a;const A=world(i,a,t);if(m<=0)return A;const B=world(i,a+1,t);return[mix(A[0],B[0],m),mix(A[1],B[1],m),mix(A[2],B[2],m)]}
// the other groups: the particles flow from one page's shape to the next
function morphPos(i,t,g){const f=stageF(g,t),ia=Math.min(Math.floor(f),g.b-g.a-1),m=f-ia,A=CLOUD[g.a+ia],B=CLOUD[g.a+ia+1];
 const e=sm(clamp((m-.35*hs(i,5))/.65)),bw=Math.sin(Math.PI*e)*420;
 return[mix(A[i*3],B[i*3],e)+(hs(i,6)-.5)*bw,mix(A[i*3+1],B[i*3+1],e)+(hs(i,7)-.5)*bw,mix(A[i*3+2],B[i*3+2],e)+(hs(i,8)-.5)*bw]}
// ambient dust + the thread (one point per slot)
const UC=C3.filter((x,k)=>k===0||SLOT[k]!==SLOT[k-1]),UP=P3.filter((x,k)=>k===0||SLOT[k]!==SLOT[k-1]);
const DUST=[];for(let i=0;i<2600;i++){const u=hs(i,201)*(NSLOT-1+1.2)-.1,c=cr(UC,u);DUST.push([c[0]+(hs(i,202)-.5)*2800,c[1]+(hs(i,203)-.5)*1900,c[2]+(hs(i,204)-.5)*SP*.9,.6+hs(i,205)*1.4])}
const THREAD=[];for(let i=0;i<900;i++){const u=i/899*(UP.length-1),c=cr(UP,u);THREAD.push([c[0]+(hs(i,211)-.5)*10,c[1]-70+(hs(i,212)-.5)*10,c[2]+(hs(i,213)-.5)*10])}
'''
s=s[:a]+eng+s[b:]
a=s.index("function drawScene");b=s.index("function drawCaption")
ds=r'''function drawScene(cm,k,t){const o=SCN[k].o,C=C3[k],s=SCN[k].t;
 const fog=cm.pb>0?mix(4300,16000,cm.pb):4300;const gi=GOF[k];
 const col=i=>(i%9===0)?VERM:'#0D0D0C',sz=i=>(i%9===0?3:1.5)+hs(i,8)*1.4;
 if(gi>=0){const g=GR[gi];if(k!==g.a)return;
  const psi=YAW[k]+.12*Math.sin(t*.6)+(g.sa||g.world?t*.3:0),cs=Math.cos(psi),sn=Math.sin(psi);
  const al=clamp((t-(s-.9))/.9);
  drawPts(cm,null,i=>{const q=g.sa?saPos(i,t,g):g.world?worldPos(i,t,g):morphPos(i,t,g);return[C[0]+q[0]*cs+q[2]*sn,C[1]+q[1],C[2]-q[0]*sn+q[2]*cs]},N,sz,
   g.sa?(i=>(i%9===0||(i>=1950&&i%2==0))?VERM:'#0D0D0C'):g.world?(i=>(i%9===0||(i>=1450&&i%3==0))?VERM:'#0D0D0C'):col,fog,al,0);return}
 const A=CLOUD[k];const lt=t-s;let sw=.95*(1-eo(clamp(lt/.9)));
 if(k+1<NS){const ts=TS[k];if(t>ts)sw=-.95*eio(clamp((t-ts)/TRAV[k]))}
 sw+=.07*Math.sin(t*.8+k);
 const psi=YAW[k]+sw,cs=Math.cos(psi),sn=Math.sin(psi);
 drawPts(cm,null,i=>{const lx=A[i*3],ly=A[i*3+1],lz=A[i*3+2];return[C[0]+lx*cs+lz*sn,C[1]+ly,C[2]-lx*sn+lz*cs]},N,sz,col,fog,1,0)}
'''
s=s[:a]+ds+s[b:]
old="for(let k=0;k<NS;k++){if(Math.abs(k-p)>(cm.pb>0?30:3.4))continue;drawScene(cm,k,t)}"
assert old in s
s=s.replace(old,"for(let k=0;k<NS;k++){const gi=GOF[k],lo=gi>=0?GR[gi].a:k,hi=gi>=0?GR[gi].b:k;if(cm.pb<=0&&(p<lo-3.4||p>hi+3.4))continue;drawScene(cm,k,t)}")
s=s.replace("const fogD=cm.pb>0?mix(5200,15000,cm.pb):5200;","const fogD=cm.pb>0?mix(6200,15000,cm.pb):6200;")
open('docs/videos/aiwa-v24/video.html','w').write(s)
print(len(s),NSLOT if False else '')
