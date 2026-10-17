#!/bin/sh
# v28, from the repository root. Needs: /tmp/v28/plate/<0..6>/%05d.png (the real desk, 58 images at 8 fps per shot: plate.py), /tmp/v27/sfx.wav (v27 sfx.py),
# the voice and the music of v24 (/tmp/v24/vo.wav, /tmp/v24/music.wav).
set -e
T=50.717
W=/tmp/v28
mkdir -p $W
python3 docs/videos/aiwa-v28/build.py
rm -rf $W/comp
# the two layers are rendered once (delete $W/film and $W/caps to render them again)
[ -f $W/film/01521.jpg ] || { rm -rf $W/film; node video-kit/src/cli.js render docs/videos/aiwa-v28/video-film.html $W/film --seconds=$T --width=1920 --height=1080 --workers=4; }
[ -f $W/caps/01521.png ] || { rm -rf $W/caps; node video-kit/src/cli.js render docs/videos/aiwa-v28/video-caps.html $W/caps --seconds=$T --width=1920 --height=1080 --workers=4 --transparent; }
# the plate: each shot smoothed from 8 to 30 images a second, to 1080p, graded dark and cool (the constellations are the light), 7.245 s each, cross-faded over 0.5 s
for k in 0 1 2 3 4 5 6; do
  ffmpeg -loglevel error -y -framerate 8 -i $W/plate/$k/%05d.png \
    -vf "minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1,scale=1920:1080:flags=lanczos,eq=brightness=-0.16:gamma=0.78:saturation=0.78:contrast=1.08,colorbalance=bs=0.16:bm=0.07:rs=-0.05,format=yuv420p" \
    -t 7.245 -c:v libx264 -crf 14 -preset fast $W/plate_$k.mp4
done
ffmpeg -loglevel error -y $(for k in 0 1 2 3 4 5 6; do printf -- "-i $W/plate_$k.mp4 "; done) -filter_complex \
 "[0][1]xfade=transition=fade:duration=0.5:offset=6.745[a];[a][2]xfade=transition=fade:duration=0.5:offset=13.49[b];[b][3]xfade=transition=fade:duration=0.5:offset=20.235[c];[c][4]xfade=transition=fade:duration=0.5:offset=26.98[d];[d][5]xfade=transition=fade:duration=0.5:offset=33.725[e];[e][6]xfade=transition=fade:duration=0.5:offset=40.47,format=yuv420p[p]" \
 -map "[p]" -t $T -c:v libx264 -crf 14 -preset fast $W/plate.mp4
mkdir -p $W/comp
# the fusion: the points light the desk (a blurred copy added), are reflected in the wood (a squashed, flipped, blurred copy on the lower half), and are screened over it; the captions go over all
ffmpeg -loglevel error -y -i $W/plate.mp4 -framerate 30 -i $W/film/%05d.jpg -framerate 30 -i $W/caps/%05d.png -filter_complex \
 "[1:v]format=gbrp,split=3[f1][f2][f3];\
[f1]gblur=sigma=46,eq=brightness=-0.02,lutrgb=r='val*0.55':g='val*0.62':b='val*0.9'[glow];\
[f2]vflip,scale=1920:540,gblur=sigma=6,lutrgb=r='val*0.22':g='val*0.22':b='val*0.3',pad=1920:1080:0:540:black[refl];\
[0:v]format=gbrp[p];\
[p][glow]blend=all_mode=screen[p1];[p1][refl]blend=all_mode=screen[p2];[p2][f3]blend=all_mode=screen,format=rgba[fused];\
[fused][2:v]overlay=0:0:format=auto" \
  -q:v 2 -start_number 0 $W/comp/%05d.jpg
ffmpeg -loglevel error -y -i /tmp/v24/music.wav -i /tmp/v27/sfx.wav -filter_complex "[0:a][1:a]amix=inputs=2:normalize=0:duration=first,volume=1.0" -ar 44100 -ac 2 $W/audio.wav
node video-kit/src/cli.js assemble $W/comp $W/aiwa-v28-16x9.mp4 --audio=$W/audio.wav --voice=/tmp/v24/vo.wav --duck=light --crf=20 --grade=cinematic --bloom=0.10 --grain=0.012 --vignette=0.18
node video-kit/src/cli.js check $W/aiwa-v28-16x9.mp4 --seconds=$T --width=1920 --height=1080
