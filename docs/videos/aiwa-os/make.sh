#!/bin/sh
# Aiwa, the film « Aiwa, l'OS du vibecoder » (1 min 20), restartable (frames and files already made are kept). From the repository root:
#   sh docs/videos/aiwa-os/make.sh <work dir> [fast]     -> <work dir>/aiwa-os.mp4   (fast: lighter frames and a lighter encode, a first look to check the rhythm)
set -e
W=${1:?work dir}; MODE=${2:-final}; D=docs/videos/aiwa-os
mkdir -p $W
python3 - <<PY
import json
sched = json.load(open('$D/sched.json'))
open('$D/video.html', 'w').write(open('$D/page.html').read().replace('__SCHED__', json.dumps(sched, ensure_ascii=False)))
PY
TOTAL=80
if [ "$MODE" = fast ]; then Q=72; CRF=24; else Q=94; CRF=16; fi
NODE_PATH=${NODE_PATH:-/usr/local/lib/node_modules_global} node video-kit/src/cli.js render $D/video.html $W/frames-$MODE --seconds=$TOTAL --fps=24 --width=1920 --height=1080 --workers=4 --quality=$Q > $W/render-$MODE.log 2>&1
[ -f $W/vo.wav ] || { python3 video-kit/src/voice.py $D/cues.json $W/vo_raw.wav $TOTAL --lang=fr > $W/voice.log 2>&1; ffmpeg -loglevel error -y -i $W/vo_raw.wav -af "asetrate=44100*0.88,aresample=44100,atempo=1.1364" $W/vo.wav; }
[ -f $W/bed.wav ] || python3 $D/sound.py $D/video.html $W/bed.wav
node video-kit/src/cli.js assemble $W/frames-$MODE $W/aiwa-os-$MODE.mp4 --fps=24 --audio=$W/bed.wav --voice=$W/vo.wav --duck=light --grade=cinematic --bloom=0.12 --grain=0.02 --vignette=0.22 --crf=$CRF > $W/asm-$MODE.log 2>&1
echo done > $W/DONE-$MODE
