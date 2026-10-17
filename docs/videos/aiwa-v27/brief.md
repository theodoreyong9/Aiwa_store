# Aiwa v27 — constellations, a real scenic shot, and a synthesised sound design

Same words, voices and music as v24–v26. What is new is the **stack** and the **workflow**.

## The stack

| Layer | Tool | What it does |
|---|---|---|
| Abstract, type, UI | the page engine (`video-film.html`, canvas, `window.render(t)`), rendered by `video-kit render` | the constellations, the sculptures that evolve, the camera, the mock-ups |
| Captions | the same page, a second pass (`video-caps.html`, transparent) | the words stay **above** the real shot, with a soft dark gradient under them so they read on a bright table |
| A real place | Blender (Cycles, CPU, `desk-shot.py`) | a desk in a warm room: **Poly Haven (CC0)** models — WoodenTable 01, Binder Notebook, Round Spectacles, Potted Plant 04, Industrial Pipe Lamp — and the interior light « Lythwood Lounge » (2k), copied into the release `assets` by the workflow `asset-mirror.yml` (this session cannot reach Poly Haven). The phone is the only invented object (`kit.phone`: no modern phone in the catalogue), its screen a page (`screen.html`) rendered at 12 images a second |
| Sound | `sfx.py` (numpy) | whooshes under the camera moves, a pluck on each of the six kinds, sonar pings on the lock screen, a room tone under the real shot, a riser and a low boom for the last card — all synthesised, nothing to license |
| Finish | `video-kit assemble --grade=cinematic --bloom --grain --vignette`, one encode | the same look on pages, renders and everything |

## The workflow (`make.sh`)

1. **Look for a base, ask for it**: `node asset-research/src/cli.js search …`, then `asset-mirror.yml` with the ids; download the zips from the release.
2. **A set, not an object**: a table, props with scale and weight, a phone leaning on the notebook, an interior light **dimmed 28× for the camera and kept for the lighting** (a dark, warm, out-of-focus room behind the desk), a warm lamp and a small vermilion rim, a shallow depth of field.
3. **Blocking**: the camera starts on the phone, glides across the notebook and pulls its focus to the spectacles, where a holographic display (the widget's glasses form) lights up; a breath of hand-held shake. Look at stills first (`--still N`), then the 101 images (about 17 minutes on 4 cores, 960x540).
4. **12 → 30 images a second** (`minterpolate`), up to 1080p, faded over the page frames from 13.75 s to 22.06 s (the pages « widget », « écran de verrouillage », « lunette » of the page hide their own sculpture and mock-ups there).
5. **Captions on top**, sound mixed under the voice, **one grade** on the whole.

## Credits and honesty

Poly Haven models and light: CC0 (no credit asked, given anyway, and on the last card). Music « Killers » — Kevin MacLeod (CC BY 4.0). Voice: Piper synthesis. Sounds: synthesised. The desk is a **rendering** (the phone is made up, its screen drawn; no photograph). Not used: Pexels, Pixabay, ambientCG (not reachable from this session; a mirror workflow would be needed, and Pexels/Pixabay want an API key). The 3D shot is 960x540 brought to 1080p.
