#!/bin/sh
# Roger in the first person (126 s, 16:9), from the repository root. Needs: Chromium (Playwright), ffmpeg, `bpy` in /tmp/bpyenv (python -m venv /tmp/bpyenv; /tmp/bpyenv/bin/pip install bpy),
# the Poly Haven assets in /tmp/ph (node asset-research/src/cli.js fetch ph-lythwood_lounge /tmp/ph/ph-lythwood_lounge, ph-WoodenTable_01, ph-binder_notebook, ph-potted_plant_04, ph-industrial_pipe_lamp),
# the film that plays on the TV (docs/videos/aiwa-v25-2) and the voices (video-kit/src/voice.py with roger.json and agent.json; Roger's lowered by ffmpeg asetrate 0.82).
# The timeline is schedule.json / timeline.json (the lines, when they start, when the sites, the film and the progress appear). The pages are drawn by screens.html (the TV, the phone, the captions).
set -e
D=docs/videos/roger-fp; R=/tmp/r2; mkdir -p $R/tvs $R/phs $R/tv24 $R/caps $R/s
node $D/capture.js screens      # the TV and the phone, 12 a second (the film of the TV is laid in the TV sequence from 58.2 s)
node $D/capture.js tv24         # the TV alone, 24 a second, for the two stretches laid over a plate (13.25..58.45 and 105.35..126)
node $D/capture.js caps         # the captions layer (transparent), 24 a second
# 3D: only where the camera moves (0..13.5 s, the zoom in at 58.2 s, the zoom out at 103.2 s); Cycles, CPU, 960x540, 8 samples
/tmp/bpyenv/bin/python $D/scene.py -- --ph /tmp/ph --out $R/s --tv "$R/tvs/%04d.png" --phone "$R/phs/%04d.png" --frames 1512 --fps 12 --width 960 --height 540 --samples 8 --exposure 0.45 \
  --cues "0:room,3.4:room,4.4:tv,7.2:tv,9.0:phone,11.2:phone,13.4:tv,126:tv" --zoom "58.2:56,60.4:90,103.2:90,105.47:56" --only 0:162,698:727,1238:1267
# two plates (the room, the TV dark): at 56 mm (still 400) and at 90 mm (still 900); tv-corners.json says where the screen falls; 96 samples
# the stretches: 12 -> 24 a second (minterpolate) for the 3D pieces; the TV laid on a plate with a glow for the others; crossfades of 0.12 to 0.25 s cut as separate pieces; the captions over all; the sound; check
