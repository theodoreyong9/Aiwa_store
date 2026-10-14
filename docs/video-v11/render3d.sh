#!/bin/sh
# Renders the four 3D scenes of the film v11 (CPU, 1600 x 900, 12 images a second; the montage brings them to 30). Resumes where it stopped.
cd "$(dirname "$0")/shots"
PY=${PY:-/tmp/bpyenv/bin/python}; W=${W:-/tmp/v11}
$PY phone_scene.py -- --out $W/r_off --screen $W/scrn_off/%04d.jpg --frames 56 --width 1600 --height 900 --samples 20 --x 0.74 --arc0 -14 --arc1 8
$PY phone_scene.py -- --out $W/r_widget --screen $W/scrn_widget/%04d.jpg --frames 112 --width 1600 --height 900 --samples 20 --x 0.74 --arc0 -20 --arc1 12
$PY planets_scene.py -- --out $W/r_planets --frames 84 --width 1600 --height 900 --samples 20
$PY glasses_hud.py -- --out $W/r_glasses --asset $W/assets/spectacles/round_spectacles_2k.gltf --hud $W/hud.png --frames 84 --width 1600 --height 900 --samples 20
echo done3d
