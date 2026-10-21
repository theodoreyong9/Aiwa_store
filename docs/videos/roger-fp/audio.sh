#!/bin/sh
# Roger in the first person: the sound, from the repository root, after timeline.py.  sh docs/videos/roger-fp/audio.sh <the 16:9 Aiwa film> <the music bed (39 s)> <out.wav>
# The voices: video-kit/src/voice.py (Piper) with roger.json and agent.json; Roger's is lowered (asetrate 0.82, tempo back). The bed is ducked under the voices, the film brings its own sound at film_from.
set -e
D=docs/videos/roger-fp; FILM=$1; BED=$2; OUT=$3; W=$(mktemp -d)
TOTAL=$(python3 -c "import json;print(json.load(open('$D/timeline.json'))['EV']['total'])")
AT=$(python3 -c "import json;print(int(round(json.load(open('$D/timeline.json'))['EV']['film_from']*1000)))")
python3 video-kit/src/voice.py $D/roger.json $W/roger_raw.wav $TOTAL --lang=fr > /dev/null
python3 video-kit/src/voice.py $D/agent.json $W/agent.wav $TOTAL --lang=fr > /dev/null
ffmpeg -loglevel error -y -i $W/roger_raw.wav -af "asetrate=44100*0.82,aresample=44100,atempo=1.2195" $W/roger.wav
ffmpeg -loglevel error -y -i $BED -i $W/roger.wav -i $W/agent.wav -i $FILM -filter_complex "
[0:a]volume=0.5,afade=t=out:st=37:d=2,apad=whole_dur=$TOTAL[m];
[1:a][2:a]amix=inputs=2:normalize=0:duration=longest[v];[v]asplit[v1][v2];
[m][v1]sidechaincompress=threshold=0.03:ratio=8:attack=20:release=400[md];
[md][v2]amix=inputs=2:normalize=0:duration=longest[x];
[3:a]adelay=$AT|$AT,aresample=44100,aformat=channel_layouts=stereo[va];
[x]aformat=channel_layouts=stereo[xs];
[xs][va]amix=inputs=2:normalize=0:duration=longest,alimiter=limit=0.95,afade=t=out:st=$(python3 -c "print($TOTAL-2)"):d=2[a]" -map "[a]" -t $TOTAL -ar 44100 $OUT
rm -rf $W
