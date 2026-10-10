# The global film: Aiwa, then the vision

`video.html` is the shot list of the film: the first part is the one of `docs/video-aiwa/video.html` (the short Aiwa film, without its last shot), the second part is `vision.js` (the vision paper, `docs/VISION-SOCIETE-AUGMENTEE.md`). `cues.json` is the voice-over: a few punchy lines placed on the cuts, over a real track from the music register.

Everything that is drawn for the vision (the person who is looked at, the profile that follows them, the visibility choices, the chess game, the layers, the roadmap) is an **illustration** of the vision, marked as such on the picture. None of it has been demonstrated: the paper says so, and the figures on screen (< 5°, < 100 ms, 1 to 3 m) are shown as "objectifs à prouver — pas des résultats".

```
KIT=video-kit
node $KIT/src/cli.js render docs/video-vision/video.html frames/ --seconds=139.55 --fps=30 --width=1920 --height=1080     # 9:16: --width=1080 --height=1920
python3 $KIT/src/voice.py docs/video-vision/cues.json vo.wav 139.55 --kspeed=1.08            # sh $KIT/src/voice-setup.sh once
node music-research/src/cli.js fetch inc-the-complex music.mp3                              # CC BY 4.0: the credit is written at the end of the film
ffmpeg -ss 14.82 -i music.mp3 -t 145 music.wav                                              # start measured so that the strong beats fall on the cuts
node $KIT/src/cli.js assemble frames/ film.mp4 --audio=music.wav --voice=vo.wav --duck=light --crf=29
node $KIT/src/cli.js check film.mp4 --seconds=139.55 --width=1920 --height=1080
```

Music: « The Complex », Kevin MacLeod (incompetech.com), CC BY 4.0 (credit shown at the end of the film).
