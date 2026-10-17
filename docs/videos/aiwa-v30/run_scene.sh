#!/bin/sh
# The 3D render of v30, from the repository root: it can be launched again at any time (same stamp: it goes on where it stopped; another: it starts again).
exec /tmp/bpyenv/bin/python docs/videos/aiwa-v30/scene.py -- --data /tmp/v30/data --ph /tmp/ph --ovl /tmp/v30/ovl --scr /tmp/v30/scrraw --out /tmp/v30/s --width 960 --height 540 --samples 8
