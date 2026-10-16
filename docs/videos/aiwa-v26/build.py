# v26: the constellations film (v25) with ONE real shot: the widget and the lock screen are a lit 3D phone (Blender, a Poly Haven studio HDRI, CC0),
# laid over the page. In the page, the sculpture of those two pages and their flat mock-ups are hidden while the phone is there.
s=open('docs/videos/aiwa-v25/video.html').read()
s=s.replace('<title>Aiwa v25</title>','<title>Aiwa v26</title>')
old="al=clamp((t-(s-.9))/.9);"
assert old in s
s=s.replace(old,"al=clamp((t-(s-.9))/.9)*(g.a===ix('widget')?clamp(1-clamp((t-(s-.45))/.35)+clamp((t-(SCN[g.a+2].t-.3))/.5),0,1):1);",1)
old2="if(ao>0){OVX.clearRect"
assert old2 in s
s=s.replace(old2,"if(ao>0&&!(kk===ix('widget')||kk===ix(4))){OVX.clearRect",1)
open('docs/videos/aiwa-v26/video.html','w').write(s)
print(len(s))
