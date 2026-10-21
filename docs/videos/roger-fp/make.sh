#!/bin/sh
# Roger in the first person (122 s, 16:9), from the repository root. Needs: Chromium (Playwright), ffmpeg, `bpy` in /tmp/bpyenv (python -m venv /tmp/bpyenv; /tmp/bpyenv/bin/pip install bpy), the Poly Haven assets in /tmp/ph
# (node asset-research/src/cli.js fetch ph-lythwood_lounge /tmp/ph/ph-lythwood_lounge, ph-WoodenTable_01, ph-binder_notebook, ph-potted_plant_04, ph-industrial_pipe_lamp), the film that plays on the TV
# (docs/videos/aiwa-v25-mcp, built by its make steps), the voices (video-kit/src/voice.py with roger.json and agent.json, Roger's lowered by ffmpeg asetrate 0.82) and the music bed.
set -e
D=docs/videos/roger-fp; R=/tmp/rfp; mkdir -p $R/tvs $R/phs
# 1) the screens, 12 a second: the TV and the phone, drawn by the page (screens.html); the film of the TV replaces the page from 45 s to 99.8 s
node $D/screens.js
# 2) the 3D room (Cycles, CPU, 960x540, 8 samples); the frames of the film playing on the TV (540..1198) are not rendered: they are laid over one plate (step 3)
/tmp/bpyenv/bin/python $D/scene.py -- --ph /tmp/ph --out $R/s --tv "$R/tvs/%04d.png" --phone "$R/phs/%04d.png" --frames 1464 --fps 12 --width 960 --height 540 --samples 8 --exposure 0.45 --cues "$(cat $D/cues.txt)" --skip 540:1198
# 3) the plate: the room with the TV dark, one still, and where the four corners of the screen fall (tv-corners.json)
# 4) the picture: 12 -> 24 a second (minterpolate), the TV film warped on the plate for 45..99.8 s, the captions layer (caps.js) over all, the sound, check
