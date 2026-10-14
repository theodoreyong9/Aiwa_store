#!/usr/bin/env python3
"""Builds the maximum film of docs/video-v10 from pieces laid on the bars of the track (104 bpm: a bar is 2.3077 s). Three kinds of piece:
  N    the new scenes of video.html (?g=<first bar>-<end bar>), rendered by the cinematic engine
  v9   one scene of the global film v9 (video-vision/pieces.html?shot=<i>), rendered by the same engine, with its own timing
  cut  a 3D scene of v9 (phone, planets, glasses: Blender renders that are not in the repository) cut from docs/aiwa-global-v9-16x9.mp4 (picture only)
All the frames are put end to end (frames/00001.jpg ...), then `video-kit assemble` mixes the voice and the track. Usage: build.py WORKDIR [piece-index ...]"""
import json, os, shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BAR, FPS = 240 / 104, 30
f = lambda b: round(b * BAR * FPS)
V9 = ROOT / 'docs/aiwa-global-v9-16x9.mp4'
TAIL = 3.6
PIECES = [  # first bar, end bar, kind, parameters
    (0, 6, 'N', {}),
    (6, 8, 'cut', {'s': 0.0, 'e': 4.65}),                    # v9: the phone locked, the agent listening
    (8, 10, 'cut', {'s': 5.1, 'e': 8.85}),                   # v9: « Tu choisis ton mot d'éveil » on a 3D phone
    (10, 21, 'N', {}),
    (21, 23, 'v9', {'shot': 7}),                             # v9: a file, a campaign
    (23, 26, 'cut', {'s': 51.1, 'e': 57.117}),               # v9: « Tu valides. Une par une. » on a 3D phone
    (26, 38, 'N', {}),
    (38, 40, 'v9', {'shot': 10}),                            # v9: a store without permission, ranked by production
    (40, 43, 'v9', {'shot': 11}),                            # v9: the production of AIWA (restart, growing difficulty)
    (43, 45, 'N', {}),
    (45, 48, 'v9', {'shot': 13}),                            # v9: pay in a session
    (48, 50, 'v9', {'shot': 14}),                            # v9: pay offline
    (50, 54, 'v9', {'shot': 18}),                            # v9: the guarantees
    (54, 56, 'N', {}),
    (56, 59, 'cut', {'s': 92.6, 'e': 99.73}),                # v9: Earth and Mars (3D planets)
    (59, 62, 'cut', {'s': 126.5, 'e': 133.03}),              # v9: the glasses (3D)
    (62, 65, 'v9', {'shot': 22}),                            # v9: you look at someone (an illustration of the vision)
    (65, 71, 'N', {'tail': TAIL}),
]


def run(*a, **k):
    subprocess.run(a, check=True, **k)


def render(url, out, seconds):
    shutil.rmtree(out, ignore_errors=True)
    run('node', str(ROOT / 'video-kit/src/cli.js'), 'render', url, str(out), f'--seconds={seconds:.3f}', '--fps=30', '--width=1920', '--height=1080', stdout=subprocess.DEVNULL)


def main():
    work = Path(sys.argv[1]); work.mkdir(parents=True, exist_ok=True)
    only = {int(x) for x in sys.argv[2:]}
    for i, (b0, b1, kind, p) in enumerate(PIECES):
        if only and i not in only:
            continue
        n = f(b1) - f(b0) + (round(p.get('tail', 0) * FPS) if kind == 'N' else 0)
        d = work / f'p{i:02d}'
        secs = n / FPS
        if kind == 'N':
            render(f"{ROOT}/docs/video-v10/video.html?g={b0}-{b1}", d, secs + 0.1)
        elif kind == 'v9':
            render(f"{ROOT}/docs/video-vision/pieces.html?shot={p['shot']}&secs={secs + 0.1:.3f}", d, secs + 0.1)
        else:
            shutil.rmtree(d, ignore_errors=True); d.mkdir()
            run('ffmpeg', '-loglevel', 'error', '-y', '-ss', f"{p['s']}", '-t', f"{p['e'] - p['s']}", '-i', str(V9),
                '-vf', f'scale=1920:1080,fps=30,tpad=stop_mode=clone:stop_duration=3', '-frames:v', str(n), '-q:v', '2', '-start_number', '0', str(d / '%05d.jpg'))
        have = len(list(d.glob('*.jpg')))
        print(f'piece {i} bars {b0}-{b1} {kind}: need {n} frames, have {have}', flush=True)
        if have < n:
            print('  !! short', flush=True)
    if only:
        return
    # end to end
    out = work / 'all'; shutil.rmtree(out, ignore_errors=True); out.mkdir()
    k = 0
    for i, (b0, b1, kind, p) in enumerate(PIECES):
        n = f(b1) - f(b0) + (round(p.get('tail', 0) * FPS) if kind == 'N' else 0)
        names = sorted((work / f'p{i:02d}').glob('*.jpg'))
        for j in range(n):
            src = names[min(j, len(names) - 1)]
            os.link(src, out / f'{k + 1:05d}.jpg') if False else shutil.copy(src, out / f'{k + 1:05d}.jpg')
            k += 1
    print('frames', k, k / FPS, flush=True)


main()
