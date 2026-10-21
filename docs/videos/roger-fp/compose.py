"""Roger in the first person: the picture, from the pieces (run from the repository root, after capture.cjs and scene.py; see make.sh).
  python3 docs/videos/roger-fp/compose.py --r /tmp/r2 --film <the 16:9 film that plays on the TV> --audio <the mixed sound> --out roger_fp.mp4
Stretches (seconds, on the final timeline): the 3D pieces where the eyes move (12 a second, interpolated to 24, except the still room at the start: frames repeated, nothing is invented there), the plates (the room with the TV
dark, one still per lens) where the eyes rest on the TV: the TV laid on the plate with a glow. Joints are short crossfades. Over everything: the eyes never stand still, they drift, and move more while Roger speaks
(a crop of an enlarged picture, so that the motion is smooth to less than a pixel), then the captions layer."""
import argparse, json, os, subprocess, sys

ap = argparse.ArgumentParser()
ap.add_argument('--r', default='/tmp/r2'); ap.add_argument('--film', required=True); ap.add_argument('--audio', required=True); ap.add_argument('--out', required=True)
ap.add_argument('--timeline', default=os.path.join(os.path.dirname(os.path.abspath(__file__)), 'timeline.json'))
ap.add_argument('--keep', action='store_true', help='keep the pieces (to re-run only the last step)')
a = ap.parse_args()
R = a.r; W = os.path.join(R, 'pc2'); os.makedirs(W, exist_ok=True)
FPS = 24; D = 5      # a crossfade of 5 frames (0.21 s)
T = json.load(open(a.timeline))
TOTAL = round(T['EV']['total'] * FPS)

# (kind, from, to) in frames of the final timeline. kind: s = 3D frames, p56 / p90 = plate at that lens, film = the film on the 90 mm plate
SEG = [('s', 0, 27.0), ('p56', 27.0, 33.0), ('s', 33.0, 38.8), ('p56', 38.8, 43.8), ('s', 43.8, 60.4), ('film', 60.4, 103.2), ('s', 103.2, 105.5), ('p56', 105.5, 110.2), ('s', 110.2, 121.2), ('p56', 121.2, T['EV']['total'])]
SEG = [(k, round(x * 12) * 2, round(y * 12) * 2) for k, x, y in SEG]      # on even frames: the 3D pieces are 12 a second
CORN = {'p56': ('plate400/00400.png', 612, 345, 697, 392), 'p90': ('plate900/00900.png', 400, 226, 1120, 630)}
FILM_AT = 58.2      # when the film starts on the TV


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit('ffmpeg failed: ' + ' '.join(cmd)[:300] + '\n' + r.stderr[-1500:])


def piece(i, kind, f0, f1, last):
    """The file of segment i: frames f0..f1 (+ D frames beyond when a crossfade follows)."""
    n = f1 - f0 + (0 if last else D)
    dur = n / FPS
    out = os.path.join(W, f'seg{i:02d}.mp4')
    x264 = ['-c:v', 'libx264', '-crf', '14', '-pix_fmt', 'yuv420p', '-r', str(FPS), '-t', f'{dur:.5f}']
    if kind == 's':
        # 3D frames at 12 a second; frames 0..40 (the room, still) are repeated, the rest interpolated
        g0 = f0 // 2
        if f0 == 0:
            run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-start_number', '0', '-i', f'{R}/s/%05d.png', '-vf', 'trim=end_frame=41,scale=1920:1080:flags=lanczos,fps=24,format=yuv420p', '-t', f'{82 / FPS:.5f}'] + x264[:-2] + [os.path.join(W, 'still.mp4')])
            run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-start_number', '41', '-i', f'{R}/s/%05d.png', '-vf', 'scale=1920:1080:flags=lanczos,minterpolate=fps=24:mi_mode=mci:mc_mode=aobmc:vsbmc=1,format=yuv420p', '-t', f'{(n - 82) / FPS:.5f}'] + x264[:-2] + [os.path.join(W, 'moving.mp4')])
            open(os.path.join(W, 'l.txt'), 'w').write(f"file '{W}/still.mp4'\nfile '{W}/moving.mp4'\n")
            run(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(W, 'l.txt'), '-c:v', 'libx264', '-crf', '14', '-pix_fmt', 'yuv420p', '-t', f'{dur:.5f}', out])
        else:
            run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-start_number', str(g0), '-i', f'{R}/s/%05d.png', '-vf', 'scale=1920:1080:flags=lanczos,minterpolate=fps=24:mi_mode=mci:mc_mode=aobmc:vsbmc=1,format=yuv420p'] + x264 + [out])
        return out
    lens = 'p90' if kind == 'film' else kind
    img, x, y, w, h = CORN[lens]
    gw, gh = int(w * 1.3) // 2 * 2, int(h * 1.3) // 2 * 2
    px, py = int((x + w / 2) - gw / 2), int((y + h / 2) - gh / 2)
    if kind == 'film':
        src = ['-ss', f'{f0 / FPS - FILM_AT:.4f}', '-i', a.film]
    else:
        src = ['-framerate', '24', '-start_number', str(f0), '-i', f'{R}/tv24/%05d.png']
    # the film is slowly pushed in and out (a dolly of 3 %): the eyes do not stand still while it plays
    push = f",scale=w='trunc(1920*(1+0.03*sin(PI*t/{dur:.3f}))/2)*2':h='trunc(1080*(1+0.03*sin(PI*t/{dur:.3f}))/2)*2':eval=frame,crop=1920:1080" if kind == 'film' else ''
    run(['ffmpeg', '-loglevel', 'error', '-y', '-loop', '1', '-framerate', '24', '-t', f'{dur:.5f}', '-i', f'{R}/{img}'] + src + ['-filter_complex', f"""
[0:v]scale=1920:1080:flags=lanczos,unsharp=5:5:0.3,format=gbrp[bg];
[1:v]fps=24,split=2[v1][v2];
[v1]scale={w}:{h}:flags=lanczos,eq=contrast=1.04:saturation=1.05,format=gbrp[tv];
[v2]scale={gw}:{gh},gblur=sigma=45,pad=1920:1080:{px}:{py}:black,format=gbrp[glow];
[bg][glow]blend=all_mode=screen:all_opacity=0.5[bg2];
[bg2][tv]overlay={x}:{y},format=yuv420p{push}[out]""", '-map', '[out]'] + x264 + [out])
    return out


files = []
for i, (k, f0, f1) in enumerate(SEG):
    last = i == len(SEG) - 1
    print('piece', i, k, f0 / FPS, f1 / FPS, flush=True)
    files.append(piece(i, k, f0, f1, last))

# the joints: main_i = [D .. len) of piece i (the first one from 0), the crossfade = the tail of i over the head of i + 1
G, names = [], []
for i, (k, f0, f1) in enumerate(SEG):
    ln = f1 - f0; last = i == len(SEG) - 1
    s0 = 0 if i == 0 else D
    G.append(f"[{i}:v]trim=start_frame={s0}:end_frame={ln},setpts=PTS-STARTPTS,fps=24,settb=1/24,setsar=1,format=yuv420p[m{i}];")
    names.append(f'[m{i}]')
    if not last:
        G.append(f"[{i}:v]trim=start_frame={ln}:end_frame={ln + D},setpts=PTS-STARTPTS,fps=24,settb=1/24,setsar=1,format=yuv420p[a{i}];")
        G.append(f"[{i + 1}:v]trim=start_frame=0:end_frame={D},setpts=PTS-STARTPTS,fps=24,settb=1/24,setsar=1,format=yuv420p[c{i}];")
        G.append(f"[a{i}][c{i}]xfade=transition=fade:duration={(D - 1) / FPS:.4f}:offset=0[x{i}];")
        names.append(f'[x{i}]')
# order: m0 x0 m1 x1 ... but the head of piece i + 1 is consumed by x_i, so m_{i+1} starts after it (s0 = D)
gate = '+'.join(f"clip((t-{s})/0.25,0,1)*clip(({e}-t)/0.5,0,1)" for k, (s, e) in T['T'].items() if k.startswith('R'))
g = f"min(1,{gate})"
X = f"96+(9+18*{g})*sin(2*PI*t*0.17)+(3+9*{g})*sin(2*PI*t*0.61+1)"
Y = f"54+(5+8*{g})*sin(2*PI*t*0.23+2)+(2+6*{g})*sin(2*PI*t*0.97)"
n = len(files)
G.append(''.join(names) + f"concat=n={len(names)}:v=1:a=0[base];")
G.append(f"[base]scale=4032:2268:flags=bicubic,crop=3840:2160:'2*({X})':'2*({Y})',scale=1920:1080:flags=lanczos,format=yuv420p[sw];")
G.append(f"[sw][{n}:v]overlay=0:0:format=auto,format=yuv420p[vout]")
cmd = ['ffmpeg', '-loglevel', 'error', '-y']
for f in files:
    cmd += ['-i', f]
cmd += ['-framerate', '24', '-i', f'{R}/caps/%05d.png', '-i', a.audio, '-filter_complex', ''.join(G), '-map', '[vout]', '-map', f'{n + 1}:a', '-t', f'{TOTAL / FPS:.4f}',
        '-c:v', 'libx264', '-crf', '16', '-preset', 'medium', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', a.out]
run(cmd)
print('done', a.out)
