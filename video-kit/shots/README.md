# Shots that are not drawn by the page: which tool for which shot

The cinematic engine (`template/cinematic.js`) draws text, cards, diagrams, phones and illustrations from HTML: always available, never realistic. When a shot must look **real** (a product, a person, a place), choose from this list, in this order of cost. A shot is an *illustration* unless it comes from a real footage: say which on the picture or in the report.

| Need | Tool | What it gives | What it costs / limits |
|---|---|---|---|
| A product (glasses, a device, an object) in real 3D: reflections, depth of field, light, a camera move | **Blender**, `shots/blender/glasses.py` as a model (procedural: no model to download) | A PNG sequence, used as a full-frame clip | `sh video-kit/src/shots-setup.sh` once (bpy, 400 MB, Python 3.13). CPU only: about 10 to 25 s per 1080p frame, so 3 to 6 s per shot; look at 3 frames at 640x360 first. Procedural shapes look like a render, not like a photograph |
| A real place or person with **our elements over it** (a profile that follows someone, a halo, a label) | **Real footage + `track.py` + `render --transparent` + `--clips`** | The footage, with elements that follow a point | The footage must be free to use *and* the people in it must not be recognisable in a way that implies them in a claim. Sources: `footage-mirror.yml` (Internet Archive, Wikimedia Commons, with licence and page written by whoever asks); Pexels or Pixabay by hand (their sites are not reachable by a program). `track.py` follows a box, it does not recognise anyone |
| Scenes of atmosphere that no tool here can make (a crowd, a street at dusk, a face) | **A video generator** (Veo, Runway, Sora, Kling, Luma…) **of the user's** | A clip, from a prompt written by `video-kit shotlist` | Nothing is generated here: the file comes back from the user into `aiwa-videos/clips/<id>.mp4`. Label it as an illustration |
| Everything else: text, cards, diagrams, phones, UI | **The cinematic engine** (`template/cinematic.js`) | Pure functions of time, any format | Never photographic |

## The flow

1. In the script (stage 2), mark each scene with its tool: `html`, `blender`, `footage` or `generated`. `video-kit shotlist storyboard.json out/` writes `shotlist.md` (what to produce, the prompts for a generator) and `clips.json`, and **refuses** a footage without its licence.
2. Make each shot: `python3.13 video-kit/shots/blender/glasses.py --out clips/glasses --frames 150` · `python3 video-kit/src/track.py footage.mp4 track.json --bbox=x,y,w,h` · a page that reads `track.json` and renders with `--transparent`.
3. Lay them: `node video-kit/src/cli.js assemble frames/ film.mp4 --audio=music.wav --voice=vo.wav --duck=light --clips=clips.json`. A clip with an alpha channel goes over the picture, an opaque one replaces it, each with a short cross-fade.
4. Critique on the contact sheet as always (`docs/DIRECTOR-PLAYBOOK.md`), then say in the report which shots are real footage, which are 3D, which are generated, which are drawn.

## Honest rules

- A drawn or rendered or generated shot is never presented as a recording. Nothing is added to a real footage that changes what it shows (a profile is an element *over* it, labelled as an illustration of the idea).
- A real footage keeps its source, its licence and the credit it asks for, in `brief.md` and in the film if it asks. The right to the image of people is not settled by the licence of the file.
- What does not work here is said, not faked: no GPU (Blender is slow), no access to most footage sites, no video generator of ours.
