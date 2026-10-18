#!/usr/bin/env python3
"""hdri.py <file.hdr> <out-dir> <name>: a Radiance HDR panorama becomes (1) an RGBE picture, 1024x512, that the shader of v32 decodes (light is kept, not clipped to 8 bits)
and (2) its anchors: the directions of its brightest points (a lamp, a window, the sun), which the camera tracks and marks on screen. No library besides numpy and ffmpeg."""
import sys, os, json, subprocess, numpy as np

def read_hdr(path):
    f = open(path, 'rb'); w = h = 0
    while True:
        l = f.readline()
        if l.startswith(b'-Y') or l.startswith(b'+Y'):
            p = l.split(); h = int(p[1]); w = int(p[3]); break
    data = np.zeros((h, w, 4), np.uint8)
    for y in range(h):
        hd = f.read(4)
        if hd[0] != 2 or hd[1] != 2 or (hd[2] << 8 | hd[3]) != w:     # flat, not run-length encoded
            raw = hd + f.read(4 * (w - 1)); data[y] = np.frombuffer(raw, np.uint8).reshape(w, 4); continue
        for c in range(4):
            x = 0; line = np.zeros(w, np.uint8)
            while x < w:
                n = f.read(1)[0]
                if n > 128: n -= 128; line[x:x + n] = f.read(1)[0]
                else: line[x:x + n] = np.frombuffer(f.read(n), np.uint8)
                x += n
            data[y, :, c] = line
    return data

def to_float(rgbe):
    e = rgbe[..., 3].astype(np.int32)
    s = np.where(e > 0, np.ldexp(1.0, e - 136), 0.0)
    return rgbe[..., :3].astype(np.float32) * s[..., None].astype(np.float32)

def to_rgbe(rgb):
    m = rgb.max(axis=2); e = np.where(m > 1e-32, np.floor(np.log2(np.maximum(m, 1e-32))) + 1, 0)
    sc = np.where(e != 0, np.ldexp(1.0, (-e).astype(np.int32)) * 256.0, 0.0)
    out = np.zeros(rgb.shape[:2] + (4,), np.uint8)
    out[..., :3] = np.clip(rgb * sc[..., None], 0, 255).astype(np.uint8); out[..., 3] = np.where(e != 0, e + 128, 0).astype(np.uint8)
    return out

def down(a, w, h):                                         # area average, in light (float), never in bytes
    H, W = a.shape[:2]; fy, fx = H // h, W // w
    a = a[:fy * h, :fx * w].reshape(h, fy, w, fx, -1).mean(axis=(1, 3)); return a

src, out, name = sys.argv[1:4]
rgb = to_float(read_hdr(src)); H, W = rgb.shape[:2]
small = down(rgb, 512, 256)                                # the light for the reflections: soft is enough, and it keeps the page small
rg = to_rgbe(small)
rawf = f'{out}/{name}.rgba'; open(rawf, 'wb').write(rg.tobytes())
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgba', '-s', f'{small.shape[1]}x{small.shape[0]}', '-i', rawf, f'{out}/{name}.png'], check=True)
import os; os.remove(rawf)
# the place as the eye sees it, behind the forms: the light of the file, brought to a common level, through a filmic curve; 2048x1024
lum0 = down(rgb, 256, 128).dot(np.array([.2126, .7152, .0722], np.float32)); key = float(np.exp(np.log(np.maximum(lum0, 1e-4)).mean()))
expo = min(0.30 / max(key, 1e-3), 1.7)      # a night stays a night: the lift is capped
x = rgb * expo; c = np.clip((x * (2.51 * x + .03)) / (x * (2.43 * x + .59) + .14), 0, 1) ** (1 / 2.2)
bgw = 2048 if W >= 2048 else W; bgh = bgw // 2
if W != bgw: c = down(c, bgw, bgh)
open(f'{out}/{name}.rgb', 'wb').write((c * 255 + .5).astype(np.uint8).tobytes())
subprocess.run(['ffmpeg', '-v', 'error', '-y', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{c.shape[1]}x{c.shape[0]}', '-i', f'{out}/{name}.rgb', '-q:v', '3', f'{out}/{name}.jpg'], check=True); os.remove(f'{out}/{name}.rgb')
# anchors: the brightest points, far from each other
lum = down(rgb, 256, 128).dot(np.array([.2126, .7152, .0722], np.float32)); mean = float(lum.mean()) + 1e-6
anch = []; l2 = lum.copy(); hh, ww = l2.shape
for _ in range(5):
    y, x = np.unravel_index(np.argmax(l2), l2.shape)
    if l2[y, x] < mean * 3: break
    yaw = (x + .5) / ww * 2 * np.pi - np.pi; pitch = np.pi / 2 - (y + .5) / hh * np.pi
    anch.append({'yaw': round(float(yaw), 4), 'pitch': round(float(pitch), 4), 'lum': round(float(l2[y, x] / mean), 1)})
    yy, xx = np.ogrid[:hh, :ww]; dx = np.minimum(abs(xx - x), ww - abs(xx - x)); l2[(dx * dx / 2.0 + (yy - y) ** 2) < 20 ** 2 / 2] = 0
# features: the points that a tracker would hold on to (a lamp, a corner, a window edge): high local contrast, spread over the sphere
g = down(rgb, 512, 256).dot(np.array([.2126, .7152, .0722], np.float32)); g = np.log1p(g * 4)
blur = (g + np.roll(g, 1, 0) + np.roll(g, -1, 0) + np.roll(g, 1, 1) + np.roll(g, -1, 1)) / 5
ct = np.abs(g - blur); ct[:20] = 0; ct[-20:] = 0                                      # not the poles
feat = []; hh2, ww2 = ct.shape
for _ in range(70):
    y, x = np.unravel_index(np.argmax(ct), ct.shape)
    if ct[y, x] <= 0: break
    feat.append({'yaw': round(float((x + .5) / ww2 * 2 * np.pi - np.pi), 4), 'pitch': round(float(np.pi / 2 - (y + .5) / hh2 * np.pi), 4), 'c': round(float(ct[y, x]), 3)})
    yy, xx = np.ogrid[:hh2, :ww2]; dx = np.minimum(abs(xx - x), ww2 - abs(xx - x)); ct[(dx * dx + (yy - y) ** 2) < 14 ** 2] = 0
exp = float(np.exp(np.log(np.maximum(lum, 1e-4)).mean()))  # the log-average luminance: how bright the place is overall
json.dump({'name': name, 'features': feat, 'anchors': anch, 'key': round(expo, 3)}, open(f'{out}/{name}.json', 'w'))
print(name, 'anchors', len(anch), 'key', round(exp, 4))
