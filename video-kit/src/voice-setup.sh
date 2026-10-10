#!/bin/sh
# Sets up the natural voice used by voice.py (kokoro: a small neural text-to-speech model, runs on the CPU, French included): the python package and two
# model files (about 350 MB) from GitHub releases. Run it once. Without it, voice.py falls back on espeak-ng (robotic).
set -e
python3 -m pip install -q kokoro-onnx soundfile
DIR="${HOME}/.cache/aiwa-video"
mkdir -p "$DIR"
BASE=https://github.com/thewh1teagle/kokoro-onnx/releases/download/model-files-v1.0
[ -s "$DIR/kokoro-v1.0.onnx" ] || curl -fsSL -o "$DIR/kokoro-v1.0.onnx" "$BASE/kokoro-v1.0.onnx"
[ -s "$DIR/voices-v1.0.bin" ] || curl -fsSL -o "$DIR/voices-v1.0.bin" "$BASE/voices-v1.0.bin"
command -v espeak-ng >/dev/null 2>&1 || apt-get install -y espeak-ng >/dev/null 2>&1 || true
echo "voice ready: $DIR"
