# The short Aiwa video: sources

`video.html` is the shot list (`SHOTS`) of the video, made with the cinematic engine of `video-kit/` (`template/motion.js`, `template/cinematic.js`); `cues.json` is its voice-over (each line, and the second it starts). The same page renders in 16:9 and in 9:16. Every statement of the video comes from what the product does and from what the published promotional video (`docs/aiwa-promo.mp4`) says today: the voice and the widget, the formats, the agent, the store without permission, the production of AIWA, paying in a session and offline, Earth and Mars, autonomous agents, and the three guarantees. (A first version took its script from an old working file, with scenes the published video no longer has: it was redone from the video itself.)

```
KIT=video-kit
node $KIT/src/cli.js render docs/video-aiwa/video.html frames/ --seconds=83.65 --fps=30 --width=1920 --height=1080     # 9:16: --width=1080 --height=1920
python3 $KIT/src/voice.py docs/video-aiwa/cues.json vo.wav 83.65 --lang=fr --speed=145       # needs espeak-ng (apt-get install -y espeak-ng)
python3 $KIT/src/beat.py bed.wav 83.65 --bpm=104 --drop=4.6 --cuts-file=frames/cuts.json
node $KIT/src/cli.js assemble frames/ out.mp4 --audio=bed.wav --voice=vo.wav --crf=29
node $KIT/src/cli.js check out.mp4 --seconds=83.65 --width=1920 --height=1080
```

The voice is a synthetic voice (robotic) and the music is generated: both are placeholders. The result is `docs/aiwa-promo-cine-16x9.mp4` and `docs/aiwa-promo-cine-9x16.mp4`, next to the longer `docs/aiwa-promo.mp4`, which they do not replace.

A version with a real track (music register) and a few punchy voice lines is `docs/aiwa-promo-musique-16x9.mp4` / `-9x16.mp4`; the global film (Aiwa, then the vision) is in `docs/video-vision/`.
