# v29 — one scene

The constellations, the room and its objects are ONE 3D world filmed by ONE camera (the v28 superposition was rejected: two layers on top of each other).
- `video-kit/src/dump-constellations.mjs` dumps the constellations engine (positions of every point per frame, threads, camera moves relative to each sculpture).
- `scene.py` (Blender, Cycles) builds the Poly Haven desk, puts every point and thread in it as emissive geometry, filming it with the engine's own camera moves around each sculpture's anchor above the desk. The phone and the spectacles rise into the sculpture of their page, the notebook floats under the types, the lamp pulses with the super agent, the plant breathes, the points light the desk.
- `build.py` + `make.sh`: the captions layer, 12 -> 30 images a second, glow, the v27 sound design, the v24 voice and music, assembly and check.
