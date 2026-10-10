# The one-minute Aiwa video: sources

`video.html` is the shot list (`SHOTS`) of the video, made with the cinematic engine of `video-kit/` (`template/motion.js`, `template/cinematic.js`); `cues.json` is its voice-over (each line, and the second it starts). The same page renders in 16:9 and in 9:16. Every statement of the video comes from what the product does and from what the earlier promotional video already said.

```
KIT=video-kit
node $KIT/src/cli.js render docs/video-aiwa/video.html frames/ --seconds=58.7 --fps=30 --width=1920 --height=1080     # 9:16: --width=1080 --height=1920
python3 $KIT/src/voice.py docs/video-aiwa/cues.json vo.wav 58.7 --lang=fr --speed=145       # needs espeak-ng (apt-get install -y espeak-ng)
python3 $KIT/src/beat.py bed.wav 58.7 --bpm=104 --drop=22.3 --cuts-file=frames/cuts.json
node $KIT/src/cli.js assemble frames/ out.mp4 --audio=bed.wav --voice=vo.wav --crf=29
node $KIT/src/cli.js check out.mp4 --seconds=58.7 --width=1920 --height=1080
```

The voice is a synthetic voice (robotic) and the music is generated: both are placeholders. The result is `docs/aiwa-promo-cine-16x9.mp4` and `docs/aiwa-promo-cine-9x16.mp4`, next to the longer `docs/aiwa-promo.mp4`, which they do not replace.
