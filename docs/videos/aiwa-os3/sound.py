"""Sound effects of the film, synthesised and placed on the timeline (each one sits on something the picture does). The music is a real track from the catalog, mixed under by make.sh.
   python3 sound.py timeline.json out.wav"""
import json, sys, wave
import numpy as np
SR = 44100
tl = json.load(open(sys.argv[1])); T = tl['T']
out = np.zeros((int(T['total'] * SR) + SR, 2)); rng = np.random.default_rng(11)

def put(sig, t, pan=0.0, gain=1.0):
    i = int(t * SR); n = min(len(sig), len(out) - i)
    if i < 0 or n <= 0: return
    out[i:i + n, 0] += sig[:n] * gain * (1 - max(0, pan)); out[i:i + n, 1] += sig[:n] * gain * (1 + min(0, pan))

def tt(d): return np.arange(int(d * SR)) / SR
def blip(f, d=.25, a=.004):                     # a soft sine with a quick attack and a round decay
    t = tt(d); return np.sin(2 * np.pi * f * t) * np.minimum(1, t / a) * np.exp(-t / (d * .3))
def bell(f, d=1.2):                              # inharmonic partials: glass
    t = tt(d); return sum(np.sin(2 * np.pi * f * k * t) * np.exp(-t / (d * .25 / k ** .6)) * a for k, a in ((1, 1), (2.76, .35), (5.4, .15))) * np.minimum(1, t / .003)
def noise_sweep(d, f0, f1, q=.9):               # air that opens (a whoosh), filtered white noise
    t = tt(d); n = rng.standard_normal(len(t)); f = np.geomspace(f0, f1, len(t)); ph = np.cumsum(2 * np.pi * f / SR)
    mod = np.sin(ph) * .5 + .5
    x = np.convolve(n, np.ones(24) / 24, 'same') * mod
    e = np.sin(np.pi * np.linspace(0, 1, len(t))) ** 2; return x * e
def sub(f, d, drop=.5):
    t = tt(d); ff = f * (1 - drop * (1 - np.exp(-t * 3))); return np.sin(np.cumsum(2 * np.pi * ff / SR)) * np.exp(-t / (d * .35)) * np.minimum(1, t / .01)
def tick(d=.05, f=2400):
    t = tt(d); return rng.standard_normal(len(t)) * np.exp(-t / .006) * .6 + np.sin(2 * np.pi * f * t) * np.exp(-t / .01) * .4
def lp(x, k):                                    # a one-pole low-pass, to take the edge off
    y = np.zeros_like(x); s = 0.0
    for i, v in enumerate(x): s += k * (v - s); y[i] = s
    return y

# the dot in the dark: three soft blips, rising
for i, b in enumerate(T['dotBlink']): put(blip(880 * 2 ** (i * 7 / 12), .3), b, 0, .35)
put(sub(48, 3.5, .1), 4.6, 0, .35)                                           # the room warms up under the phone's arrival
put(bell(1318, 1.6), T['phoneIn'] + .1, .3, .22)
put(blip(1760, .2), T['dotFix'] + .3, .6, .25)                          # the dot docks on the phone
for k in range(2): put(lp(noise_sweep(2.2, 200, 1800), .12), 6.2 + k * .2, -.2, .5)  # the camera pulls back
put(bell(987, 1.4), T['unlock'], 0, .3); put(bell(1480, 1.2), T['unlock'] + .14, .2, .2)    # "j'écoute"
for t0, f in zip(np.arange(T['tvShapes'][0], T['tvShapes'][1], (T['tvShapes'][1] - T['tvShapes'][0]) / 4), (523, 659, 784, 988)):
    put(blip(f, .5), t0 + .55, -.3, .22); put(lp(noise_sweep(.7, 300, 2600), .2), t0 + .35, -.3, .25)   # each shape that becomes the next one
for i in range(14): put(tick(), T['tvContract'][0] + .2 + i * .15 + rng.random() * .03, -.2, .1)       # the code typed
put(sub(70, 1.2), T['tvContract'][0] + 1.9, -.2, .3)                       # the contract is sealed
put(lp(noise_sweep(3.6, 150, 1400), .1), T['rise'][0], .2, .55); put(sub(90, 3, .2), T['rise'][0], 0, .25)   # the phone rises
for t0 in np.linspace(T['taps'][0] + .2, T['taps'][1] - .3, 5): put(tick(.06, 3200), t0, .3, .22); put(blip(1100, .08), t0, .3, .12)  # the widget buttons
put(blip(520, .6) * np.linspace(1, 0, int(.6 * SR)), T['screenOff'], 0, .4); put(sub(55, .8, .6), T['screenOff'], 0, .35)  # the screen goes dark
put(bell(1760, 1.5), T['cast'][0], 0, .25)
for k in range(3): put(lp(noise_sweep(1.6, 400, 4200), .25), T['cast'][0] + .1 + k * .3, .1 + k * .1, .22)   # the stream goes to the television
put(sub(60, 1.6, .3), T['cast'][0] + 1.0, -.3, .3)
for i, t0 in enumerate((29.0, 29.6, 30.2, 32.2, 33.4)): put(bell(660 * 2 ** (i * 3 / 12), .8), t0 + .3, .5 - i * .25, .18)   # the nodes arrive
for t0 in np.arange(T['campaigns'][0], T['campaigns'][0] + 3.4, .45): put(blip(300, .5) * .8 + sub(110, .5, .3) * .4, t0, .3, .2)   # the campaign rings leave
for i in range(5): put(blip(740 * 2 ** (i * 2 / 12), .25), T['campaigns'][0] + 1.5 + i * .25, .4, .18)    # the scenario steps
put(lp(noise_sweep(3.5, 150, 2500), .1), T['world'][0] - .2, -.3, .5)   # the view travels to the world
for i, t0 in enumerate(np.arange(T['world'][0] + 1.0, T['world'][0] + 3.0, .35)): put(bell(1175 * (1 + (i % 3) * .25), .9), t0, -.5 + (i % 4) * .3, .14)  # the pins
put(sub(46, 2.5, .2), T['profileTV'][0] - .2, 0, .3); put(bell(784, 1.8), T['profileTV'][0] + .1, 0, .22)
put(lp(noise_sweep(4.5, 1800, 120), .06), T['closed'][0] + 2.0, 0, .5)    # the room dims
put(blip(55, 4.0), 56.0, 0, .4)
put(sub(36, 3.8, .4), T['end'][0], 0, .85); put(bell(1568, 3.0), T['end'][0] + .05, 0, .3)    # "Aiwa."
put(bell(784, 3.0), 60.6, 0, .18)
m = np.max(np.abs(out)) or 1; out = out / m * .85
with wave.open(sys.argv[2], 'wb') as w:
    w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes((np.clip(out, -1, 1) * 32767).astype('<i2').tobytes())
print('sfx ok', round(len(out) / SR, 1), 's')
