# Promotional videos: the standard

What a Claude Code session reads when asked for a promotional video of a creation (the widget's "Vidéo" instruction points here). The tools are in `video-kit/`. A video is a deliverable like the creation itself: made by a script, checked by a script, and stored in the creation's own GitHub repository.

## 1. What the user gets

A video without any video-editing skill needed. The steps: **brief** (a description, an address, a logo, photos, screenshots, a product sheet, or just the creation itself) → **analysis and concept** (target, promise, hook, script, storyboard) → **production** (media, scenes, animation, voice if available, music, editing) → **check** (technical, visual, sound, advertising) → **delivery** (the final video, the other formats, variants).

Two modes: automatic, and intermediate validation (the storyboard is shown before the render, through a *demande* of the widget). The user is never forced to validate every scene.

## 2. Rules that do not bend

- **Nothing is invented.** Every statement of the video is traceable to what the user gave or to what the creation shows. No statistic, result, testimonial, guarantee or "before / after" that is not authentic and authorised. Assumptions are said in one sentence.
- **A brief may be incomplete.** Fill in only what can be deduced from the available sources, and say which hypotheses matter.
- **Media have a source.** Priority: what the user provided; captures and resources of the real product (Chromium screenshots of the creation); free libraries whose terms were checked; graphics made locally. Each medium is recorded with its source, its type, its size, the rights known and the scene that uses it. A missing medium is replaced by an authorised one or reported, never filled in silently with an incoherent scene.
- **One art direction** for the whole video: colours, typefaces, contrast, rhythm, transitions, the way the logo is used. No change of style from scene to scene without a reason.
- **Free and local first.** ffmpeg, Chromium, local graphics. An external service only when its value justifies its cost, and the cost is said before it is spent. Free software, a downloadable model, a free hosted service and a free API are four different things: say which one is used.

## 3. Script and storyboard

Scenes, in order. Each has an identifier, a planned duration, a narrative goal, the narration or dialogue, the text shown, the main medium, the movement or framing, the transitions, the sound, and the call to action if any. The storyboard is a versioned JSON/JS object (`STORYBOARD` in `video-kit/template/timeline.html`), independent of any AI provider, so that one scene can be changed without redoing the others.

Several concepts only when that brings a real value (problem then solution, product demonstration, a benefit, an application walk-through, a cinematic presentation, an offer and a call to action); say which one is chosen and why.

## 4. Formats

| Format | Size | For |
|---|---|---|
| 9:16 | 1080 × 1920 | TikTok, Reels, Shorts |
| 16:9 | 1920 × 1080 | YouTube, websites, presentations |
| 1:1 | 1080 × 1080 | square posts |
| 4:5 | 1080 × 1350 | vertical social ads |

Durations: 15, 30, 45, 60 s (a first one of 20 to 30 s). MP4, H.264, AAC. The layout is recomposed for each format (the template does it): the text is repositioned and kept out of the zones the platforms cover (the top tenth and the bottom sixth of a vertical format).

## 5. The check, mandatory, separate from the making

`video-kit check` reads the finished file and reports three levels: **blocking** (do not deliver: unreadable file, wrong size or duration, no audio track, silent audio, black screen), **warning** (codec, frame rate, size, clipping, a frozen picture), **suggestion** (a quiet level). Creative suggestions are never presented as certain technical errors. After the script check, look at frames of the video yourself (a contact sheet) for what a script cannot see: text cut off, a deformed logo, a medium unrelated to the product, a wrong transition. A blocking error stops the delivery until it is fixed or the user accepts it explicitly.

## 6. Making it

```
node video-kit/src/cli.js render page.html frames/ --seconds=25 --width=1080 --height=1920
python3 video-kit/src/audio-bed.py bed.wav 25 0 3 7 …        # a placeholder bed with a tick at each scene start; use a licensed track when there is one
node video-kit/src/cli.js assemble frames/ out-9x16.mp4 --audio=bed.wav
node video-kit/src/cli.js check out-9x16.mp4 --seconds=25 --width=1080 --height=1920
```

The page defines `window.render(t)`, a pure function of t. Start from `video-kit/template/timeline.html`. The render is deterministic and can be redone for part of the frames when only one scene changed. ffmpeg and ffprobe are looked for in `FFMPEG` / `FFPROBE`, then in the PATH.

## 7. Revisions in natural language

"Make the start punchier", "keep the editing but change the music", "replace only scene three", "cut it to fifteen seconds", "a more premium version", "adapt it vertically": translate the request into precise changes of the storyboard, keep what is not concerned, redo only the frames of the scenes that changed, and check again. Each version is kept with the storyboard it came from.

## 8. Where it goes

In the creation's repository: the video as a file of the GitHub release `videos` (one file per video, named `name-9x16.mp4`, `name-16x9.mp4`, …), and under `aiwa-videos/` its storyboard, the media list with their sources and rights, and the check report. The widget's "Vidéos" button lists the release's files (a public repository: Aiwa never asks for a GitHub token) and asks the session for a new one.

## 9. Limits to state

A first version does not do AI video generation for each scene, a good synthetic voice, or professional music: the bed is a placeholder, and a voice needs a tool that was checked for licence and quality. Rendering and the cost in time and space are measured, not promised: say what the render took. Rights to the media remain the user's: say which ones were assumed.
