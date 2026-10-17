# v27: the constellations film + a real scenic 3D shot (the desk: Poly Haven CC0 models and interior light, Blender) + a synthesised sound design.
# The page is rendered twice: the FILM layer (everything but the captions) and the CAPTIONS layer (transparent): the captions stay above the real shot.
# Built from docs/videos/aiwa-v25/video.html; run from the repository root.
s=open('docs/videos/aiwa-v25/video.html').read()
s=s.replace('<title>Aiwa v25</title>','<title>Aiwa v27</title>')
def rep(old,new,count=1):
    global s
    assert old in s,old[:60]
    s=s.replace(old,new,count)
# the real shot replaces the page from the widget to the end of the glasses (13.75 – 22.06): the sculpture of that group and its flat mock-ups are hidden
rep("al=clamp((t-(s-.9))/.9);","al=clamp((t-(s-.9))/.9)*(g.a===ix('widget')?clamp(1-clamp((t-(s-.45))/.35),0,1):1);")
rep("if(ao>0){OVX.clearRect","if(ao>0&&!(kk===ix('widget')||kk===ix(4)||kk===ix('glasses'))){OVX.clearRect")
rep("Musique : « Killers » — Kevin MacLeod (incompetech.com), CC BY 4.0  ·  Voix : synthèse Piper  ·  Polices : Space Grotesk, Fraunces, JetBrains Mono (OFL)","Musique « Killers » Kevin MacLeod CC BY 4.0 · Voix Piper · Décor 3D Poly Haven CC0 · Sons de synthèse · Polices Space Grotesk, Fraunces, JetBrains Mono (OFL)")
# the two layers
hdr="cx.save();cx.font='500 22px JB';cx.fillStyle='#F1EDE4';cx.globalAlpha=.8;cx.textBaseline='middle';cx.textAlign='left';cx.fillText('AIWA',120,64);cx.restore();"
bar="cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6)}\nwindow.READY"
rep(hdr,"")
rep(" drawCaption(t,1);\n // bloom, vignette, grain"," // bloom, vignette, grain")
rep(bar,"}\nwindow.READY")
rep("function frame(t){\n cx=MAIN;","function frame(t){\n if(LAYER==='caps'){cx=MAIN;cx.globalAlpha=1;cx.globalCompositeOperation='source-over';cx.clearRect(0,0,W,H);const gb=cx.createLinearGradient(0,H-380,0,H);gb.addColorStop(0,'rgba(0,0,0,0)');gb.addColorStop(1,'rgba(0,0,0,.62)');cx.fillStyle=gb;cx.fillRect(0,H-380,W,380);"+hdr+"drawCaption(t,1);cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6);return}\n cx=MAIN;")
rep("const W=1920,H=1080","const LAYER='__LAYER__';const W=1920,H=1080")
rep("html,body{margin:0;background:#05060A}","html,body{margin:0;background:transparent}")
open('docs/videos/aiwa-v27/video-film.html','w').write(s.replace('__LAYER__','film'))
open('docs/videos/aiwa-v27/video-caps.html','w').write(s.replace('__LAYER__','caps'))
print('ok')
