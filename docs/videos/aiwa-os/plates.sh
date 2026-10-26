#!/bin/sh
# the three plates, restartable: a plate that exists is not made again.   sh docs/videos/aiwa-os/plates.sh [/tmp/os/plates]
cd /home/user/aiwa_store
OUT=${1:-/tmp/os/plates}
mkdir -p $OUT
for S in P T G; do
  [ -f $OUT/corners-$S.json ] || /tmp/bpyenv/bin/python docs/videos/aiwa-os/plates.py -- --ph /tmp/ph --shot $S --out $OUT --width 2560 --height 1440 --samples 96 > $OUT/log-$S.txt 2>&1
done
echo done > $OUT/DONE
