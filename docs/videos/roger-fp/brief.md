# Roger in the first person: the study (before any long render)

**What the film is.** A concept film (an illustration, never presented as a recording): a man, Roger, on his sofa on a Saturday afternoon, tells his phone what to do and validates the result; the film then plays on his TV (Aiwa v25 + MCP) and he asks for the state of his own video.
**The words and the facts of the scenario** are those of `docs/videos/…` and of the conversation (the wake phrase « Hé, mon petit oisillon », the request, « Oui », « Envoie » one by one, the Aiwa film on the TV, « Maintenant envoie-moi où en est la vidéo de moi… »); the names of the guesthouses, the percentages and the « serveur du bureau » are made up and the film says it is a concept.

## What was reread before deciding
- `video-kit/shots/README.md`: a shot that must look real is a **base from the catalogue** (3D models, HDRI lights), **transformed** (Blender, `kit.py`), with the page drawing the screens; a person or a place is never photographic; a drawn or rendered shot is never presented as a recording.
- v30 (`docs/videos/aiwa-v30/`): the room is the **HDRI « lythwood_lounge »** (the lounge) seen by the camera, with objects in front, rendered at 960×540, 8 samples, 12 images a second, brought to 30 by interpolation; the flat details of the pages are rendered apart (`video-ovl.html`) and hung in the room.
- `examples/desk.py`: a phone on a table whose **screen is an image sequence** drawn by a page, a camera that breathes and pulls its focus.
- `asset-research/`: Poly Haven (CC0). Present in the release copy: the HDRIs of v30, `WoodenTable_01`, `binder_notebook`, `potted_plant_04`, `industrial_pipe_lamp`, `round_spectacles`. **No modern TV and no sofa in the copy** (the catalogue has sofas, but they are not in the release): the TV is a made-up object, said.
- `video-kit/conductor/`: made for films from documents (notes, `story.json`, mood, places from footage indexes): the beat and word budget are used, the rest does not fit a fiction.
- `track.py`: not needed here: the screens are on 3D objects (the TV, the phone), so they follow the camera by construction.

## The look
The room is a photographic panorama (the lounge), the foreground is 3D (a low table with a plant, a notebook and a lamp, the phone of the kit lying on it, a made-up flat TV), the camera is the eyes (24 mm, 1.05 m high, breathing, turns to the TV, looks down at the phone). Roger's knees are at the bottom of the frame. Light: the HDRI plus a warm lamp and the blue spill of the TV. Screens: the pages of `docs/videos/roger-*/` drawn by Chromium, laid on the TV and on the phone as image sequences.

## Where it is weak (seen on stills)
The TV on its console still looks stuck on the picture (a made-up box on a photographic floor); contact shadows help only a little. The knees are two cylinders. Hands are not made. The photographic room behind is the strength of the shot.

## Cost
Cycles on the CPU (4 cores): about 13 s for a 960×540 still at 24 samples, with the scene loaded; a frame of the film is about 5 to 9 s. At 12 images a second a minute of film is 720 frames: about 1 to 2 hours. The 55 s of the Aiwa film on the TV can be laid on a still plate (a static camera) instead of being rendered image by image.
