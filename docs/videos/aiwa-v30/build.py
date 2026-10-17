# v30: the pages of the constellations film (docs/videos/aiwa-v25/video.html) in two transparent layers:
#  - `ovl`: the flat details of each page (the labels, the mock-ups of the widget and of the glasses, the rings), which scene.py hangs in the 3D room, at the sculpture
#  - `caps`: the captions, over everything
#  - `film`: the engine itself (the points of every page: dumped by video-kit/src/dump-constellations.mjs for scene.py)
# « écran de verrouillage » becomes « écran éteint ». Run from the repository root.
s=open('docs/videos/aiwa-v25/video.html').read()
s=s.replace('<title>Aiwa v25</title>','<title>Aiwa v30</title>')
def rep(old,new,count=1):
    global s
    assert old in s,old[:60]
    s=s.replace(old,new,count)
rep("Ou même à ton *écran de verrouillage*.","Ou même à ton *écran éteint*.")
rep("écran verrouillé · micro ouvert","écran éteint · micro ouvert")
rep("rr(c,960,470,120,90,12);c.fill();c.beginPath();c.arc(960,425,40,Math.PI,0);c.lineWidth=14;c.stroke()","c.lineWidth=16;c.lineCap='round';c.beginPath();c.arc(960,490,56,-Math.PI/2+.75,-Math.PI/2-.75+Math.PI*2);c.stroke();ln(c,960,410,960,482)")   # the phone with a POWER symbol (écran éteint), not a padlock
rep("Musique : « Killers » — Kevin MacLeod (incompetech.com), CC BY 4.0  ·  Voix : synthèse Piper  ·  Polices : Space Grotesk, Fraunces, JetBrains Mono (OFL)","Musique « Killers » Kevin MacLeod CC BY 4.0 · Voix Piper · Décors 3D Poly Haven CC0 · Sons de synthèse · Polices Space Grotesk, Fraunces, JetBrains Mono (OFL)")
hdr="cx.save();cx.font='500 22px JB';cx.fillStyle='#F1EDE4';cx.globalAlpha=.8;cx.textBaseline='middle';cx.textAlign='left';cx.fillText('AIWA',120,64);cx.restore();"
caps="if(LAYER==='caps'){cx=MAIN;cx.globalAlpha=1;cx.globalCompositeOperation='source-over';cx.clearRect(0,0,W,H);const gb=cx.createLinearGradient(0,H-380,0,H);gb.addColorStop(0,'rgba(0,0,0,0)');gb.addColorStop(1,'rgba(0,0,0,.82)');cx.fillStyle=gb;cx.fillRect(0,H-380,W,380);cx.shadowColor='rgba(0,0,0,.75)';cx.shadowBlur=16;"+hdr+"drawCaption(t,1);cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6);return}"
ovl="if(LAYER==='ovl'){cx=MAIN;cx.globalAlpha=1;cx.globalCompositeOperation='source-over';cx.clearRect(0,0,W,H);let kk=0;for(let i=0;i<NS;i++)if(t>=SCN[i].t)kk=i;const lt=t-SCN[kk].t,nx=kk+1<NS?SCN[kk+1].t:1e9;const ao=SCN[kk].o==='end'?clamp((lt-.1)/.3):clamp((lt-.12)/.3)*(1-clamp((t-(nx-.3))/.25));if(ao>0){OVX.clearRect(0,0,W,H);cx=OVX;overlay(t,kk,1);cx=MAIN;cx.globalAlpha=ao;cx.drawImage(ovc,0,0);cx.globalAlpha=1}return}"
rep("function frame(t){\n cx=MAIN;","function frame(t){\n "+caps+"\n "+ovl+"\n cx=MAIN;")
rep("const W=1920,H=1080","const LAYER='__LAYER__';const W=1920,H=1080")
rep("html,body{margin:0;background:#05060A}","html,body{margin:0;background:transparent}")
for name in ('film','caps','ovl'):
    open(f'docs/videos/aiwa-v30/video-{name}.html','w').write(s.replace('__LAYER__',name))
print('ok')
