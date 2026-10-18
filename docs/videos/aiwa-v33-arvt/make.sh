#!/bin/sh
# ARVT film, from the repository root. The places (HDRI) are copied by the `Asset mirror` workflow (ids ph-old_depot, ph-cinema_lobby, ph-abandoned_greenhouse) and unzipped in /tmp/arvt/ph/<name>/.
# Chain: concept checked (novelty) -> places (hdri.py) -> plan (conductor) -> page -> render -> voice -> sound (no music: beds and accents made from the plan) -> assemble -> check.
set -e
W=/tmp/arvt; T=52
node video-kit/conductor/novelty.mjs docs/videos/aiwa-v33-arvt/concept.json
mkdir -p $W/hdri
for n in old_depot cinema_lobby abandoned_greenhouse; do [ -f $W/hdri/$n.json ] || python3 docs/videos/aiwa-v32/hdri.py $W/ph/$n/${n}_2k.hdr $W/hdri $n; done
node video-kit/conductor/conduct.mjs docs/videos/aiwa-v33-arvt/film.json --out=$W/plan.json
node docs/videos/aiwa-v33-arvt/build.mjs $W/plan.json $W/hdri $W/page
N=$((T*30))
[ -n "$RESUME" ] || rm -rf $W/frames
for TRY in $(seq 1 30); do
  node video-kit/src/cli.js render $W/page/index.html $W/frames --seconds=$T --width=1920 --height=1080 --workers=4 || true
  [ "$(ls $W/frames | grep -c '\.jpg$')" -ge "$N" ] && break
done
[ "$(ls $W/frames | grep -c '\.jpg$')" -ge "$N" ] || { echo "the render did not finish"; exit 1; }
node -e "const p=JSON.parse(require('fs').readFileSync('$W/plan.json','utf8'));require('fs').writeFileSync('$W/cues.json',JSON.stringify(p.beats.filter(b=>b.say).map(b=>({at:+(b.t0+.3).toFixed(2),text:b.say}))))"
python3 video-kit/src/voice.py $W/cues.json $W/vo.wav $T > $W/voice.log 2>&1 || { echo "no voice (see $W/voice.log)"; rm -f $W/vo.wav; }
python3 video-kit/conductor/mix.py $W/plan.json $W/audio.wav
node video-kit/src/cli.js assemble $W/frames $W/arvt-16x9.mp4 --audio=$W/audio.wav $( [ -f $W/vo.wav ] && echo --voice=$W/vo.wav ) --duck=light --crf=20 --grade=cinematic --bloom=0.04 --grain=0.012 --vignette=0.1
node video-kit/src/cli.js check $W/arvt-16x9.mp4 --seconds=$T --width=1920 --height=1080
