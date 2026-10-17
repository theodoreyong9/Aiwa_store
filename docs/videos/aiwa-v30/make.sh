#!/bin/sh
# v30, from the repository root. Needs the 3D render /tmp/v30/s (609 images at 12 a second: sh docs/videos/aiwa-v30/run_scene.sh, after the dump of the constellations and the render of the
# details: see brief.md), the voice /tmp/v30/vo.wav (video-kit/src/voice.py with the cues of v24, « écran éteint »), the music of v24 and the sound design of v27.
set -e
T=50.717
W=/tmp/v30
python3 docs/videos/aiwa-v30/build.py
[ -f $W/caps/01521.png ] || { rm -rf $W/caps; node video-kit/src/cli.js render docs/videos/aiwa-v30/video-caps.html $W/caps --seconds=$T --width=1920 --height=1080 --workers=4 --transparent; }
rm -rf $W/comp; mkdir -p $W/comp
# 12 -> 30 images a second, to 1080p, a glow round the bright points (they light the room: the bloom is real light), the captions over all
ffmpeg -loglevel error -y -framerate 12 -i $W/s/%05d.png -framerate 30 -i $W/caps/%05d.png -filter_complex \
 "[0:v]minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1,scale=1920:1080:flags=lanczos,unsharp=5:5:0.5,format=gbrp,split=2[a][b];\
[b]curves=all='0/0 0.5/0 0.78/0.45 1/1',gblur=sigma=14,lutrgb=r='val*0.9':g='val*0.95':b='val*1.1'[g];\
[a][g]blend=all_mode=screen:all_opacity=0.6,format=rgba[w];[w][1:v]overlay=0:0:format=auto" \
 -frames:v 1522 -q:v 2 -start_number 0 $W/comp/%05d.jpg
ffmpeg -loglevel error -y -i /tmp/v24/music.wav -i /tmp/v27/sfx.wav -filter_complex "[0:a][1:a]amix=inputs=2:normalize=0:duration=first,volume=1.0" -ar 44100 -ac 2 $W/audio.wav
node video-kit/src/cli.js assemble $W/comp $W/aiwa-v30-16x9.mp4 --audio=$W/audio.wav --voice=$W/vo.wav --duck=light --crf=20 --grade=cinematic --bloom=0.06 --grain=0.012 --vignette=0.18
node video-kit/src/cli.js check $W/aiwa-v30-16x9.mp4 --seconds=$T --width=1920 --height=1080
