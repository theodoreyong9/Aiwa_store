#!/bin/sh
# Roger for Nexthink (a demonstration): restartable (frames and files already made are kept). Run from the repository root:
#   sh docs/videos/roger-nexthink/make.sh <work dir> <music bed .wav>      -> <work dir>/roger-nexthink.mp4
set -e
W=${1:?work dir}; BED=${2:?bed}; D=docs/videos/roger-nexthink
mkdir -p $W
python3 $D/build.py > $W/build.log
TOTAL=$(python3 -c "import re;print(re.search(r'TOTAL=([0-9.]+)',open('$D/video.html').read()).group(1))")
NODE_PATH=${NODE_PATH:-/usr/local/lib/node_modules_global} node video-kit/src/cli.js render $D/video.html $W/frames --seconds=$TOTAL --fps=30 --width=1920 --height=1080 --workers=4 --quality=92 > $W/render.log 2>&1
[ -f $W/vo.wav ] || { python3 video-kit/src/voice.py $D/cues.json $W/vo_raw.wav $TOTAL --lang=fr > $W/voice.log 2>&1; ffmpeg -loglevel error -y -i $W/vo_raw.wav -af "asetrate=44100*0.82,aresample=44100,atempo=1.2195" $W/vo.wav; }
[ -f $W/music.wav ] || ffmpeg -loglevel error -y -stream_loop -1 -i $BED -t $TOTAL -af "afade=out:st=$(python3 -c "print(round($TOTAL-3,2))"):d=3,aformat=sample_rates=44100:channel_layouts=stereo" $W/music.wav
node video-kit/src/cli.js assemble $W/frames $W/roger-nexthink.mp4 --audio=$W/music.wav --voice=$W/vo.wav --duck=light --crf=18 > $W/asm.log 2>&1
echo done > $W/DONE
