#!/bin/sh
# v32, from the repository root. Needs the real places: the HDRI of docs/videos/aiwa-v32/places.md, copied by the `Asset mirror` workflow (the session cannot reach Poly Haven), unzipped in /tmp/v32/ph/<name>/.
# Chain: places (hdri.py) -> plan (conductor) -> page (shader world) -> render -> voice -> sound -> assemble -> check.
set -e
W=/tmp/v32; T=52
mkdir -p $W/hdri
for n in aarfontein_dawn_2 colorful_studio blaubeuren_night little_paris_eiffel_tower venice_sunset tv_studio kiara_1_dawn; do
  [ -f $W/hdri/$n.json ] || python3 docs/videos/aiwa-v32/hdri.py $W/ph/$n/${n}_2k.hdr $W/hdri $n
done
node video-kit/conductor/conduct.mjs docs/videos/aiwa-v32/film.json --out=$W/plan.json
node docs/videos/aiwa-v32/build.mjs $W/plan.json $W/hdri $W/page
N=$((T*30))
[ -n "$RESUME" ] || rm -rf $W/frames
for TRY in $(seq 1 30); do                       # a long render can lose the browser: started again, the frames already on disk are kept
  node video-kit/src/cli.js render $W/page/index.html $W/frames --seconds=$T --width=1920 --height=1080 --workers=4 || true
  [ "$(ls $W/frames | grep -c '\.jpg$')" -ge "$N" ] && break
done
[ "$(ls $W/frames | grep -c '\.jpg$')" -ge "$N" ] || { echo "the render did not finish"; exit 1; }
node -e "const p=JSON.parse(require('fs').readFileSync('$W/plan.json','utf8'));require('fs').writeFileSync('$W/cues.json',JSON.stringify(p.beats.filter(b=>b.say).map(b=>({at:+(b.t0+.3).toFixed(2),text:b.say.replace(/\*/g,'')}))))"
python3 video-kit/src/voice.py $W/cues.json $W/vo.wav $T > $W/voice.log 2>&1 || { echo "no voice (see $W/voice.log)"; rm -f $W/vo.wav; }
ffmpeg -loglevel error -y -i /tmp/v24/music.wav -af "apad=whole_dur=$T" -t $T $W/music.wav
python3 video-kit/conductor/mix.py $W/plan.json $W/audio.wav --music $W/music.wav
node video-kit/src/cli.js assemble $W/frames $W/aiwa-v32-16x9.mp4 --audio=$W/audio.wav $( [ -f $W/vo.wav ] && echo --voice=$W/vo.wav ) --duck=light --crf=20 --grade=cinematic --bloom=0.05 --grain=0.014 --vignette=0.12
node video-kit/src/cli.js check $W/aiwa-v32-16x9.mp4 --seconds=$T --width=1920 --height=1080
