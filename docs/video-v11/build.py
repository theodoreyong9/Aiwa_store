#!/usr/bin/env python3
"""Builds the film v11 on the bars of the track (104 bpm: a bar is 2.3077 s). Every piece is a run of bars:
  2D       the shots of video.html (?g=<first bar>-<end bar>), rendered by the cinematic engine
  over     2D shots with a 3D render laid OVER them (the phones, the planets: transparent PNG made by shots/*.py, 12 images a second, brought to 30)
  under    a 3D render (the glasses, opaque) with the words of the page (?t=1, transparent) laid over it
All the frames end to end in WORKDIR/all, then `video-kit assemble` mixes the voice and the track.  Usage: build.py WORKDIR 3DDIR [piece-index ...]"""
import shutil, subprocess, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BAR, FPS = 240 / 104, 30
f = lambda b: round(b * BAR * FPS)
TAIL = 3.6
PIECES = [  # first bar, end bar, kind, the 3D folder (in 3DDIR)
    (0, 6, '2D', None),
    (6, 8, 'over', 'r_off'),
    (8, 12, 'over', 'r_widget'),
    (12, 26, '2D', None),
    (26, 51, '2D', None),
    (51, 54, 'over', 'r_planets'),
    (54, 57, 'under', 'r_glasses'),
    (57, 63, '2D', None),
]


def run(*a, **k):
    subprocess.run(a, check=True, **k)


def render(url, out, seconds, transparent=False):
    shutil.rmtree(out, ignore_errors=True)
    run('node', str(ROOT / 'video-kit/src/cli.js'), 'render', url, str(out), f'--seconds={seconds:.3f}', '--fps=30', '--width=1920', '--height=1080', *(['--transparent', '1'] if transparent else []), stdout=subprocess.DEVNULL)


def main():
    work, d3 = Path(sys.argv[1]), Path(sys.argv[2]); work.mkdir(parents=True, exist_ok=True)
    only = {int(x) for x in sys.argv[3:] if x.isdigit()}
    concat_only = '--concat' in sys.argv
    for i, (b0, b1, kind, r3) in enumerate(PIECES):
        if concat_only or (only and i not in only):
            continue
        tail = round(TAIL * FPS) if b1 == 63 else 0
        n = f(b1) - f(b0) + tail
        secs = n / FPS + 0.1
        base = work / f'p{i:02d}'
        if kind == 'under':
            render(f'{ROOT}/docs/video-v11/video.html?g={b0}-{b1}&t=1', work / f'p{i:02d}t', secs, transparent=True)
            out = base; shutil.rmtree(out, ignore_errors=True); out.mkdir()
            run('ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-i', str(d3 / r3 / '%05d.png'), '-framerate', '30', '-i', str(work / f'p{i:02d}t' / '%05d.png'),
                '-filter_complex', '[0:v]scale=1920:1080:flags=lanczos,minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1[b];[b][1:v]overlay=format=auto,format=yuvj420p', '-frames:v', str(n), '-q:v', '2', str(out / '%05d.jpg'))
        else:
            render(f'{ROOT}/docs/video-v11/video.html?g={b0}-{b1}', base if kind == '2D' else work / f'p{i:02d}b', secs)
            if kind == 'over':
                out = base; shutil.rmtree(out, ignore_errors=True); out.mkdir()
                run('ffmpeg', '-loglevel', 'error', '-y', '-framerate', '30', '-i', str(work / f'p{i:02d}b' / '%05d.jpg'), '-framerate', '12', '-i', str(d3 / r3 / '%05d.png'),
                    '-filter_complex', '[1:v]scale=1920:1080:flags=lanczos,format=rgba,split[ca][cb];[ca]format=rgb24,minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1[c];[cb]alphaextract,minterpolate=fps=30:mi_mode=mci:mc_mode=aobmc:vsbmc=1[m];[c][m]alphamerge[o];[0:v][o]overlay=0:0:format=auto,format=yuvj420p', '-frames:v', str(n), '-q:v', '2', str(out / '%05d.jpg'))
        have = len(list(base.glob('*.jpg')))
        print(f'piece {i} bars {b0}-{b1} {kind}: need {n} frames, have {have}', flush=True)
    if only and not concat_only:
        return
    out = work / 'all'; shutil.rmtree(out, ignore_errors=True); out.mkdir()
    k = 0
    for i, (b0, b1, kind, r3) in enumerate(PIECES):
        n = f(b1) - f(b0) + (round(TAIL * FPS) if b1 == 63 else 0)
        names = sorted((work / f'p{i:02d}').glob('*.jpg'))
        for j in range(n):
            shutil.copy(names[min(j, len(names) - 1)], out / f'{k + 1:05d}.jpg')
            k += 1
    print('frames', k, k / FPS, flush=True)


main()
