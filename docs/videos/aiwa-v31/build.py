# v31 (stage): the pages of the constellations film (docs/videos/aiwa-v25/video.html) become the foreground of the conductor's stage: places behind (videos of real places), the objects
# that take themselves apart into the points of their sculpture, the hand-overs between places. The plan (video-kit/conductor) says what happens when; this builds the page.
# Run from the repository root:   node video-kit/conductor/conduct.mjs docs/videos/aiwa-v31/film.json --out=/tmp/v31/plan.json ; python3 docs/videos/aiwa-v31/build.py
import json, os, base64, glob
PLAN = json.load(open('/tmp/v31/plan.json'))
s = open('docs/videos/aiwa-v25/video.html').read()
def rep(old, new, count=1):
    global s
    assert old in s, old[:70]
    s = s.replace(old, new, count)
s = s.replace('<title>Aiwa v25</title>', '<title>Aiwa v31</title>')
# --- the words and the symbol: « écran éteint », a power symbol instead of a padlock
rep("Ou même à ton *écran de verrouillage*.", "Ou même à ton *écran éteint*.")
rep("écran verrouillé · micro ouvert", "écran éteint · micro ouvert")
rep("rr(c,960,470,120,90,12);c.fill();c.beginPath();c.arc(960,425,40,Math.PI,0);c.lineWidth=14;c.stroke()", "c.lineWidth=16;c.lineCap='round';c.beginPath();c.arc(960,490,56,-Math.PI/2+.75,-Math.PI/2-.75+Math.PI*2);c.stroke();ln(c,960,410,960,482)")
# --- the credits of the clips used
credits = []
for w in PLAN['worlds'].values():
    j = '/tmp/v31/footage/%s.json' % w['plate']
    try:
        c = json.load(open(j)).get('credit', '')
    except Exception:
        c = ''
    if c and c not in credits: credits.append(c)
cred = ' · '.join(c.replace(' · Wikimedia Commons', '').replace(' · ', ' ') for c in credits)
rep("Musique : « Killers » — Kevin MacLeod (incompetech.com), CC BY 4.0  ·  Voix : synthèse Piper  ·  Polices : Space Grotesk, Fraunces, JetBrains Mono (OFL)", "Vidéos : " + cred.replace("'", " ") + " (Wikimedia Commons, NASA) · Musique Killers, Kevin MacLeod CC BY 4.0 · Voix Piper · Polices OFL")
# --- the worlds (the clips as pictures) and the sprites
WORLDS = {}
for wid, w in PLAN['worlds'].items():
    d = '/tmp/v31/plates/%s' % w['plate']
    n = len(glob.glob(d + '/*.jpg'))
    first = min((b['t0'] for b in PLAN['beats'] if b['world'] == wid), default=0)
    WORLDS[wid] = {'dir': 'file://' + d, 'n': n, 'fps': 30, 'from': max(0, first - 1.6)}
SPR = {}
for name, path in (('phone_on', '/tmp/v31/sprites_final/phone_on/005.png'), ('phone_off', '/tmp/v31/sprites_final/phone_off/005.png'), ('glasses', '/tmp/v31/sprites_final/glasses/000.png')):
    SPR[name] = {'uri': 'data:image/png;base64,' + base64.b64encode(open(path, 'rb').read()).decode()}
stage = open('docs/videos/aiwa-v31/stage.js').read().replace('__PLAN__', json.dumps(PLAN)).replace('__WORLDS__', json.dumps(WORLDS)).replace('__SPRITES__', json.dumps(SPR))
rep("function frame(t){", stage + "\nfunction frame(t){")
# --- the frame: the places instead of the black ground, the objects before the sculptures
rep(" const bg=createRadialGradient" if False else " const bg=cx.createRadialGradient(960,470,80,960,540,1250);bg.addColorStop(0,'#0F1118');bg.addColorStop(1,'#05060A');cx.fillStyle=bg;cx.fillRect(0,0,W,H);\n", "")
rep(" const fogD=cm.pb>0?mix(6200,15000,cm.pb):6200;\n", " const fogD=cm.pb>0?mix(6200,15000,cm.pb):6200;\n actorsFrame(t,cm);stagePlaces(t);for(const q of ACTQ)q();\n")
# --- the sculpture: the points that leave an object start from it
rep(" const lw=Math.max(.7,1.3*F/zcC);\n threads(PA,.5*fa*al*(PB?1-mB:1),'#F1EDE4',lw);if(PB)threads(PB,.5*fa*al*mB,'#F1EDE4',lw);\n for(let i=0;i<N;i++){const zc=SZ[i];if(zc<ZT)continue;const node=i%6===0,v=verm(i);\n  glow(SX[i],SY[i],zc,node?(v?6.5:5):(v?3.4:2.1)+hs(i,8)*1.2,v,fogA(zc,fog)*al*(node?1:.62),fd)}",
    " const lw=Math.max(.7,1.3*F/zcC);const OV=OVR[k];const tf=OV?sm(clamp((OV.m-.45)/.5)):1;\n threads(PA,.5*fa*al*tf*(PB?1-mB:1),'#F1EDE4',lw);if(PB)threads(PB,.5*fa*al*tf*mB,'#F1EDE4',lw);\n"
    " for(let i=0;i<N;i++){const zc=SZ[i];if(zc<ZT)continue;const node=i%6===0,v=verm(i);let sx=SX[i],sy=SY[i];const aa=fogA(zc,fog)*al*(node?1:.62);\n"
    "  if(OV){const e=OV.e[i];if(e<=.001)continue;if(e<.999){const arc=Math.sin(Math.PI*e)*70,wob=Math.sin(i*1.7+t*3)*arc*.35;sx=OV.px[i]+(SX[i]-OV.px[i])*e+wob;sy=OV.py[i]+(SY[i]-OV.py[i])*e-arc*.6;\n"
    "   const pc=OV.a.pts[i].c;cx.save();cx.globalCompositeOperation='lighter';cx.globalAlpha=.9;cx.fillStyle=`rgb(${pc[0]|0},${pc[1]|0},${pc[2]|0})`;cx.beginPath();cx.arc(sx,sy,1.4+1.8*e,0,7);cx.fill();cx.restore();\n"
    "   glow(sx,sy,zc,node?(v?6.5:5):(v?3.4:2.1),v,aa*e*e,fd);continue}}\n"
    "  glow(sx,sy,zc,node?(v?6.5:5):(v?3.4:2.1)+hs(i,8)*1.2,v,aa,fd)}")
# --- ready: the objects are sampled once the engine is built; the render waits for the pictures of the frame
rep("build();cloud3();buildPairs();layout()})();", "build();cloud3();buildPairs();layout();await setupActors()})();")
rep("window.render=t=>frame(t);", "window.render=async t=>{await prepare(t);frame(t)};")
open('docs/videos/aiwa-v31/video-stage.html', 'w').write(s)
print('ok', len(s))
