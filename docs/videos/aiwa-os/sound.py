"""The sound of the film « Aiwa », all synthesised (numpy: no sample, no licence to carry), and NOT the bed of the other films: here the sound has a score and a signature for each part.
  listen      a heartbeat in the dark (lub-dub), a far drone
  control     a plucked arpeggio (FM bells) comes in; the keys of a terminal
  create      a pentatonic « glass » for each object that is born, a bass and a soft kick
  free        a breath, then one deep stamp and a shimmer
  everywhere  an airy pad, a sonar for the voice, a TV hum, a glass chime for the glasses
  connect     data blips that jump from the left to the right, three voices of the pad
  campaigns   the groove at full strength (kick, hats, bass), a clock that ticks, a check that rings
  discover    a radar sweep, pings that echo, a swelling pad
  profile     shimmering chimes high in the air
  end         a riser, a boom, then one single bell and the silence
  python3 docs/videos/aiwa-os/sound.py docs/videos/aiwa-os/video.html out.wav"""
import json, re, sys, wave
import numpy as np

SR = 44100
src = open(sys.argv[1], encoding='utf-8').read()
SCHED = json.loads(re.search(r'const SCHED=(\[.*?\]),TOTAL=', src, re.S).group(1)); TOTAL = float(re.search(r'TOTAL=([0-9.]+)', src).group(1))
K = {s['key']: s for s in SCHED}
N = int(TOTAL * SR) + 2 * SR
L = np.zeros(N); R = np.zeros(N); rng = np.random.default_rng(23)
BPM = 96; BEAT = 60 / BPM

def put(sig, t, pan=0.0, gain=1.0, bus=None):
    i = int(t * SR)
    if i < 0 or i >= N: return
    n = min(len(sig), N - i); l, r = (1 - max(0, pan)) * gain, (1 + min(0, pan)) * gain
    L[i:i + n] += sig[:n] * l; R[i:i + n] += sig[:n] * r

def env(n, a=0.005, d=0.3):
    t = np.arange(n) / SR; return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / d)

def fm(freq, dur, ratio=3.5, index=3.0, d=0.5, gain=0.2):
    n = int(dur * SR); t = np.arange(n) / SR
    mod = np.sin(2 * np.pi * freq * ratio * t) * index * np.exp(-t / (d * 0.6))
    return np.sin(2 * np.pi * freq * t + mod) * env(n, 0.003, d) * gain

def kick(gain=0.7):
    n = int(0.45 * SR); t = np.arange(n) / SR
    return np.sin(2 * np.pi * (46 + 110 * np.exp(-t / 0.035)) * t) * np.exp(-t / 0.16) * gain

def hat(gain=0.07):
    n = int(0.05 * SR); t = np.arange(n) / SR
    x = np.diff(rng.standard_normal(n + 1)); return x * np.exp(-t / 0.011) * gain

def bass(freq, dur, gain=0.3):
    n = int(dur * SR); t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)) * env(n, 0.01, dur * 0.7) * gain

def lowpass_sweep(x, f0, f1):
    y = np.zeros_like(x); st = 0.0; a = 1 - np.exp(-2 * np.pi * np.linspace(f0, f1, len(x)) / SR)
    for i in range(len(x)): st += a[i] * (x[i] - st); y[i] = st
    return y

def whoosh(dur, up=True, f=(300, 5200), gain=0.4):
    n = int(dur * SR); y = lowpass_sweep(rng.standard_normal(n), *(f if up else f[::-1])); y = y - lowpass_sweep(y, 80, 80)
    t = np.arange(n) / SR / dur
    return y / (np.max(np.abs(y)) + 1e-9) * np.sin(np.pi * np.clip(t, 0, 1)) ** (2.2 if up else 1.6) * gain

def pad(dur, notes, gain=0.05, lp=2400):
    n = int(dur * SR); t = np.arange(n) / SR; sig = np.zeros(n)
    for f in notes:
        for d in (-0.5, 0.0, 0.6): sig += np.sin(2 * np.pi * (f + d) * t + rng.uniform(0, 6.28)) + 0.3 * np.sin(2 * np.pi * (f * 2 + d) * t)
    sig = lowpass_sweep(sig / (len(notes) * 3), lp, lp)
    return sig * np.minimum(1, t / 2.5) * np.minimum(1, (dur - t) / 2.5) * gain

def tick(gain=0.09, f=2600):
    n = int(0.03 * SR); t = np.arange(n) / SR
    return (rng.standard_normal(n) * 0.6 + np.sin(2 * np.pi * f * t)) * np.exp(-t / 0.004) * gain

# the score: D minor, 96 bpm
D2, F2, A2, C3 = 73.4, 87.3, 110.0, 130.8
CH = [(D2, [146.8, 220.0, 349.2]), (F2 * 2 / 2, [174.6, 261.6, 349.2]), (A2 / 2 * 1.0, [220.0, 329.6, 440.0]), (C3 / 2, [130.8, 196.0, 329.6])]
PENT = [293.7, 349.2, 392.0, 440.0, 523.3, 587.3, 698.5, 784.0, 880.0]

def bar_chords(t0, t1, gain):
    t = t0; i = 0
    while t < t1:
        root, notes = CH[i % 4]; put(pad(min(2 * BEAT * 2 + 0.6, t1 - t + 0.6), notes, gain), t); t += 4 * BEAT; i += 1

# listen: lub-dub at 64 per minute, a far drone
t = 0.2
while t < K['listen']['end']:
    put(kick(0.55) * 0.8, t); put(kick(0.4) * 0.7, t + 0.24); t += 0.94
put(pad(K['listen']['end'] + 2, [D2, D2 * 2], 0.07, 600), 0)
put(whoosh(1.4, True, (150, 2400), 0.3), 4.0)
# the whoosh of each part
for k, s in K.items():
    if k != 'listen': put(whoosh(0.8, True, (400, 5000), 0.22), s['at'] - 0.4)
# control: the arpeggio, the keys
a0 = K['control']['at']; arp = [0, 2, 4, 2, 5, 4, 2, 0]
for j in range(int((K['campaigns']['end'] - a0) / (BEAT / 2))):
    tt = a0 + j * BEAT / 2
    if tt > K['free']['at'] and tt < K['everywhere']['at'] + 0.2: continue
    put(fm(PENT[arp[j % 8] + (2 if (j // 16) % 2 else 0)], 0.5, 3.5, 2.5, 0.35, 0.09), tt, ((j % 5) - 2) * 0.18)
for k in range(22): put(tick(0.08), a0 + 2.2 + k * 0.17 + rng.uniform(0, 0.05), (k % 3 - 1) * 0.3)
bar_chords(a0, K['free']['at'], 0.05)
# create: a glass for each object, a bass and a kick
c0 = K['create']['at']
for i, f in enumerate([PENT[4], PENT[5], PENT[6], PENT[3], PENT[7], PENT[8]]): put(fm(f, 1.4, 5.0, 4.0, 0.8, 0.16), c0 + 0.3 + i * 0.5 + 0.2, (i % 3 - 1) * 0.5)
for j in range(int(((K['free']['at']) - c0) / BEAT)):
    put(kick(0.5), c0 + j * BEAT); put(bass([D2, D2, F2, A2 / 2][j % 4] * 1.0, BEAT * 0.9, 0.28), c0 + j * BEAT + BEAT / 2)
# free: a breath then the stamp
f0 = K['free']['at']; put(whoosh(0.6, False, (3000, 200), 0.25), f0 - 0.2)
put(kick(1.0) * 1.3, f0 + 0.4); put(fm(110, 3.0, 2.0, 1.5, 1.6, 0.35), f0 + 0.4); put(fm(1760, 2.5, 3.0, 2.0, 1.2, 0.1), f0 + 0.45)
# everywhere: air, a sonar, a hum, a glass chime
e0 = K['everywhere']['at']; d = (K['everywhere']['end'] - e0) / 3
bar_chords(e0, K['connect']['at'], 0.06)
for k in range(3): put(fm(1175, 2.4, 1.0, 0.6, 1.2, 0.2), e0 + 0.5 + k * 0.7, -0.3 + k * 0.3)
put(pad(d, [60.0, 120.0], 0.09, 400), e0 + d)
put(fm(1568, 2.8, 5.0, 3.0, 1.4, 0.16), e0 + 2 * d + 0.6); put(fm(2093, 2.8, 5.0, 3.0, 1.4, 0.1), e0 + 2 * d + 0.9)
# connect: blips that jump, three voices
n0 = K['connect']['at']
for j in range(int((K['campaigns']['at'] - n0) / (BEAT / 2))):
    tt = n0 + j * BEAT / 2; put(fm(PENT[(j * 3) % 9] * 2, 0.18, 2.0, 1.5, 0.08, 0.07), tt, -0.8 + (j % 8) / 8 * 1.6)
for j in range(int((K['campaigns']['at'] - n0) / BEAT)): put(kick(0.45), n0 + j * BEAT)
# campaigns: the groove at full strength, the clock, the check
m0 = K['campaigns']['at']
for j in range(int((K['discover']['at'] - m0) / BEAT)):
    put(kick(0.6), m0 + j * BEAT); put(hat(0.09), m0 + j * BEAT + BEAT / 2); put(bass([D2, D2, F2, A2 / 2][j % 4], BEAT * 0.8, 0.3), m0 + j * BEAT + BEAT / 4)
for j in range(int((K['discover']['at'] - m0) / 1.0)): put(tick(0.06, 1500), m0 + j * 1.0 + 0.5, 0.4)
for k in range(3): put(fm(1568 + 220 * k, 1.2, 1.0, 0.5, 0.5, 0.12), m0 + 2.6 + k * 1.6)
bar_chords(m0, K['discover']['at'], 0.06)
# discover: a radar sweep, pings that echo
r0 = K['discover']['at']
put(whoosh(5.0, True, (200, 6000), 0.3), r0 + 0.3)
for k in range(8): put(fm(880 * (1 + (k % 4) * 0.25), 1.6, 1.0, 0.4, 0.8, 0.12 / (1 + k * 0.1)), r0 + 0.6 + k * 0.75, (k % 5 - 2) * 0.3)
put(pad(8, [A2 * 2, A2 * 3, A2 * 4], 0.06, 3000), r0 - 0.5)
# profile: shimmering chimes
p0 = K['profile']['at']
for k in range(10): put(fm(PENT[(k * 2) % 9] * 2, 2.0, 5.0, 3.5, 1.1, 0.1 / (1 + k * 0.05)), p0 + 0.3 + k * 0.55, (k % 4 - 1.5) * 0.4)
put(pad(7, [D2 * 4, D2 * 6, D2 * 8], 0.06, 4000), p0)
# end: the riser, the boom, the single bell
z0 = K['end']['at']
put(whoosh(2.0, True, (200, 9000), 0.6), z0 - 0.2)
put(kick(1.0) * 1.5, z0 + 1.8); put(fm(36, 4.0, 1.0, 0.6, 1.8, 0.55), z0 + 1.8)
put(fm(587.3, 5.0, 3.0, 1.2, 2.4, 0.2), z0 + 3.4); put(fm(293.7, 6.0, 3.0, 1.0, 3.0, 0.16), z0 + 3.4)
# an echo (one delay line), a gentle master
def echo(x, delay, fb, mix):
    n = int(delay * SR); y = x.copy()
    for k in range(1, 5): y[k * n:] += x[:len(x) - k * n] * (fb ** k) * mix
    return y
Lo = echo(L, 0.375, 0.5, 0.35); Ro = echo(R, 0.5, 0.5, 0.35)
fi = int(0.6 * SR); fo = int(2.6 * SR); tot = int(TOTAL * SR)
e = np.ones(N); e[:fi] = np.linspace(0, 1, fi); e[tot - fo:tot] = np.linspace(1, 0, fo); e[tot:] = 0
out = np.stack([Lo * e, Ro * e], axis=1); out = np.tanh(out * 1.3) / 1.3 * 0.92
data = (np.clip(out, -1, 1) * 32767).astype('<i2')
with wave.open(sys.argv[2], 'wb') as w: w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(data.tobytes())
print('sound written', sys.argv[2], round(TOTAL, 1), 's')
