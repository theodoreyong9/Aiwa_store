# v25-3: the film of v25-2 with the scenarios: three pages after « Et te pousse à créer. »: « Lance des scénarios. » (two silhouettes, the second dashed: the double),
# « Un mot, et ton double s'ouvre. » and « Traduction instantanée. » (two bubbles, FR and EN). The rest moves later.
# Run from the repository root, after v25-2's build: python3 docs/videos/aiwa-v25-3/build.py
import json,re
src=open('docs/videos/aiwa-v25-2/video.html').read()
sched=json.load(open('docs/videos/aiwa-v25-2/sched.json'))
scn,total=sched['scn'],sched['total']
# the voice lengths measured with video-kit/src/voice.py (Piper): 1.23 s, 1.37 s; a gap of 0.4 s like the other pages
NEW=[('sc0','duo','Lance des *scénarios*.',1.23,'Lance des scénarios.'),('sc1','more','Et bien plus *encore*.',1.37,'Et bien plus encore.')]
at=[k for k,x in enumerate(scn) if x['o']=='w0'][0]
t0=scn[at]['t']
t=t0; ins=[]; cues_new=[]
for o,d,c,v,spoken in NEW:
    ins.append({'t':round(t,3),'d':d,'o':o,'c':c,'v':v}); cues_new.append({'at':round(t,3),'text':spoken}); t+=v+0.4
delta=round(t-t0,3)
for x in scn[at:]: x['t']=round(x['t']+delta,3)
scn[at:at]=ins
total=round(total+delta,3)
sub=re.search(r"const SCN=\[.*?\];const TOTAL=[0-9.]+;",src).group(0)
src=src.replace(sub,"const SCN="+json.dumps(scn,ensure_ascii=False)+";const TOTAL="+str(total)+";",1)
drawers=r'''
// the scenarios: a person and her double (dashed), then two bubbles that translate
SCALL.push(c=>{c.lineWidth=14;c.lineCap='round';[[800,0],[1120,1]].forEach(([x,dash])=>{c.setLineDash(dash?[26,20]:[]);circ(c,x,340,72,0);c.beginPath();c.arc(x,590,150,Math.PI,0);c.closePath();c.stroke()});c.setLineDash([]);ln(c,930,470,990,470);ln(c,930,470,952,448);ln(c,930,470,952,492);ln(c,990,470,968,448);ln(c,990,470,968,492)});
SCALL.push(c=>{c.lineWidth=18;c.lineCap='round';ln(c,960,250,960,590);ln(c,790,420,1130,420);[[620,300],[1300,300],[600,560],[1320,560],[760,200],[1160,640],[1200,210],[720,640]].forEach(([x,y],q)=>{q%2?circ(c,x,y,22,0):circ(c,x,y,14,1)})});
const DUO=SCALL.length-2,MORE=SCALL.length-1;SCN.forEach(x=>{if(x.d==='duo')x.d=DUO;if(x.d==='more')x.d=MORE});
'''
a="SC.length=0;SCN.forEach(x=>SC.push(SCALL[x.d]));"
assert a in src
src=src.replace(a,drawers+a,1)
src=src.replace('<title>Aiwa v25-2</title>','<title>Aiwa v25-3</title>').replace('src:url(../aiwa-v25/fonts/','src:url(../aiwa-v25/fonts/')
open('docs/videos/aiwa-v25-3/video.html','w').write(src)
json.dump(dict(scn=scn,total=total),open('docs/videos/aiwa-v25-3/sched.json','w'),ensure_ascii=False)
cues=json.load(open('docs/videos/aiwa-v25-2/cues.json'))
out=[]
for c in cues:
    if c['at']>=t0-0.01: c={'at':round(c['at']+delta,3),'text':c['text']}
    out.append(c)
out=sorted(out+cues_new,key=lambda c:c['at'])
json.dump(out,open('docs/videos/aiwa-v25-3/cues.json','w'),ensure_ascii=False)
print('added',delta,'s; total',total)
