#!/bin/sh
# the four plates, restartable: a plate that exists is not made again.   sh docs/videos/jean-max/plates.sh [/tmp/jean/plates]
cd /home/user/aiwa_store
OUT=${1:-/tmp/jean/plates}
mkdir -p $OUT
for S in B D C A; do
  [ -f $OUT/corners-$S.json ] || /tmp/bpyenv/bin/python docs/videos/jean-max/plates.py -- --ph /tmp/ph --shot $S --out $OUT --width 2560 --height 1440 --samples 96 > $OUT/log-$S.txt 2>&1
done
echo done > $OUT/DONE
