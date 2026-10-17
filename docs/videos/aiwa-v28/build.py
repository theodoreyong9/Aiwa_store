# v28: the constellations FUSED with the real room, on every page. The constellations film is rendered on a black ground (no vignette, no grain: those come at the
# end, over the whole), the real desk is the plate (seven camera moves, plate.py), and make.sh projects the one into the other: the points light the desk
# (a blurred copy added to the plate), they are reflected in the wood, and they are screened over it. The captions are a third, transparent layer.
# Built from docs/videos/aiwa-v25/video.html; run from the repository root.
s=open('docs/videos/aiwa-v25/video.html').read()
s=s.replace('<title>Aiwa v25</title>','<title>Aiwa v28</title>')
def rep(old,new,count=1):
    global s
    assert old in s,old[:60]
    s=s.replace(old,new,count)
rep("Musique : « Killers » — Kevin MacLeod (incompetech.com), CC BY 4.0  ·  Voix : synthèse Piper  ·  Polices : Space Grotesk, Fraunces, JetBrains Mono (OFL)","Musique « Killers » Kevin MacLeod CC BY 4.0 · Voix Piper · Décor 3D Poly Haven CC0 · Sons de synthèse · Polices Space Grotesk, Fraunces, JetBrains Mono (OFL)")
hdr="cx.save();cx.font='500 22px JB';cx.fillStyle='#F1EDE4';cx.globalAlpha=.8;cx.textBaseline='middle';cx.textAlign='left';cx.fillText('AIWA',120,64);cx.restore();"
bar="cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6)}\nwindow.READY"
rep(hdr,"")
rep(" drawCaption(t,1);\n // bloom, vignette, grain"," // bloom, vignette, grain")
rep(bar,"}\nwindow.READY")
rep("function frame(t){\n cx=MAIN;","function frame(t){\n if(LAYER==='caps'){cx=MAIN;cx.globalAlpha=1;cx.globalCompositeOperation='source-over';cx.clearRect(0,0,W,H);const gb=cx.createLinearGradient(0,H-380,0,H);gb.addColorStop(0,'rgba(0,0,0,0)');gb.addColorStop(1,'rgba(0,0,0,.55)');cx.fillStyle=gb;cx.fillRect(0,H-380,W,380);"+hdr+"drawCaption(t,1);cx.fillStyle=VERM;cx.fillRect(0,H-6,W*clamp(t/TOTAL),6);return}\n cx=MAIN;")
rep("const W=1920,H=1080","const LAYER='__LAYER__';const W=1920,H=1080")
# no vignette, no grain in the film: they are put on the whole at the end
rep(" cx.drawImage(VG,0,0,W,H);\n","")
import re
i=s.index(" {const ox=Math.floor(hs(Math.floor(t*30),90)"); j=s.index("cx.restore()}",i)+len("cx.restore()}")
s=s[:i]+s[j:]
# black ground: the room is the ground
rep("cx.fillStyle=bg;cx.fillRect(0,0,W,H);","cx.fillStyle='#000';cx.fillRect(0,0,W,H);")
rep("html,body{margin:0;background:#05060A}","html,body{margin:0;background:#000}")
open('docs/videos/aiwa-v28/video-film.html','w').write(s.replace('__LAYER__','film'))
s2=s.replace('html,body{margin:0;background:#000}','html,body{margin:0;background:transparent}')
open('docs/videos/aiwa-v28/video-caps.html','w').write(s2.replace('__LAYER__','caps'))
print('ok')
