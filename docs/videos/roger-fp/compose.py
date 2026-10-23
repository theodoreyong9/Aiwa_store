"""Roger in the first person: the picture, from the pieces (run from the repository root, after timeline.py, capture.cjs and scene.py; see make.sh).
  python3 docs/videos/roger-fp/compose.py --r /tmp/r3 --film F.mp4 --cv C.mp4 --cand A.mp4 --audio sound.wav --out roger.mp4
Stretches (seg.json, written by timeline.py), in seconds of the final timeline:
  s     3D frames (Blender, 12 a second, interpolated to 24) where the eyes move or the phone is in the hand; the first 3.4 s (the still room) are repeated, not interpolated
  p56   the room at 56 mm with the TV dark (one still) and the page of the TV laid on it, with its glow
  p90   the same at 90 mm (the close-up)
  v90   the close-up with a VIDEO on the TV: the presentation of the CV, the Aiwa film, the application video
  pph   the phone on the table (one still, its screen dark), the page of the phone warped onto the four corners of its screen
Joints are short crossfades. Over everything the eyes never stand still: they drift, and move more while Roger speaks (a crop of an enlarged picture, so that the motion is smooth to less than a pixel); then the captions."""
import argparse, json, os, subprocess, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ap = argparse.ArgumentParser()
ap.add_argument('--r', default='/tmp/r3'); ap.add_argument('--film', required=True); ap.add_argument('--cv', required=True); ap.add_argument('--cand', required=True)
ap.add_argument('--audio', required=True); ap.add_argument('--out', required=True)
a = ap.parse_args()
R = a.r; W = os.path.join(R, 'pc2'); os.makedirs(W, exist_ok=True)
FPS = 24; D = 5      # a crossfade of 5 frames (0.21 s)
T = json.load(open(os.path.join(HERE, 'timeline.json')))
EV = T['EV']
TOTAL = round(EV['total'] * FPS)
SEG = [dict(s, f0=round(s['a'] * 12) * 2, f1=round(s['b'] * 12) * 2) for s in json.load(open(os.path.join(HERE, 'seg.json')))]      # on even frames: the 3D pieces are 12 a second
SEG[-1]['f1'] = TOTAL
VIDEO = {'cv': (a.cv, EV['vstart']['cv'], False), 'film': (a.film, EV['vstart']['film'], True), 'cand': (a.cand, EV['vstart']['cand'], False)}      # file, when it starts on the timeline, pushed in and out
PLATE = {'p56': ('plate400/00400.png', 612, 345, 697, 392), 'p90': ('plate900/00900.png', 400, 226, 1120, 630)}      # image, then where the screen of the TV falls (x, y, w, h)


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.exit('ffmpeg failed: ' + ' '.join(cmd)[:400] + '\n' + r.stderr[-1500:])


def piece(i, sg, last):
    """The file of segment i: frames f0..f1 (+ D frames beyond when a crossfade follows)."""
    k, f0, f1 = sg['k'], sg['f0'], sg['f1']
    n = f1 - f0 + (0 if last else D)
    dur = n / FPS
    out = os.path.join(W, f'seg{i:02d}.mp4')
    if os.path.exists(out + '.ok'):      # restartable: a piece that was finished (its .ok marker is written after it) is not made again
        return out
    x264 = ['-c:v', 'libx264', '-crf', '14', '-pix_fmt', 'yuv420p', '-r', str(FPS), '-t', f'{dur:.5f}']
    if k == 's':
        g0 = f0 // 2
        interp = 'scale=1920:1080:flags=lanczos,minterpolate=fps=24:mi_mode=mci:mc_mode=aobmc:vsbmc=1,format=yuv420p'
        if f0 == 0:      # frames 0..40 (the room, still) are repeated, the rest interpolated
            run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-start_number', '0', '-i', f'{R}/s/%05d.png', '-vf', 'trim=end_frame=41,scale=1920:1080:flags=lanczos,fps=24,format=yuv420p', '-t', f'{82 / FPS:.5f}'] + x264[:-2] + [os.path.join(W, 'still.mp4')])
            run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-start_number', '41', '-i', f'{R}/s/%05d.png', '-vf', interp, '-t', f'{(n - 82) / FPS:.5f}'] + x264[:-2] + [os.path.join(W, 'moving.mp4')])
            open(os.path.join(W, 'l.txt'), 'w').write(f"file '{W}/still.mp4'\nfile '{W}/moving.mp4'\n")
            run(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', os.path.join(W, 'l.txt'), '-c:v', 'libx264', '-crf', '14', '-pix_fmt', 'yuv420p', '-t', f'{dur:.5f}', out])
        else:
            run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '12', '-start_number', str(g0), '-i', f'{R}/s/%05d.png', '-vf', interp] + x264 + [out])
        return out
    if k == 'pph':
        # the screen of the phone: its page, stretched over the whole picture, is warped onto the four corners (a mask does the same with a white picture), then laid on the plate
        c = json.load(open(f'{R}/plate68/phone-corners.json'))
        px = [(round(x * 1920, 2), round(y * 1080, 2)) for x, y in c]       # top left, top right, bottom right, bottom left
        persp = f"perspective=x0={px[0][0]}:y0={px[0][1]}:x1={px[1][0]}:y1={px[1][1]}:x2={px[3][0]}:y2={px[3][1]}:x3={px[2][0]}:y3={px[2][1]}:sense=destination:interpolation=cubic"
        run(['ffmpeg', '-loglevel', 'error', '-y', '-loop', '1', '-framerate', '24', '-t', f'{dur:.5f}', '-i', f'{R}/plate68/00000.png', '-framerate', '24', '-start_number', str(f0), '-i', f'{R}/ph24/%05d.png',
             '-f', 'lavfi', '-i', f'color=c=white:s=1920x1080:r=24:d={dur:.5f}', '-filter_complex',
             f"[0:v]scale=1920:1080:flags=lanczos,unsharp=5:5:0.3,format=gbrp[bg];[1:v]scale=1920:1080,{persp},format=gbrp[sc];[2:v]{persp},format=gray[mk];"
             f"[sc]format=gbrap[sc2];[sc2][mk]alphamerge[scm];[bg][scm]overlay=0:0:format=gbrp,format=yuv420p[out]", '-map', '[out]'] + x264 + [out])
        return out
    lens = 'p90' if k in ('p90', 'v90') else 'p56'
    img, x, y, w, h = PLATE[lens]
    gw, gh = int(w * 1.3) // 2 * 2, int(h * 1.3) // 2 * 2
    px, py = int((x + w / 2) - gw / 2), int((y + h / 2) - gh / 2)
    push = ''
    if k == 'v90':
        file, start, pushed = VIDEO[sg['v']]
        src = ['-ss', f'{max(0.0, f0 / FPS - start):.4f}', '-i', file]
        if pushed:      # the film is slowly pushed in and out (a dolly of 3 %): the eyes do not stand still while it plays
            push = f",scale=w='trunc(1920*(1+0.03*sin(PI*t/{dur:.3f}))/2)*2':h='trunc(1080*(1+0.03*sin(PI*t/{dur:.3f}))/2)*2':eval=frame,crop=1920:1080"
    else:
        src = ['-framerate', '24', '-start_number', str(f0), '-i', f'{R}/tv24/%05d.png']
    run(['ffmpeg', '-loglevel', 'error', '-y', '-loop', '1', '-framerate', '24', '-t', f'{dur:.5f}', '-i', f'{R}/{img}'] + src + ['-filter_complex', f"""
[0:v]scale=1920:1080:flags=lanczos,unsharp=5:5:0.3,format=gbrp[bg];
[1:v]fps=24,split=2[v1][v2];
[v1]scale={w}:{h}:flags=lanczos,eq=contrast=1.04:saturation=1.05,format=gbrp[tv];
[v2]scale={gw}:{gh},gblur=sigma=45,pad=1920:1080:{px}:{py}:black,format=gbrp[glow];
[bg][glow]blend=all_mode=screen:all_opacity=0.5[bg2];
[bg2][tv]overlay={x}:{y},format=yuv420p{push}[out]""", '-map', '[out]'] + x264 + [out])
    return out


files = []
for i, sg in enumerate(SEG):
    last = i == len(SEG) - 1
    print('piece', i, sg['k'], sg['a'], sg['b'], flush=True)
    files.append(piece(i, sg, last))
    open(files[-1] + '.ok', 'w').write('1')

# the joints: main_i = [D .. len) of piece i (the first one from 0), the crossfade = the tail of i over the head of i + 1
G, names = [], []
for i, sg in enumerate(SEG):
    ln = sg['f1'] - sg['f0']; last = i == len(SEG) - 1
    s0 = 0 if i == 0 else D
    G.append(f"[{i}:v]trim=start_frame={s0}:end_frame={ln},setpts=PTS-STARTPTS,fps=24,settb=1/24,setsar=1,format=yuv420p[m{i}];")
    names.append(f'[m{i}]')
    if not last:
        G.append(f"[{i}:v]trim=start_frame={ln}:end_frame={ln + D},setpts=PTS-STARTPTS,fps=24,settb=1/24,setsar=1,format=yuv420p[a{i}];")
        G.append(f"[{i + 1}:v]trim=start_frame=0:end_frame={D},setpts=PTS-STARTPTS,fps=24,settb=1/24,setsar=1,format=yuv420p[c{i}];")
        G.append(f"[a{i}][c{i}]xfade=transition=fade:duration={(D - 1) / FPS:.4f}:offset=0[x{i}];")
        names.append(f'[x{i}]')
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
