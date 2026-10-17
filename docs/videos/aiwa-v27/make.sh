#!/bin/sh
# v27, from the repository root. Needs: /tmp/v27/desk/%05d.png (the 3D shot, 101 images at 12 fps, desk-shot.py), /tmp/v27/sfx.wav (sfx.py), the voice and the music of v24 (/tmp/v24/vo.wav, /tmp/v24/music.wav).
set -e
T=50.717
W=/tmp/v27
mkdir -p $W
python3 docs/videos/aiwa-v27/build.py
rm -rf $W/film $W/caps $W/comp
node video-kit/src/cli.js render docs/videos/aiwa-v27/video-film.html $W/film --seconds=$T --width=1920 --height=1080 --workers=4
node video-kit/src/cli.js render docs/videos/aiwa-v27/video-caps.html $W/caps --seconds=$T --width=1920 --height=1080 --workers=4 --transparent
mkdir -p $W/comp
# the real shot: smoothed from 12 to 30 images a second, to 1080p, faded in and out over the film from 13.75 s; the captions go over everything
ffmpeg -loglevel error -y -framerate 30 -i $W/film/%05d.jpg \
  -framerate 12 -i $W/desk/%05d.png \
  -framerate 30 -i $W/caps/%05d.png \
  -filter_complex "[1:v]minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1,scale=1920:1080:flags=lanczos,eq=contrast=1.04:saturation=0.96,format=rgba,fade=in:st=0:d=0.55:alpha=1,fade=out:st=7.76:d=0.55:alpha=1,setpts=PTS+13.75/TB[d];[0:v][d]overlay=0:0:eof_action=pass:format=auto[f];[f][2:v]overlay=0:0:format=auto" \
  -q:v 2 -start_number 0 $W/comp/%05d.jpg
# the sound: the music and the sound design, under the voice
ffmpeg -loglevel error -y -i /tmp/v24/music.wav -i $W/sfx.wav -filter_complex "[0:a][1:a]amix=inputs=2:normalize=0:duration=first,volume=1.0" -ar 44100 -ac 2 $W/audio.wav
node video-kit/src/cli.js assemble $W/comp $W/aiwa-v27-16x9.mp4 --audio=$W/audio.wav --voice=/tmp/v24/vo.wav --duck=light --crf=20 --grade=cinematic --bloom=0.10 --grain=0.012 --vignette=0.15
node video-kit/src/cli.js check $W/aiwa-v27-16x9.mp4 --seconds=$T --width=1920 --height=1080
