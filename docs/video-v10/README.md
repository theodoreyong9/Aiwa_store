# Aiwa, version 10

The whole product in one film (2 min 36, 16:9): the stack, the mind and the body, the screen off, the agent and its commands, what it makes, sessions and loops, campaigns, the super agent, the voice, the autonomy and the founding papers, hosting in one's own repository, the wallet, the registry and the payment, the glasses (an illustration), then what works and what is still to prove.

`video.html` is the shot list (`SHOTS`), `cues.json` the voice-over (Piper « siwis medium », French, through sherpa-onnx). Everything drawn as an example or as the vision says so on the picture; the last scene says what is done and what is still to prove.

```
KIT=video-kit
node $KIT/src/cli.js render docs/video-v10/video.html frames/ --seconds=156 --fps=30 --width=1920 --height=1080
python3 $KIT/src/voice.py docs/video-v10/cues.json vo.wav 156 --lang=fr --kspeed=1.0          # sh $KIT/src/voice-setup.sh once
node music-research/src/cli.js fetch inc-the-complex music.mp3                               # CC BY 4.0: the credit is in the last scene
ffmpeg -ss 14.82 -i music.mp3 -t 160 music.wav
node $KIT/src/cli.js assemble frames/ docs/aiwa-global-v10-16x9.mp4 --audio=music.wav --voice=vo.wav --duck=light --crf=29
node $KIT/src/cli.js check docs/aiwa-global-v10-16x9.mp4 --seconds=156 --width=1920 --height=1080
```

Music: « The Complex », Kevin MacLeod (incompetech.com), CC BY 4.0. The music start (14.82 s) is the one of version 9: the strong beats do **not** fall on the cuts of this film. Landscape only (no 9:16 version yet).
