# v30 — one world that changes places

What v29 did (the constellations, the room and its objects in ONE 3D world, ONE camera) and:
- **The objects take part in the animation.** The points of each sculpture leave the SURFACE of the objects of its theme (the notebook and the phone for the logo, the lamp for the super agent, all five for the world) and go back to them; the objects dissolve (a noise cut) as their points leave. The phone and the glasses of the widget page are the exception: they rise into the middle of the sculpture.
- **The details of the pages are back** (the labels, the mock-ups, the rings of v25): they are rendered flat (`video-ovl.html`) and hung in the room at the sculpture.
- **Several places, in step with the theme**: the lounge (cover, end), a loft studio (the types, with the constellations film playing on its wall), a night city (the widget, the glasses), a neon studio (campaigns), a foundry (conversations, the super agent), a night sky (the world). The camera flies from one place to the next (higher arc, a roll) and the place changes in the middle of the flight; each place drifts slowly.
- « écran de verrouillage » became « écran éteint »: the voice again (Piper), the caption, the phone's screen (off, only the open microphone), the sculpture (a power symbol, not a padlock).
- The render folder carries a stamp (scene.py, data, size, samples, HDRIs): another stamp starts again, the same one goes on.

Pipeline: `node video-kit/src/dump-constellations.mjs /tmp/v30/data 12 docs/videos/aiwa-v30/video-film.html` (from video-kit/), the render of `video-ovl.html` and `screen.html` at 12 a second, `sh run_scene.sh`, `sh make.sh`.
