#!/bin/sh
# v31, from the repository root. Needs the plates (real-place footage from the index, extracted to /tmp/v31/plates/<name>/%05d.jpg: see brief.md),
# the sprites, the voice /tmp/v30/vo.wav and the music of v24. The conductor plans, the stage renders, mix.py makes the sound from the same plan.
set -e
T=50.717
W=/tmp/v31
node video-kit/conductor/conduct.mjs docs/videos/aiwa-v31/film.json --pages=cover,imag,t0,t1,t2,t3,t4,t5,widget,4,glasses,10,31,32,33,7,9,40,41,14,43,44,16,46,end \
 --plates=f-night,f-clouds,f-nightwalk,f-orbit,f-datacenter,f-aurora,f-earth,f-mars,f-sunrise --sprites=phone_on,phone_off,glasses --out=$W/plan.json
python3 docs/videos/aiwa-v31/build.py
rm -rf $W/frames
node video-kit/src/cli.js render docs/videos/aiwa-v31/video-stage.html $W/frames --seconds=$T --width=1920 --height=1080 --workers=4
python3 video-kit/conductor/mix.py $W/plan.json $W/audio.wav --music /tmp/v24/music.wav
node video-kit/src/cli.js assemble $W/frames $W/aiwa-v31-16x9.mp4 --audio=$W/audio.wav --voice=/tmp/v30/vo.wav --duck=light --crf=20 --grade=cinematic --bloom=0.06 --grain=0.012 --vignette=0.18
node video-kit/src/cli.js check $W/aiwa-v31-16x9.mp4 --seconds=$T --width=1920 --height=1080
