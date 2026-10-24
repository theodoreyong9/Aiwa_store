#!/bin/sh
# Jean (quality max), restartable (frames and files already made are kept). From the repository root, after plates.sh:
#   sh docs/videos/jean-max/make.sh <work dir> [plates dir]      -> <work dir>/jean.mp4
set -e
W=${1:?work dir}; PL=${2:-/tmp/jean/plates}; D=docs/videos/jean-max
mkdir -p $W
python3 $D/build.py $PL > $W/build.log
TOTAL=$(python3 -c "import re;print(re.search(r'TOTAL=([0-9.]+)',open('$D/video.html').read()).group(1))")
NODE_PATH=${NODE_PATH:-/usr/local/lib/node_modules_global} node video-kit/src/cli.js render $D/video.html $W/frames --seconds=$TOTAL --fps=30 --width=1920 --height=1080 --workers=4 --quality=94 > $W/render.log 2>&1
[ -f $W/vo.wav ] || { python3 video-kit/src/voice.py $D/cues.json $W/vo_raw.wav $TOTAL --lang=fr > $W/voice.log 2>&1; ffmpeg -loglevel error -y -i $W/vo_raw.wav -af "asetrate=44100*0.82,aresample=44100,atempo=1.2195" $W/vo.wav; }
[ -f $W/bed.wav ] || python3 $D/sound.py $D/video.html $W/bed.wav
node video-kit/src/cli.js assemble $W/frames $W/jean.mp4 --audio=$W/bed.wav --voice=$W/vo.wav --duck=light --grade=cinematic --bloom=0.15 --grain=0.025 --vignette=0.25 --crf=16 > $W/asm.log 2>&1
echo done > $W/DONE
