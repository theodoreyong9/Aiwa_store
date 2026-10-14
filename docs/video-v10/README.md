# Aiwa, version 10 (maximum quality)

The whole product in 2 min 22, 16:9, 1920 x 1080, H.264 (crf 22 after a crf 18 master), AAC at -13.7 LUFS (peak -1.6 dBFS). The stack, mind and body, the screen off, the agent and its commands, what it makes, loops, campaigns, the super agent, the voice, autonomy and the founding papers, **the wallet** (burn of SOL, production of AIWA, claim, ranking by production and not by stock, the rule: a burn makes a claim, a claim restarts the production, which slows with age), hosting in one's own repository, the registry and payment in a session, **games**, **Earth and Mars**, the glasses (an illustration), then what works and what is still to prove.

The stages of `docs/PROMO-VIDEO.md` are in this folder: `facts.md`, `brief.md`, `style.md`, `script.md`, `critique.md`, then `video.html` (the shots) and `cues.json` (the voice). **Every cut falls on a beat** of « The Complex » (104 bpm, a bar = 2.308 s): the shots are listed by the bar they start on; the wallet is on the loudest part of the track, the hosting on its dip.

Real material: `real/wallet.png` and `real/papers.png` are captures of the web app (a throwaway wallet whose recovery words are cropped out). The widget is drawn after the real one.

```
KIT=video-kit
node $KIT/src/cli.js render docs/video-v10/video.html frames/ --seconds=142.1 --fps=30 --width=1920 --height=1080
python3 $KIT/src/voice.py docs/video-v10/cues.json vo.wav 142.1 --lang=fr --kspeed=1.05        # sh $KIT/src/voice-setup.sh once
node music-research/src/cli.js fetch inc-the-complex music.mp3                                 # CC BY 4.0: the credit is in the last scene
ffmpeg -i music.mp3 -t 146 music0.wav                                                          # from 0: the beat grid is 0.029 s + k x 0.5769 s
node $KIT/src/cli.js assemble frames/ master.mp4 --audio=music0.wav --voice=vo.wav --duck=light --crf=18
ffmpeg -i master.mp4 -c:v copy -af loudnorm=I=-14:TP=-1:LRA=9 -c:a aac -b:a 192k film.mp4
ffmpeg -i film.mp4 -c:v libx264 -preset slow -crf 22 -pix_fmt yuv420p -c:a copy docs/aiwa-global-v10-16x9.mp4      # under 100 MB (GitHub's limit per file)
node $KIT/src/cli.js check docs/aiwa-global-v10-16x9.mp4 --seconds=142.1 --width=1920 --height=1080
```

Music: « The Complex », Kevin MacLeod (incompetech.com), CC BY 4.0. Landscape only (no 9:16 yet). Voice: Piper « siwis medium » (French).
