# v25 + MCP: the film of v25, with a passage added INSIDE its engine (same constellations, camera, threads, bloom, captions), after « Et te pousse à créer. » and before « Et vote. »:
# « Et ouvre-le aux autres agents. / Une porte MCP. » Two pages of one sculpture that evolves (your agent and the agents outside, then the MCP ring). Everything after is moved later by the length of the passage. Run from the repository root: python3 docs/videos/aiwa-v25-mcp/build.py
import json,re
src=open('docs/videos/aiwa-v25/video.html').read()
def sub(a,b,count=1):
    global src
    assert a in src,a[:60]
    src=src.replace(a,b,count)
# 1) the schedule: four new pages after sa2, the rest moved later
scn=json.loads(re.search(r"const SCN=(\[.*?\]);const TOTAL",src).group(1))
total=float(re.search(r"const TOTAL=([0-9.]+);",src).group(1))
i=[k for k,x in enumerate(scn) if x['o']=='sa2'][0]
base=scn[i+1]['t']                      # where « Et vote. » began
new=[dict(t=0,d=38,o='mcp0',c='Et ouvre-le aux *autres agents*.',v=1.75),
     dict(t=0,d=39,o='mcp1',c='Une porte *MCP*.',v=1.45)]
gaps=[2.0,2.0]               # time from the start of a page to the start of the next (voice + breath)
t=base
for p,g in zip(new,gaps):
    p['t']=round(t,3);t+=g
delta=round(t-base,3)
for x in scn[i+1:]:x['t']=round(x['t']+delta,3)
scn[i+1:i+1]=new
total=round(total+delta,3)
sub(re.search(r"const SCN=\[.*?\];const TOTAL=[0-9.]+;",src).group(0),"const SCN="+json.dumps(scn)+";const TOTAL="+str(total)+";")
# 2) the shapes (masks the points are drawn on): your agent, the agents outside, the ring (the door), a message through it, the validation
shapes=r'''
// 38: your agent (the heart) and the agents outside, joined by threads
SC.push(c=>{c.lineWidth=8;circ(c,960,420,62,1);[[480,250],[1440,230],[520,600],[1400,610]].forEach(([x,y])=>{circ(c,x,y,34,1);ln(c,x,y,960,420)})});
// 39: the door: the MCP ring around the agent, the threads stop at it
SC.push(c=>{c.lineWidth=8;circ(c,960,420,62,1);circ(c,960,420,235,0);[[480,250],[1440,230],[520,600],[1400,610]].forEach(([x,y])=>{circ(c,x,y,34,1);const a=Math.atan2(y-420,x-960);ln(c,x,y,960+Math.cos(a)*235,420+Math.sin(a)*235)})});
const SCALL=SC.slice();'''
sub("const SCALL=SC.slice();",shapes.strip()[0:0]+shapes)
# 3) the group: the four pages are ONE sculpture, filmed by one move (yaw, pitch, distance at the start of each page, then when the camera leaves)
sub(" {a:ix('w0'),b:ix('w4')"," {a:ix('mcp0'),b:ix('mcp1'),keys:[[-.5,.1,1.3],[-.1,.12,1.15],[.3,0,1.2]]},\n {a:ix('w0'),b:ix('w4')")
# 4) the labels of the four pages (the other pages' own style: mono, light, the vermilion for the new thing)
labels=r''' case 'mcp0':case 'mcp1':{const o_=SCN[k].o;
  L('TON AGENT',960,520,.2,{sz:24});L('CHATGPT',480,185,.4,{sz:24});L('CLAUDE',1440,165,.55,{sz:24});L('AGENT',520,665,.7,{sz:24});L('AGENT',1400,675,.85,{sz:24});
  if(o_!=='mcp0')L('MCP',960,150,.15,{sz:34,c:VERM});break}
'''
sub(" case 't0':case 't1':case 't2':case 't3':case 't4':case 't5':{",labels+" case 't0':case 't1':case 't2':case 't3':case 't4':case 't5':{")
sub('<title>Aiwa v25</title>','<title>Aiwa v25 + MCP</title>')
src=src.replace('src:url(fonts/','src:url(../aiwa-v25/fonts/')
open('docs/videos/aiwa-v25-mcp/video.html','w').write(src)
json.dump(dict(scn=scn,total=total),open('docs/videos/aiwa-v25-mcp/sched.json','w'))
# the voice: the cues of v25, the four lines of the new passage, the rest moved later
cues=json.load(open('docs/videos/aiwa-v25/cues.json'))
out=[c for c in cues if c['at']<base-0.01]
new_cues=[("Et ouvre-le aux autres agents.",0),("Une porte M C P.",1)]
for text,q in new_cues:out.append({"at":new[q]['t']+0.02,"text":text})
out+= [{"at":round(c['at']+delta,3),"text":c['text']} for c in cues if c['at']>=base-0.01]
json.dump(out,open('docs/videos/aiwa-v25-mcp/cues.json','w'),ensure_ascii=False)
print('passage',base,'->',round(base+delta,3),'delta',delta,'total',total)
