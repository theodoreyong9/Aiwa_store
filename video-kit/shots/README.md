# Shots that are not drawn by the page: base, transformation, production

The cinematic engine (`template/cinematic.js`) draws text, cards, diagrams, phones and illustrations from HTML: always available, never realistic. A shot that must look **real** (an object, a place, a light, a material, a person) is made in three ways that are used together, none of them a library of finished scenes:

1. **A base from a catalogue**, found by description and downloaded only if kept: 3D models, HDRI lights and PBR textures in `asset-research/` (CC0, Poly Haven: about 520 models, 1000 lights, 870 textures); free music in `music-research/`; a free footage through `footage-mirror.yml` (Internet Archive, Wikimedia Commons, licence written by whoever asks).
2. **A transformation, written for the film**: Blender, headless, on the CPU, with the functions of `shots/blender/kit.py` (import an asset, set it on the floor, bring it to the palette of the film, stage it with a light rig or an HDRI, a camera with depth of field and a move, a beam, render PNG frames) and, for any shot of any origin, `video-kit transform` (grade, bloom, grain, vignette, speed, crop: the same look on every shot, so that a render, a real footage and a clip from a generator read as one picture); a real footage gets our elements over it with `track.py`, `render --transparent` and `assemble --clips`.
3. **A production**, for what exists nowhere: the shapes, the movements and the graphics of the subject, written as a Blender script from the brief (`shots/blender/examples/product.py` makes a shape from nothing) or drawn by the page.

What none of these makes is a **photographic person or place**: that takes a real footage or a video generator of the user's (`video-kit shotlist` writes the prompts). A drawn, rendered or generated shot is never presented as a recording.

| Need | Tool | What it gives | What it costs / limits |
|---|---|---|---|
| An object, a place, a material, a light in real 3D: reflections, depth of field, a camera move | **Blender** with `shots/blender/kit.py` (functions, not scenes): an asset of the catalogue transformed, or a shape made from the brief (`examples/product.py`; `glasses.py` is one more example) | A PNG sequence, used as a full-frame clip, then `transform` | `sh video-kit/src/shots-setup.sh` once (bpy, 400 MB, Python 3.13). CPU only: about 10 to 25 s per 1080p frame, so 3 to 6 s per shot; look at 3 frames at 640x360 first. Procedural shapes look like a render, not like a photograph |
| A real place or person with **our elements over it** (a profile that follows someone, a halo, a label) | **Real footage + `track.py` + `render --transparent` + `--clips`** | The footage, with elements that follow a point | The footage must be free to use *and* the people in it must not be recognisable in a way that implies them in a claim. Sources: `footage-mirror.yml` (Internet Archive, Wikimedia Commons, with licence and page written by whoever asks); Pexels or Pixabay by hand (their sites are not reachable by a program). `track.py` follows a box, it does not recognise anyone |
| Scenes of atmosphere that no tool here can make (a crowd, a street at dusk, a face) | **A video generator** (Veo, Runway, Sora, Kling, Luma…) **of the user's** | A clip, from a prompt written by `video-kit shotlist` | Nothing is generated here: the file comes back from the user into `aiwa-videos/clips/<id>.mp4`. Label it as an illustration |
| Everything else: text, cards, diagrams, phones, UI | **The cinematic engine** (`template/cinematic.js`) | Pure functions of time, any format | Never photographic |

## The flow

1. In the script (stage 2), mark each scene with its tool: `html`, `blender`, `footage` or `generated`. `video-kit shotlist storyboard.json out/` writes `shotlist.md` (what to produce, the prompts for a generator) and `clips.json`, and **refuses** a footage without its licence.
2. Make each shot. A 3D shot: look for a base (`node asset-research/src/cli.js search "..." --kind=model`, then `fetch`), write the script of the shot with `kit.py` (see `examples/product.py`), look at ONE frame first (`--still 40 --width 640 --height 360 --samples 16`), then render. A real footage: `python3 video-kit/src/track.py footage.mp4 track.json --bbox=x,y,w,h`, and a page that reads `track.json` and renders with `--transparent`. Then give every shot the same look: `node video-kit/src/cli.js transform in out.mp4 --grade=cinematic --bloom=0.3 --grain=0.04 --vignette=0.3`.
3. Lay them: `node video-kit/src/cli.js assemble frames/ film.mp4 --audio=music.wav --voice=vo.wav --duck=light --clips=clips.json`. A clip with an alpha channel goes over the picture, an opaque one replaces it, each with a short cross-fade.
4. Critique on the contact sheet as always (`docs/DIRECTOR-PLAYBOOK.md`), then say in the report which shots are real footage, which are 3D, which are generated, which are drawn.

## Honest rules

- A drawn or rendered or generated shot is never presented as a recording. Nothing is added to a real footage that changes what it shows (a profile is an element *over* it, labelled as an illustration of the idea).
- A real footage keeps its source, its licence and the credit it asks for, in `brief.md` and in the film if it asks. The right to the image of people is not settled by the licence of the file.
- What does not work here is said, not faked: no GPU (Blender is slow), no access to most footage sites, no video generator of ours.
