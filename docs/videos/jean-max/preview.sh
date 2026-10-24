#!/bin/sh
# a quick look at the four plates (small, few samples) to place the cameras before the long final renders
cd /home/user/aiwa_store; mkdir -p /tmp/jean/prev
for S in B C D A; do /tmp/bpyenv/bin/python docs/videos/jean-max/plates.py -- --ph /tmp/ph --shot $S --out /tmp/jean/prev --width 1280 --height 720 --samples 16 > /tmp/jean/prev/log-$S.txt 2>&1; done
echo done > /tmp/jean/prev/DONE
