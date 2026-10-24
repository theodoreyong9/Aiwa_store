"""Jean (qualité max): the sound under the voice, all synthesised here (numpy; no sample, no licence to carry): a slow pad on four chords with a sub, a heartbeat that rises toward the universe, the whooshes
under the cuts, the ticks of the taps and of the terminal, the pings of the notification, a riser and a low boom for the last card. Times come from the film's own schedule (video.html).
  python3 docs/videos/jean-max/sound.py docs/videos/jean-max/video.html out.wav"""
import json, re, sys, wave
import numpy as np

SR = 44100
src = open(sys.argv[1], encoding='utf-8').read()
SCHED = json.loads(re.search(r'const SCHED=(\[.*?\]),TOTAL=', src, re.S).group(1)); TOTAL = float(re.search(r'TOTAL=([0-9.]+)', src).group(1))
K = {s['key']: s for s in SCHED}
out = np.zeros((int(TOTAL * SR) + SR, 2)); rng = np.random.default_rng(11)

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

def pluck(freq, dur=0.35, gain=0.3):
    n = int(dur * SR); t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2.01 * t) + 0.12 * np.sin(2 * np.pi * freq * 3.02 * t)) * np.exp(-t / 0.09) * np.minimum(1, t / 0.004) * gain

def ping(freq=1175, dur=1.8, gain=0.2):
    n = int(dur * SR); t = np.arange(n) / SR
    return (np.sin(2 * np.pi * freq * t) + 0.3 * np.sin(2 * np.pi * freq * 2 * t)) * np.exp(-t / 0.6) * np.minimum(1, t / 0.01) * gain

def tick(gain=0.14):
    n = int(0.045 * SR); t = np.arange(n) / SR
    return rng.standard_normal(n) * np.exp(-t / 0.006) * gain

def boom(dur=3.4, gain=0.8):
    n = int(dur * SR); t = np.arange(n) / SR; ph = 2 * np.pi * np.cumsum(42 + 38 * np.exp(-t / 0.35)) / SR
    return (np.sin(ph) * np.exp(-t / 1.1) + 0.25 * np.sin(2 * ph) * np.exp(-t / 0.5)) * np.minimum(1, t / 0.005) * gain

def kick(gain=0.5):
    n = int(0.28 * SR); t = np.arange(n) / SR; ph = 2 * np.pi * np.cumsum(48 + 70 * np.exp(-t / 0.04)) / SR
    return np.sin(ph) * np.exp(-t / 0.12) * np.minimum(1, t / 0.003) * gain

def reverb(x, tail=2.2, mix=0.4):
    n = int(tail * SR); ir = rng.standard_normal(n) * np.exp(-np.arange(n) / SR / (tail / 4.5)); ir /= np.sqrt(np.sum(ir ** 2))
    from numpy.fft import rfft, irfft
    m = len(x) + n; return (1 - mix) * np.pad(x, (0, n)) + mix * irfft(rfft(x, m) * rfft(ir, m), m)

# ---- the pad: four chords (Am, F, C, G), a long slow swell on each, a sub under the root
CH = [(220.0, 261.63, 329.63, 110.0), (174.61, 220.0, 261.63, 87.31), (261.63, 329.63, 392.0, 130.81), (196.0, 246.94, 293.66, 98.0)]
SEG = 6.6; t = np.arange(len(out)) / SR; pad = np.zeros(len(out)); sub = np.zeros(len(out))
for i in range(int(TOTAL / SEG) + 2):
    a = i * SEG - 0.9; ch = CH[i % 4]
    w = np.clip((t - a) / 1.6, 0, 1) * np.clip((a + SEG + 0.9 - t) / 1.6, 0, 1)
    for f in ch[:3]:
        for det in (-0.004, 0.0, 0.004):
            ff = f * (1 + det)
            pad += w * (np.sin(2 * np.pi * ff * t) + 0.45 * np.sin(2 * np.pi * 2 * ff * t) + 0.2 * np.sin(2 * np.pi * 3 * ff * t))
    sub += w * np.sin(2 * np.pi * ch[3] * t)
slow = 0.8 + 0.2 * np.sin(2 * np.pi * 0.11 * t)
fade = np.clip(t / 2.5, 0, 1) * np.clip((TOTAL - t) / 2.5, 0, 1)
pad = pad / (np.max(np.abs(pad)) + 1e-9) * 0.30 * slow * fade; sub = sub / (np.max(np.abs(sub)) + 1e-9) * 0.22 * fade
out[:, 0] += pad + sub; out[:, 1] += np.roll(pad, 90) + sub                      # a hair of delay between the ears: width

# ---- a heartbeat that wakes at the brain and grows toward the universe
for k in range(int(TOTAL / 0.83)):
    tk = 0.83 * k
    if tk < K['brain']['at'] or tk > TOTAL - 3.2: continue
    amp = 0.18 + 0.38 * min(1, (tk - K['brain']['at']) / (K['universe']['at'] + 3 - K['brain']['at']))
    put(kick(amp), tk)

# ---- the cuts: a whoosh that rises into each new shot
for key in ['modes', 'accounts', 'brain', 'sessions', 'self', 'dark', 'universe', 'me']:
    put(whoosh(0.9, up=True, gain=0.42), K[key]['at'] - 0.75, pan=0.25 if key in ('modes', 'brain', 'self', 'universe') else -0.25)
# ---- the taps of the remote: a tick for each tab (manual, then by itself)
for dt in (.25, .6, .95, 1.3): put(tick(0.2), K['modes']['at'] + dt, pan=0.1)
for dt in (.5, 1.0, 1.5, 2.0, 2.5): put(tick(0.2), K['self']['at'] + dt, pan=-0.1); put(pluck(880 + 110 * int(dt * 2), 0.3, 0.1), K['self']['at'] + dt)
# ---- the voice ring: a soft swell while she speaks to the phone
put(whoosh(1.6, up=True, f=(200, 2400), gain=0.28), K['modes']['at'] + 1.5)
# ---- the TV: the cards arrive
put(pluck(784, 0.6, 0.22), K['accounts']['at'] + 0.1); put(pluck(988, 0.6, 0.22), K['accounts']['at'] + 0.35)
# ---- the terminal types: a quick patter, line after line
for i in range(10):
    for j in range(int(rng.integers(7, 16))): put(tick(0.07), K['brain']['at'] + 0.2 + i * 0.42 + j * 0.028, pan=0.3)
put(pluck(659, 0.5, 0.2), K['brain']['at'] + 2.6)
# ---- the sessions wake up one by one
for i in range(6): put(pluck(392 * (1.1225 ** i), 0.4, 0.2), K['sessions']['at'] + 0.3 + i * 0.55, pan=-0.5 + 0.2 * i)
# ---- the screen is dark, then the notification: a ping and its echo
put(ping(1175), K['dark']['at'] + 0.6, pan=0.1); put(ping(1568, 1.4, 0.14), K['dark']['at'] + 2.2, pan=-0.1)
# ---- the universe: words arrive with a chime
for dt in (.5, 2.9, 5.4): put(pluck(1046, 0.8, 0.16), K['universe']['at'] + dt); put(whoosh(0.7, up=False, f=(5000, 500), gain=0.2), K['universe']['at'] + dt - 0.1)
# ---- Jean: a rise to the name, a low boom on the last card
put(whoosh(2.4, up=True, f=(120, 6500), gain=0.5), TOTAL - 5.4)
b_ = boom(); p_ = 0.5 * pluck(196, 2.6, 0.6); m_ = np.zeros(max(len(b_), len(p_))); m_[:len(b_)] += b_; m_[:len(p_)] += p_
put(reverb(m_), TOTAL - 3.05, gain=0.9)
put(pluck(784, 1.0, 0.18), TOTAL - 2.3)

out = np.tanh(out * 0.9) * 0.9
pcm = (np.clip(out[:int(TOTAL * SR)], -1, 1) * 32767).astype('<i2')
with wave.open(sys.argv[2], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(pcm.tobytes())
print('sound', round(len(pcm) / SR, 1), 's')
