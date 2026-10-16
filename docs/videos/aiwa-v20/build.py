import json,math,re,sys
durs=json.load(open('/tmp/v20/durs.json')) if len(sys.argv)<2 else json.load(open(sys.argv[1]))
V=json.load(open('/tmp/v20/voice_lines.json'))
bar=2.2923;beat=bar/4;half=beat/2
# scenes: (drawer, overlay, caption, voiceIndex or None, extra)
TYPES=[(25,'t0'),(26,'t1'),(27,'t2'),(28,'t3'),(29,'t4'),(30,'t5')]
CAPS=[("Dis-le au *widget*.",1,23,'widget',1.0),
("Ou même à ton *écran de verrouillage*.",2,4,4,0.4),
("Ou à ta *lunette connectée*.",3,11,'glasses',1.0),
("Crée des *campagnes*.",4,10,10,0.0),
("*Personnalisées*.",5,31,31,0.3),
("Et *multicanaux*.",6,32,32,0.3),
("En *un clic*.",7,33,33,0.5),
("Crée des *conversations* inter-sessions.",8,7,7,0.0),
("Pilote un *super agent*.",9,9,9,0.0),
("Qui te *parle*.",10,9,40,0.0),
("Et te *pousse* à créer.",11,9,41,0.0),
("Et *vote*.",13,14,14,0.2),
("Pour les *mises à jour*.",14,35,43,0.2),
("Une *nouvelle idée* du web.",15,36,44,0.0),
("Un web *interplanétaire*.",16,16,16,0.0),
("Et *sans permission*.",17,37,46,0.0)]
scn=[]
scn.append(dict(t=0.0,d=22,o='cover',c='',v=0))
t=4*beat
cues=[]
# page 1: "Imagine" on its own, then each kind is a page
scn.append(dict(t=round(t,3),d=22,o='imag',c='*Imagine*.',v=durs[0]))
cues.append((round(t,3),V[0]))
t=math.ceil((t+durs[0]*1.12+0.08+0.3)/half)*half
for q,(d,o) in enumerate(TYPES):
    scn.append(dict(t=round(t,3),d=d,o=o,c='',v=0))
    t+=beat*1.5
t=t+0.1
t=math.ceil(t/half)*half
for text,vi,d,o,extra in CAPS:
    v=durs[vi]
    scn.append(dict(t=round(t,3),d=d,o=o,c=text,v=v))
    cues.append((round(t,3),V[vi]))
    eff=v*1.12+0.08
    t=math.ceil((t+eff+extra+0.04)/half)*half
end=round(t,3)
scn.append(dict(t=end,d=22,o='end',c='',v=0))
cues.append((round(end+0.45,3),V[18]))
total=math.ceil((end+0.45+durs[18]+3.2)/half)*half
total=round(total,3)
print(end,total,len(scn))
json.dump(dict(scn=scn,total=total),open('/tmp/v20/sched.json','w'))
json.dump([{"at":a,"text":b} for a,b in cues],open('/tmp/v20/cues.json','w'),ensure_ascii=False)
# ---- the page
s=open('/home/user/aiwa_store/docs/videos/aiwa-v19/video.html').read()
a=s.index("const SCH=");b=s.index("const N=2000;")
cfg='const SCN='+json.dumps(scn,ensure_ascii=False)+';const TOTAL='+str(total)+';\nconst NS=SCN.length;const SCH={starts:SCN.map(x=>x.t),total:TOTAL};\nwindow.TOTAL_SECONDS=TOTAL;window.CUTS=SCH.starts.slice();\n'
s=s[:a]+cfg+s[b:]
# new drawers
new_drawers=r'''
// 25-30: one kind at a time, big
SC.push(c=>{c.lineWidth=14;rr(c,960,420,300,380,26);c.stroke();[330,395,460,525].forEach((y,q)=>ln(c,830,y,q==3?1000:1090,y))});
SC.push(c=>{c.lineWidth=14;rr(c,960,420,520,310,46);c.stroke();c.beginPath();c.moveTo(900,340);c.lineTo(1050,420);c.lineTo(900,500);c.closePath();c.fill()});
SC.push(c=>{c.lineWidth=14;rr(c,960,420,560,280,130);c.stroke();ln(c,780,420,880,420);ln(c,830,370,830,470);circ(c,1060,390,26,1);circ(c,1125,450,26,1)});
SC.push(c=>{c.lineWidth=14;rr(c,960,420,640,420,30);c.stroke();ln(c,640,300,1280,300);circ(c,690,258,9,1);circ(c,722,258,9,1);circ(c,754,258,9,1);rr(c,850,420,200,130,12);c.stroke();[390,440,490].forEach(y=>ln(c,990,y,1230,y))});
SC.push(c=>{c.lineWidth=14;rr(c,960,420,270,500,54);c.stroke();ln(c,910,215,1010,215);circ(c,960,625,16,1)});
SC.push(c=>{c.lineWidth=14;rr(c,960,420,440,390,30);c.stroke();txt(c,'{ }',960,430,'700 220px JB')});
// 31: personalised cards
SC.push(c=>{c.lineWidth=7;[[630,320],[960,320],[1290,320],[630,560],[960,560],[1290,560]].forEach(([x,y])=>{rr(c,x,y,290,200,26);c.stroke();circ(c,x-95,y-45,26,0);ln(c,x-45,y-45,x+105,y-45);ln(c,x-110,y+15,x+110,y+15);ln(c,x-110,y+60,x+40,y+60)})});
// 32: four channels
SC.push(c=>{c.lineWidth=10;[555,825,1095,1365].forEach((x,q)=>{rr(c,x,430,220,220,44);c.stroke();
 if(q==0){circ(c,x,420,62,0);ln(c,x-45,470,x-70,515);ln(c,x-70,515,x-20,490)}
 if(q==1){c.beginPath();c.moveTo(x-70,430);c.lineTo(x+75,365);c.lineTo(x+30,490);c.lineTo(x+5,440);c.closePath();c.stroke()}
 if(q==2){rr(c,x,430,130,92,10);c.stroke();c.beginPath();c.moveTo(x-65,395);c.lineTo(x,445);c.lineTo(x+65,395);c.stroke()}
 if(q==3){txt(c,'in',x,440,'700 110px SG')}})});
// 33: one click
SC.push(c=>{c.lineWidth=12;circ(c,930,400,200,0);circ(c,930,400,115,1);c.beginPath();c.moveTo(1010,470);c.lineTo(1010,640);c.lineTo(1055,600);c.lineTo(1090,670);c.lineTo(1120,656);c.lineTo(1086,588);c.lineTo(1145,582);c.closePath();c.fill()});
// 34: growing bars
SC.push(c=>{c.lineWidth=10;[90,150,215,290,375,470].forEach((h,q)=>{rr(c,590+45+q*130,700-h/2,90,h,16);c.stroke()})});
// 35: update arrow
SC.push(c=>{c.lineWidth=22;c.lineCap='round';c.beginPath();c.arc(960,420,200,Math.PI*.15,Math.PI*1.75);c.stroke();c.beginPath();c.moveTo(1118,300);c.lineTo(1160,360);c.lineTo(1085,368);c.closePath();c.fill();txt(c,'v+1',960,425,'700 130px JB')});
// 36: a globe, the web
SC.push(c=>{c.lineWidth=10;circ(c,960,420,235,0);[90,170].forEach(rx=>{c.beginPath();c.ellipse(960,420,rx,235,0,0,7);c.stroke()});ln(c,960,185,960,655);[[420,235],[330,205],[510,205]].forEach(([y,hw])=>ln(c,960-hw,y,960+hw,y))});
// 37: an open padlock
SC.push(c=>{c.lineWidth=28;c.lineCap='round';rr(c,960,520,310,230,32);c.fill();c.beginPath();c.moveTo(865,405);c.lineTo(865,330);c.arc(960,330,95,Math.PI,0);c.lineTo(1055,275);c.stroke();c.globalCompositeOperation='destination-out';circ(c,960,510,28,1);c.fillRect(950,520,20,60);c.globalCompositeOperation='source-over'});
'''
k="const SCALL=SC.slice();"
s=s.replace(k,new_drawers+k,1)
# the widget card centred vertically
s=s.replace("SC.push(c=>{c.lineWidth=LW;rr(c,960,385,800,430,46);c.stroke()});","SC.push(c=>{c.lineWidth=LW;rr(c,960,455,800,430,46);c.stroke()});",1)
s=s.replace("const X0=560,Y0=185;","const X0=560,Y0=255;",1)
s=s.replace("  L('LE WIDGET · FORME LIGHT',960,125,.1,{sz:26,c:VERM,w:600});\n","",1)
s=s.replace("  L('LE WIDGET · FORME GLASSES',960,170,.1,{sz:26,c:VERM,w:600});\n","",1)
# scene building
a=s.index("const SCALL=SC.slice();");b=s.index("const TG=[];")
s=s[:a]+"const SCALL=SC.slice();\nSC.length=0;SCN.forEach(x=>SC.push(SCALL[x.d]));\n"+s[b:]
# captions
a=s.index("let CAPS;");b=s.index("// ---------- drawing ----------")
lay=r'''let CAPS;
function wrap(c,toks,font,maxw,size){c.font=font;const lines=[[]];let x=0;const sp=c.measureText(' ').width;
 toks.forEach(t=>{c.font=t.em?`italic 500 ${size*1.04}px FR`:font;const w=c.measureText(t.w).width;if(x+w>maxw&&lines[lines.length-1].length){lines.push([]);x=0}lines[lines.length-1].push({...t,w:t.w,mw:w,x});x+=w+sp});return lines}
function layout(){
 CAPS=SCN.map(sc=>{let s=sc.c;if(!s)return null;s=s.replace(/\*([^*]+)\*([.,;:!?]+)/g,'*$1$2*');const toks=[];s.split(/(\*[^*]+\*)/).forEach(p=>{if(!p)return;const em=p[0]==='*';(em?p.slice(1,-1):p).split(' ').filter(Boolean).forEach(w=>toks.push({w,em}))});
  const size=96;const font=`500 ${size}px SG`;
  const lines=wrap(cx,toks,font,1600,size);return{lines,size,font,n:toks.length}})}
'''
s=s[:a]+lay+s[b:]
# timeline engine
a=s.index("function sceneAt(t)");b=s.index("function label(")
eng=r'''const WIN=SCN.map((x,k)=>k==0?1.25:Math.max(.4,Math.min(.8,.5*(x.t-SCN[k-1].t))));
const WS=SCN.map((x,k)=>k==0?0:x.t-.6*WIN[k]);
function sceneAt(t){let k=0;for(let i=0;i<NS;i++)if(t>=WS[i])k=i;return k}
function pos(i,t){ // returns x,y,state for particle i
 const k=sceneAt(t),win=WIN[k],winStart=WS[k];
 const T0=TG[k],T1=TG[k+1];
 const del=hs(i,5)*.5, p=clamp((t-winStart-del*win*.6)/(win*.6));
 const e=eio(p);
 let x=mix(T0[i*2],T1[i*2],e),y=mix(T0[i*2+1],T1[i*2+1],e);
 const bow=Math.sin(Math.PI*e)*(hs(i,6)-.5)*(k>0&&k<=6?160:420);
 const dx=T1[i*2]-T0[i*2],dy=T1[i*2+1]-T0[i*2+1],L=Math.hypot(dx,dy)||1;
 x+=-dy/L*bow;y+=dx/L*bow;
 const ph=hs(i,7)*6.28,amp=p>=1?4:9;
 x+=Math.sin(t*1.3+ph)*amp;y+=Math.cos(t*1.1+ph*1.7)*amp;
 return[x,y,k,p]}
function drawCaption(t,inv){
 for(let k=0;k<NS;k++){const C=CAPS[k];if(!C)continue;const s=SCN[k].t,d=Math.max(.5,SCN[k].v);if(t<s-.05)continue;
  let nx=k+1;while(nx<NS&&!CAPS[nx])nx++;const nextS=nx<NS?SCN[nx].t:SCN[NS-1].t;if(t>=nextS)continue;
  const lt=t-s,fade=1-clamp((t-(nextS-.18))/.18);
  const lh=C.size*1.18,y0=H-96-(C.lines.length-1)*lh;let wi=0;const lw=C.lines.map(l=>l.length?l[l.length-1].x+l[l.length-1].mw:0);
  C.lines.forEach((ln_,li)=>{ln_.forEach(w=>{const a0=(wi/C.n)*d*.6;const a=clamp((lt-a0)/.2);wi++;if(a<=0)return;
   cx.save();cx.globalAlpha=a*fade;cx.textBaseline='alphabetic';cx.textAlign='left';
   const ex=Math.round(C.size);
   if(w.em){cx.font=`italic 500 ${ex*1.04}px FR`;cx.fillStyle=VERM}else{cx.font=C.font;cx.fillStyle=col(inv)}
   cx.fillText(w.w,(W-lw[li])/2+w.x,y0+li*lh+(1-eo(a))*22);cx.restore()})})}}
'''
s=s[:a]+eng+s[b:]
# overlay head
s=s.replace("const s=SCH.starts[k],lt0=t-s,lt=Math.max(0,lt0),d=SCH.dur[k];if(lt0<-.6)return;","const s=SCN[k].t,lt0=t-s,lt=Math.max(0,lt0),d=Math.max(.5,SCN[k].v);if(lt0<-.6)return;",1)
s=s.replace("switch(ORIG[k]){","switch(SCN[k].o){",1)
# new overlay cases: inserted before ' case 23:'
cases=r''' case 'cover':{L('Le super agent Claude Code.',960,760,.9,{sz:1});label(t,'Le super agent Claude Code.',960,760,1,{a:0});cx.save();cx.globalAlpha=clamp((lt-.9)/.5);cx.textAlign='center';cx.textBaseline='middle';cx.font='italic 500 76px FR';cx.fillStyle=VERM;cx.fillText('Le super agent Claude Code.',960,760);cx.restore();break}
 case 'end':{cx.save();cx.globalAlpha=clamp((lt-.3)/.6);cx.textAlign='center';cx.textBaseline='middle';cx.font='italic 500 76px FR';cx.fillStyle=VERM;cx.fillText('Ton univers. Ta volonté.',960,760);cx.font='400 20px JB';cx.fillStyle=ink;cx.globalAlpha*=.7;cx.fillText('Musique : « Killers » — Kevin MacLeod (incompetech.com), CC BY 4.0  ·  Voix : synthèse Piper  ·  Polices : Space Grotesk, Fraunces, JetBrains Mono (OFL)',960,1020);cx.restore();break}
 case 't0':case 't1':case 't2':case 't3':case 't4':case 't5':{const n=['DOCUMENTS','VIDÉOS','JEUX','SITES INTERNET','APPLICATIONS ANDROID','SMART CONTRACTS'][+String(SCN[k].o)[1]];L(n,960,700,.05,{sz:40,c:SCN[k].o=='t5'?VERM:ink,w:600});break}
 case 31:['Pour Léa','Pour Marc','Pour Sam','Pour Inès','Pour Tom','Pour Zoé'].forEach((n,q)=>{const x=[630,960,1290,630,960,1290][q],y=[320,560][q<3?0:1];L(n,x+8,y+105,.1+q*.12,{sz:20})});break;
 case 32:['WHATSAPP','TELEGRAM','MAIL','LINKEDIN'].forEach((n,q)=>L(n,[555,825,1095,1365][q],600,.1+q*.15,{sz:26,w:600}));break;
 case 33:{for(let r=0;r<3;r++){const u=((lt*1.1+r/3)%1);cx.save();cx.globalAlpha=(1-u)*.8;cx.strokeStyle=VERM;cx.lineWidth=5;cx.beginPath();cx.arc(930,400,215+u*180,0,7);cx.stroke();cx.restore()}break}
 case 40:{for(let r=0;r<4;r++){const u=((lt*.9+r/4)%1);cx.save();cx.globalAlpha=(1-u)*.85;cx.strokeStyle=VERM;cx.lineWidth=5;cx.beginPath();cx.arc(960,420,130+u*330,-.9,.9);cx.stroke();cx.beginPath();cx.arc(960,420,130+u*330,Math.PI-.9,Math.PI+.9);cx.stroke();cx.restore()}break}
 case 41:{HUB.forEach((p,q)=>{const u=((lt*1.0+q*.13)%1);cx.fillStyle=VERM;cx.globalAlpha=1-u*.6;cx.beginPath();cx.arc(mix(960,p[0],u),mix(420,p[1],u),10,0,7);cx.fill();cx.globalAlpha=clamp((lt-.2-q*.1)/.3);cx.font='700 38px JB';cx.textAlign='center';cx.textBaseline='middle';cx.fillText('+',p[0]+30,p[1]-52)});cx.globalAlpha=1;break}
 case 42:{[90,150,215,290,375,470].forEach((h,q)=>{const a=clamp((lt-.05-q*.12)/.35);if(a<=0)return;cx.save();cx.fillStyle=VERM;cx.globalAlpha=.9;cx.beginPath();cx.roundRect(590+q*130,700-h*a,90,h*a,16);cx.fill();cx.restore()});break}
 case 43:['v1','v2','v3'].forEach((n,q)=>L(n,[760,960,1160][q],700,.1+q*.3,{sz:34,c:q==2?VERM:ink,w:700}));break;
 case 44:L('www',960,700,.2,{sz:34,w:700});break;
 case 46:break;
 case 'widget':case 23:'''
s=s.replace(" case 23:{const A=q=>clamp((lt-.3-q*.18)/.3);"," case 'widget':case 23:{const A=q=>clamp((lt-.3-q*.18)/.3);",1)
s=s.replace(" case 'widget':case 23:{const A"," case 'widget':case 23:{const A",1)
# insert the new cases before the widget case
idx=s.index(" case 'widget':case 23:{const A")
s=s[:idx]+cases.replace(" case 'widget':case 23:","")+s[idx:]
s=s.replace(" case 11:{const A=q=>"," case 'glasses':case 11:{const A=q=>",1)
s=s.replace(" case 4:{for(let r=0;r<4;r++)"," case 4:{for(let r=0;r<4;r++)",1)
# earth-mars: no register line
s=s.replace("L('register(app)  // sans permission',960,740,2.5,{sz:28});","",1)
# frame
a=s.index("function frame(t){");b=s.index("window.READY=")
fr=r'''function frame(t){
 const inv=0;
 cx.fillStyle='rgb(241,237,228)';cx.fillRect(0,0,W,H);
 const fg='rgb(13,13,12)';
 const kk=sceneAt(t);
 for(let i=0;i<N;i++){const[x,y,k,p]=pos(i,t);const acc=i%9===0;cx.fillStyle=acc?VERM:fg;const r=(acc?3.2:1.8)+hs(i,8)*2;cx.beginPath();cx.arc(x,y,r,0,7);cx.fill()}
 cx.save();cx.font='500 22px JB';cx.fillStyle=fg;cx.globalAlpha=.8;cx.textBaseline='middle';cx.textAlign='left';cx.fillText('AIWA',120,64);cx.restore();
 overlay(t,kk,inv);
 drawCaption(t,inv);
 cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6)}
'''
s=s[:a]+fr+s[b:]
s=s.replace("<title>Aiwa v19</title>","<title>Aiwa v20</title>")
open('/home/user/aiwa_store/docs/videos/aiwa-v20/video.html','w').write(s)
