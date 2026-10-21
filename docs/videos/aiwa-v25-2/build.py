# v25-2: the film of v25 with the words the person asked to change. « écran de verrouillage » becomes « écran éteint » (the voice, the caption, the label, and the phone's sculpture: a power
# symbol instead of the padlock), « ta lunette connectée » becomes « tes lunettes connectées », and « Et multicanaux. » and « En un clic. » are removed (their two pages go, the rest moves up).
# Run from the repository root: python3 docs/videos/aiwa-v25-2/build.py
import json,re
src=open('docs/videos/aiwa-v25/video.html').read()
def sub(a,b,count=1):
    global src
    assert a in src,a[:60]
    src=src.replace(a,b,count)
scn=json.loads(re.search(r"const SCN=(\[.*?\]);const TOTAL",src).group(1))
total=float(re.search(r"const TOTAL=([0-9.]+);",src).group(1))
# 1) words
for x in scn:
    if x['o']==4: x['c']='Ou même à ton *écran éteint*.'; x['d']=38
    if x['o']=='glasses': x['c']='Ou à tes *lunettes connectées*.'
# 2) the two pages that go (o 32 « multicanaux », o 33 « un clic »)
i32=[k for k,x in enumerate(scn) if x['o']==32][0]; i33=[k for k,x in enumerate(scn) if x['o']==33][0]
nxt=scn[i33+1]['t']; delta=round(nxt-scn[i32]['t'],3)
del scn[i32:i33+1]
for x in scn[i32:]: x['t']=round(x['t']-delta,3)
total=round(total-delta,3)
sub(re.search(r"const SCN=\[.*?\];const TOTAL=[0-9.]+;",src).group(0),"const SCN="+json.dumps(scn)+";const TOTAL="+str(total)+";")
# 3) the group of the campaign pages ends at « Personnalisées »: two pages, three camera keys
sub("{a:ix(10),b:ix(33),keys:[[-.14,.08,1.15],[.1,-.05,1.08],[-.08,.08,1.1],[.06,0,1.0],[0,0,1]]}","{a:ix(10),b:ix(31),keys:[[-.14,.08,1.15],[.1,-.05,1.08],[0,0,1]]}")
# 4) the phone of « écran éteint »: its outline and a power symbol (no padlock); the label
power=r'''
// 38: the phone with its screen off: a power symbol
SC.push(c=>{c.lineWidth=LW;rr(c,960,420,300,580,50);c.stroke();c.lineWidth=16;c.lineCap='round';c.beginPath();c.arc(960,440,70,-Math.PI*.32,Math.PI*1.32);c.stroke();ln(c,960,350,960,430)});
const SCALL=SC.slice();'''
sub("const SCALL=SC.slice();",power)
sub("écran verrouillé · micro ouvert","écran éteint · micro ouvert")
sub('<title>Aiwa v25</title>','<title>Aiwa v25-2</title>')
src=src.replace('src:url(fonts/','src:url(../aiwa-v25/fonts/')
open('docs/videos/aiwa-v25-2/video.html','w').write(src)
json.dump(dict(scn=scn,total=total),open('docs/videos/aiwa-v25-2/sched.json','w'))
# the voice: the cues of v25 with the new words, the two lines removed, the rest moved up
cues=json.load(open('docs/videos/aiwa-v25/cues.json'))
t32=0
out=[]
for c in cues:
    if c['text']=='Ou même à ton écran de verrouillage.': c={'at':c['at'],'text':'Ou même à ton écran éteint.'}
    if c['text']=='Ou à ta lunette connectée.': c={'at':c['at'],'text':'Ou à tes lunettes connectées.'}
    if c['text'] in ('Et multicanaux.','En un clic.'): continue
    if c['at']>=nxt-0.01: c={'at':round(c['at']-delta,3),'text':c['text']}
    out.append(c)
json.dump(out,open('docs/videos/aiwa-v25-2/cues.json','w'),ensure_ascii=False)
print('removed',delta,'s; total',total)
