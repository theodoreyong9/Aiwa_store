# Aiwa v26 — the constellations film with one real shot

Same words, voices, music and camera as v25 (`../aiwa-v25/`). One difference: the pages « Dis-le au widget » and « Ou même à ton écran de verrouillage » are a **lit 3D phone** instead of the flat mock-ups.

How it is made (all of it in this folder and in `video-kit/`):

1. `screen.html` — the screen of the phone (the Aiwa widget on a home screen, then the lock screen with the microphone open), drawn from time, rendered at 12 images a second (`video-kit render … --fps=12`).
2. `phone-shot.py` — Blender (`pip install bpy`, Python 3.13), headless, CPU: `kit.phone()` with that screen, lit by a real studio, a slow orbit, transparent background, 960x540, 65 frames (about 7 minutes on 4 cores).
3. The 12 fps render is smoothed to 30 fps with `minterpolate`, colour and alpha apart (ffmpeg's filter has no alpha), then laid over the page frames (`ffmpeg overlay`) from 13.75 s; the page hides its own sculpture and mock-ups while the phone is there (`build.py`).
4. `video-kit assemble` with the voice and the music of v24/v25.

Credits: the studio light is **Monochrome Studio 02** (Poly Haven, CC0: no credit asked, given here anyway), copied into the release `assets` by the workflow `asset-mirror.yml` (this session cannot reach Poly Haven). The phone is made from nothing (`kit.phone`), the screen is drawn: it is a **rendering**, not a photograph. Music « Killers » — Kevin MacLeod (CC BY 4.0); voice: Piper synthesis.

Limits: the 3D shot is 960x540 brought to 1080p (a little soft); only two pages are 3D; Pexels footage is not used (no access from here, and an API key is needed).
