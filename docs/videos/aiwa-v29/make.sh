#!/bin/sh
# v29, from the repository root. One scene: needs /tmp/v28/data (video-kit/src/dump-constellations.mjs docs: node video-kit/_dump.mjs /tmp/v28/data 12), the screen of the phone
# (/tmp/v28/scr: screen.html rendered at 12 fps, 8.4 s, one file per film frame), the Poly Haven models in /tmp/ph, the sound design of v27 (/tmp/v27/sfx.wav),
# the voice and the music of v24 (/tmp/v24/vo.wav, /tmp/v24/music.wav). The real-time 3D render (hours): scene.py -- --out /tmp/v28/s --width 960 --height 540 --samples 8
set -e
T=50.717
W=/tmp/v29
mkdir -p $W
python3 docs/videos/aiwa-v29/build.py
[ -f $W/caps/01521.png ] || { rm -rf $W/caps; node video-kit/src/cli.js render docs/videos/aiwa-v29/video-caps.html $W/caps --seconds=$T --width=1920 --height=1080 --workers=4 --transparent; }
rm -rf $W/comp; mkdir -p $W/comp
# the 3D film: 12 -> 30 images a second, to 1080p, a glow round the bright points (they light the room: the bloom is real light), the captions over all
ffmpeg -loglevel error -y -framerate 12 -i /tmp/v28/s/%05d.png -framerate 30 -i $W/caps/%05d.png -filter_complex \
 "[0:v]minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1,scale=1920:1080:flags=lanczos,unsharp=5:5:0.5,format=gbrp,split=2[a][b];\
[b]curves=all='0/0 0.5/0 0.78/0.45 1/1',gblur=sigma=14,lutrgb=r='val*0.9':g='val*0.95':b='val*1.1'[g];\
[a][g]blend=all_mode=screen:all_opacity=0.7,format=rgba[w];[w][1:v]overlay=0:0:format=auto" \
 -frames:v 1522 -q:v 2 -start_number 0 $W/comp/%05d.jpg
ffmpeg -loglevel error -y -i /tmp/v24/music.wav -i /tmp/v27/sfx.wav -filter_complex "[0:a][1:a]amix=inputs=2:normalize=0:duration=first,volume=1.0" -ar 44100 -ac 2 $W/audio.wav
node video-kit/src/cli.js assemble $W/comp $W/aiwa-v29-16x9.mp4 --audio=$W/audio.wav --voice=/tmp/v24/vo.wav --duck=light --crf=20 --grade=cinematic --bloom=0.06 --grain=0.012 --vignette=0.18
node video-kit/src/cli.js check $W/aiwa-v29-16x9.mp4 --seconds=$T --width=1920 --height=1080
