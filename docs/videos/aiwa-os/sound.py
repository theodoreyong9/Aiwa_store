"""The sound under the voice of the film « Aiwa », all synthesised here (numpy: no sample, no licence to carry): a slow pad on four chords with a sub, a whoosh and a soft hit at the start of each part, ticks while the terminal types, pings when something is signed or done, a riser into the last part and a low boom at the end. Times come from the film's own schedule (video.html).
  python3 docs/videos/aiwa-os/sound.py docs/videos/aiwa-os/video.html out.wav"""
import json, re, sys, wave
import numpy as np

SR = 44100
src = open(sys.argv[1], encoding='utf-8').read()
SCHED = json.loads(re.search(r'const SCHED=(\[.*?\]),TOTAL=', src, re.S).group(1)); TOTAL = float(re.search(r'TOTAL=([0-9.]+)', src).group(1))
K = {s['key']: s for s in SCHED}
out = np.zeros((int(TOTAL * SR) + SR, 2)); rng = np.random.default_rng(5)

def put(sig, t, pan=0.0, gain=1.0):
    i = int(t * SR)
    if i < 0 or i >= len(out): return
    n = min(len(sig), len(out) - i); l, r = (1 - max(0, pan)) * gain, (1 + min(0, pan)) * gain
    out[i:i + n, 0] += sig[:n] * l; out[i:i + n, 1] += sig[:n] * r

def lowpass_sweep(x, f0, f1):
    y = np.zeros_like(x); state = 0.0; a = 1 - np.exp(-2 * np.pi * np.linspace(f0, f1, len(x)) / SR)
    for i in range(len(x)):
        state += a[i] * (x[i] - state); y[i] = state
    return y

def whoosh(dur, up=True, f=(300, 5200), gain=0.5):
    n = int(dur * SR); y = lowpass_sweep(rng.standard_normal(n), *(f if up else f[::-1])); y = y - lowpass_sweep(y, 80, 80)
    t = np.arange(n) / SR / dur
    return y / (np.max(np.abs(y)) + 1e-9) * np.sin(np.pi * np.clip(t, 0, 1)) ** (2.2 if up else 1.6) * gain

def ping(freq=1175, dur=1.6, gain=0.18):
    n = int(dur * SR); t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)) * np.exp(-t / 0.55) * np.minimum(1, t / 0.01) * gain

def tick(gain=0.1):
    n = int(0.04 * SR); t = np.arange(n) / SR
    return rng.standard_normal(n) * np.exp(-t / 0.005) * gain

def hit(freq=55, dur=2.4, gain=0.7):
    n = int(dur * SR); t = np.arange(n) / SR
    return np.sin(2 * np.pi * (freq + 90 * np.exp(-t / 0.08)) * t) * np.exp(-t / 0.9) * gain

def pad(t0, t1, notes, gain=0.05):
    n = int((t1 - t0) * SR); t = np.arange(n) / SR; sig = np.zeros(n)
    for f in notes:
        for d in (-0.4, 0.0, 0.5):
            sig += np.sin(2 * np.pi * (f + d) * t + rng.uniform(0, 6.28)) / (1 + 0.0 * f)
    fade = np.minimum(1, t / 2.0) * np.minimum(1, (t1 - t0 - t) / 2.0)
    return sig / len(notes) / 3 * fade * gain

CH = [[110, 165, 220, 262], [87.3, 131, 175, 220], [130.8, 196, 262, 330], [98, 147, 196, 247]]
i = 0; t = 0.0
while t < TOTAL:
    put(pad(0, min(8.4, TOTAL - t), CH[i % 4]), t, 0, 1.0); t += 8.0; i += 1
put(hit(48, 3.6, 0.45), 0.9)
for s in SCHED:
    put(whoosh(0.9, True, (400, 6000), 0.32), s['at'] - 0.45, 0.0, 1.0)
    put(hit(60 + (hash(s['key']) % 7) * 4, 1.6, 0.22), s['at'] + 0.35)
for k in range(16): put(tick(0.09), K['github']['at'] + 1.6 + k * 0.32, (k % 5 - 2) * 0.15)
for k in range(6): put(ping(900 + k * 90, 1.2, 0.1), K['scenario']['at'] + 2.4 + k * 0.9, (k % 3 - 1) * 0.3)
put(ping(1568, 2.2, 0.2), K['contracts']['at'] + 5.0); put(ping(1976, 2.4, 0.16), K['contracts']['at'] + 6.0)
for k in range(5): put(tick(0.12), K['voice']['at'] + 1.0 + k * 1.5)
put(whoosh(4.6, True, (200, 9000), 0.55), K['agent']['at'] - 3.0 + 4.0)
put(hit(42, 4.5, 0.85), TOTAL - 3.3)
# a gentle master: fade in, fade out, soft clip
env = np.ones(len(out)); fi = int(1.0 * SR); fo = int(2.5 * SR); env[:fi] = np.linspace(0, 1, fi); env[int(TOTAL * SR) - fo:int(TOTAL * SR)] = np.linspace(1, 0, fo); env[int(TOTAL * SR):] = 0
out *= env[:, None]; out = np.tanh(out * 1.4) / 1.4 * 0.9
data = (np.clip(out, -1, 1) * 32767).astype('<i2')
with wave.open(sys.argv[2], 'wb') as w: w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())
print('sound written', sys.argv[2], round(TOTAL, 1), 's')
