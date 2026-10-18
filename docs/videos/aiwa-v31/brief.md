# v31 — real places, a conductor

The film is a graph (`film.json`): each beat has an idea, a mood vector (energy, warmth, scale, tension, awe), a place, actors and a transition. The conductor (`video-kit/conductor/`) turns the moods into grade, camera, sound and sculpture, checks the craft rules, and plans the film. The stage (`stage.js`) plays the plan: real-place footage as plates (graded, camera coupled to the constellations camera), transitions (iris, zoom, whip, dissolve), actors that dissolve into the points of the sculpture. `mix.py` makes the sound from the same plan.

Places come from the keyless indexes (`asset-research/catalog/footage.jsonl`, HDRI panoramas): NASA, Wikimedia Commons, Poly Haven, Internet Archive. Clips are fetched on demand by the `Footage` workflow (the sandbox cannot reach those hosts), extracted to `/tmp/v31/plates/<name>/%05d.jpg` (1600x900; ultra-wide sources: `scale=-2:900,crop=1600:900`). No API key anywhere. Credits are built into the end card from each clip's json.

Run: `sh docs/videos/aiwa-v31/make.sh`.
