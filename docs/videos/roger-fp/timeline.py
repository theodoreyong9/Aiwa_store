"""Roger in the first person, version 4: the timeline, built from one place. Run from the repository root:  python3 docs/videos/roger-fp/timeline.py
Order of the film: Roger asks the agent for an application campaign from his CV → the TV, close up, plays a presentation of the CV (docs/videos/jean-cv) → the guesthouses (what was there before, moved later) →
the Aiwa film on the TV (docs/videos/aiwa-v25-3) → the state of his own video → he comes back to the applications, the TV plays the application video for the real ad (docs/videos/jean-candidature),
« Envoie » → he takes the phone in his hand, unlocks it, sees the widget and starts the « Entretien » scenario → the end card.
Writes: schedule.json, timeline.json (EV, T, CAPS), roger.json / agent.json (the voice scripts), cues.txt (the camera), seg.json (the stretches of the picture), measured.json (the length of the lines said by
the voice, measured once), and rewrites the line `const EV=…` of screens.html. Prints what to render."""
import json, math, os, subprocess, tempfile

D = os.path.join('docs', 'videos', 'roger-fp')
FILM_OLD, FILM = 47.279, 50.679          # the Aiwa film on the TV: v25-2 was before, v25-3 now (with the scenarios)
CV_LEN, CAND_LEN = 16.0, 38.04           # the presentation of the CV, the application video
TXT = {
    'R0': "Mon agent, prends mon CV et fais une campagne de postulation, avec lettre de motivation et vidéo. Donne-moi simplement les fichiers et le lien de l'annonce.",
    'A0': "Compris. Je cherche les annonces et je te prépare tout.",
    'R1': "Mon agent, le serveur du bureau a terminé ma veille des maisons d'hôtes ?", 'A1': "Pas encore.",
    'R2': "Tant pis, modernise les sites internet qui en ont besoin, et propose-leur par mail le résultat avec une vidéo de présentation. Cast ça sur la télé pour que je valide.",
    'A2': "Compris. Je lance ?", 'R3': "Oui.", 'A3': "Ça tourne.", 'A4': "C'est prêt. Premier site, sur la télé.", 'R4': "Envoie.", 'A5': "Envoyé.",
    'A6': "Deuxième site, sur la télé.", 'R5': "Envoie.", 'A7': "Envoyé.", 'R6': "Au fait, ma session Claude vidéo Aiwa est terminée ? Montre-la-moi, s'il te plaît.",
    'A8': "Elle est terminée. Je la passe sur la télé.", 'R7': "Et pour ma vidéo de reportage, mon visage a bien été intégré ?", 'A9': "Ce n'est pas encore terminé.",
    'R8': "D'accord, utilise mes crédits pour accélérer, s'il te plaît.", 'A10': "C'est fait. Environ deux minutes.",
    'R9': "Reviens sur mes candidatures. Montre-moi la vidéo pour Frontiers.", 'R10': "Envoie.", 'A11': "Envoyé, avec la lettre et le lien de l'annonce.",
    'R11': "Et maintenant, préparons mon entretien.", 'A12': "Scénario entretien lancé. Je t'écoute."}
OLD = {'R1': 4.0, 'A1': 8.4, 'R2': 12.6, 'A2': 22.1, 'R3': 24.2, 'A3': 25.2, 'A4': 28.6, 'R4': 36.4, 'A5': 37.4, 'A6': 38.9, 'R5': 47.0, 'A7': 48.2, 'R6': 50.2, 'A8': 55.1, 'R7': 106.2, 'A9': 110.6, 'R8': 112.8, 'A10': 117.1}
DUR = {'R1': 3.78, 'A1': 0.94, 'R2': 8.9, 'A2': 1.57, 'R3': 0.57, 'A3': 0.74, 'A4': 2.65, 'R4': 0.72, 'A5': 0.92, 'A6': 2.01, 'R5': 0.81, 'A7': 0.87, 'R6': 4.31, 'A8': 2.64, 'R7': 3.8, 'A9': 1.51, 'R8': 3.69, 'A10': 2.13, 'R10': 0.72}


def measure(text):
    """The length of a line said by the voice (Piper through video-kit/src/voice.py)."""
    with tempfile.TemporaryDirectory() as t:
        open(f'{t}/c.json', 'w').write(json.dumps([{'at': 1.0, 'text': text}], ensure_ascii=False))
        out = subprocess.run(['python3', 'video-kit/src/voice.py', f'{t}/c.json', f'{t}/o.wav', '40', '--lang=fr'], capture_output=True, text=True).stdout
        return round(json.loads(out)['lines'][0]['ends'] - 1.0, 2)


MEASURED = f'{D}/measured.json'      # measured once: the voice gives the same line a slightly different length from one run to the next
M = json.load(open(MEASURED)) if os.path.exists(MEASURED) else {}
for k in ('R0', 'A0', 'R9', 'A11', 'R11', 'A12'):
    if k not in M:
        M[k] = measure(TXT[k])
json.dump(M, open(MEASURED, 'w'))
DUR.update(M)
r = lambda x: round(x, 2)
START = {'R0': 4.0}
START['A0'] = r(START['R0'] + DUR['R0'] + 0.6)
a0_end = START['A0'] + DUR['A0']
# the close-up on the TV: the eyes go from the phone to the TV at 90 mm (2.4 s), the presentation of the CV plays, then back to the phone (1.8 s)
cv_zoom = r(a0_end + 0.9); cv_from = r(cv_zoom + 2.4); cv_end = r(cv_from + CV_LEN); cv_back = r(cv_end + 1.8)
delta = round(math.ceil((cv_back + 1.0 - 4.0) * 12) / 12, 4)      # R1 comes 1 s after the eyes are back on the phone; a multiple of 1/12 s (the 3D frames are 12 a second)
FD = round(FILM - FILM_OLD, 3)
for k, t in OLD.items():
    START[k] = r(t + delta + (FD if t >= 100 else 0))
film_from = r(58.2 + delta); film_end = r(film_from + FILM)
E10 = r(START['A10'] + DUR['A10'])
# the application video: R9, the eyes to the TV (90 mm), the video, « Envoie », « Envoyé », the eyes back to the phone
START['R9'] = r(E10 + 1.6); r9_end = r(START['R9'] + DUR['R9'])
tv_in = r(START['R9'] - 0.4); tv_in_end = r(tv_in + 2.4)
cand_from = r(max(r9_end + 0.5, tv_in_end)); cand_end = r(cand_from + CAND_LEN)
START['R10'] = r(cand_end + 0.5); START['A11'] = r(START['R10'] + DUR['R10'] + 0.5); e11 = r(START['A11'] + DUR['A11'])
cand_back = r(e11 + 0.2); P0 = r(cand_back + 2.2)            # the eyes on the phone on the table, then the phone is taken
START['R11'] = r(P0 + 0.3)
pick_end = r(P0 + 2.2)
H = {'pick': P0, 'pick_end': pick_end, 'unlock': r(pick_end + 1.2)}
H['home'] = r(H['unlock'] + 0.9); H['tap1'] = r(H['home'] + 1.8); H['s1'] = r(H['tap1'] + 0.35); H['tap2'] = r(H['s1'] + 1.5); H['list'] = r(H['tap2'] + 0.35)
H['tap3'] = r(H['list'] + 1.6); H['form'] = r(H['tap3'] + 0.35); H['tap4'] = r(H['form'] + 2.4); H['toast'] = r(H['tap4'] + 0.5)
START['A12'] = r(H['toast'] + 0.6)
end_card = r(START['A12'] + DUR['A12'] + 1.4); total = r(end_card + 4.6)
T = {k: [START[k], r(START[k] + DUR[k])] for k in START}
EV = {'wake': 5.0, 'glance_in': 7.6, 'glance_hold': 9.4, 'glance_out': 11.0, 'back_tv': r(13.0 + delta), 'work_from': r(25.2 + delta), 'work_to': r(28.6 + delta),
      'site1': [r(28.6 + delta), r(38.9 + delta)], 'site2': [r(38.9 + delta), r(49.9 + delta)], 'film_from': film_from, 'film_len': FILM,
      'prog_from': START['A9'], 'boost': START['A10'], 'end': end_card, 'total': total, 'ready': r(START['A7'] + 3.0), 'hand': H,
      'vstart': {'cv': cv_from, 'film': film_from, 'cand': cand_from}, 'black': [[cv_zoom, cv_back], [film_from - 0.1, film_end + 0.1], [tv_in, tv_in_end], [cand_from, cand_end + 0.1]]}
order = ['R0', 'A0', 'R1', 'A1', 'R2', 'A2', 'R3', 'A3', 'A4', 'R4', 'A5', 'A6', 'R5', 'A7', 'R6', 'A8', 'R7', 'A9', 'R8', 'A10', 'R9', 'R10', 'A11', 'R11', 'A12']
SPLIT = {'R0': ["Mon agent, prends mon CV et fais une campagne de postulation,", "avec lettre de motivation et vidéo.", "Donne-moi simplement les fichiers et le lien de l'annonce."],
         'R2': ["Tant pis, modernise les sites internet qui en ont besoin,", "et propose-leur par mail le résultat avec une vidéo de présentation.", "Cast ça sur la télé pour que je valide."]}
CAPS = []
for k in order:
    a, b = T[k]
    if k in SPLIT:
        parts = SPLIT[k]; tot = sum(len(p) for p in parts); x = a
        for p in parts:
            y = r(x + (b - a) * len(p) / tot); CAPS.append([k[0], r(x), y, p]); x = y
    else:
        CAPS.append([k[0], a, b, TXT[k]])
json.dump({'lines': [[k, TXT[k]] for k in order], 'dur': {k: DUR[k] for k in order}, 'start': {k: START[k] for k in order}, 'ev': EV, 'delta': delta}, open(f'{D}/schedule.json', 'w'), ensure_ascii=False, indent=1)
json.dump({'EV': EV, 'T': {k: T[k] for k in order}, 'CAPS': CAPS}, open(f'{D}/timeline.json', 'w'), ensure_ascii=False)
json.dump([{'at': START[k], 'text': TXT[k]} for k in order if k[0] == 'R'], open(f'{D}/roger.json', 'w'), ensure_ascii=False)
json.dump([{'at': START[k], 'text': TXT[k]} for k in order if k[0] == 'A'], open(f'{D}/agent.json', 'w'), ensure_ascii=False)
# the camera: (seconds, where). Looks: room, tv (56 mm), tv90 (the close-up), phone (on the table), held (in the hand)
d = delta
C = [(0, 'room'), (3.4, 'room'), (4.4, 'tv'), (6.4, 'tv'), (8.0, 'phone'), (cv_zoom, 'phone'), (cv_from, 'tv90'), (cv_end, 'tv90'), (cv_back, 'phone')]
old = [(17.0, 'phone'), (18.2, 'tv'), (19.8, 'tv'), (21.2, 'phone'), (25.0, 'phone'), (26.4, 'tv'), (33.0, 'tv'), (34.6, 'phone'), (37.2, 'phone'), (38.8, 'tv'), (43.8, 'tv'), (45.4, 'phone'), (55.2, 'phone'), (56.8, 'tv')]
C += [(r(t + d), w) for t, w in old]
C += [(film_from, 'tv'), (r(film_from + 2.2), 'tv90'), (r(film_end - 2.28), 'tv90'), (r(film_end - 0.01), 'tv')]
post = d + FD
C += [(r(110.2 + post), 'tv'), (r(111.8 + post), 'phone'), (r(119.0 + post), 'phone')]       # the old end: the phone while he asks for the crédits
C += [(tv_in, 'phone'), (tv_in_end, 'tv90'), (r(cand_end + 0.1), 'tv90'), (r(cand_back), 'tv90'), (P0, 'phone'), (pick_end, 'held'), (total, 'held')]
# keep the cues in time order; where two cues give the same time keep the later one
C.sort(key=lambda c: c[0]); out = []
for t, w in C:
    if out and abs(out[-1][0] - t) < 1e-6: out[-1] = (t, w)
    elif out and out[-1][1] == w and w not in ('tv90', 'held'): out.append((t, w))
    else: out.append((t, w))
open(f'{D}/cues.txt', 'w').write(','.join(f'{t}:{w}' for t, w in out))
# the stretches: a static look is a plate (the room with the TV dark, the phone dark…) with the pages laid on it; a glide is 3D
VID = [('cv', cv_from, cv_end), ('film', r(film_from + 2.2), r(film_end - 2.28)), ('cand', cand_from, cand_end)]
seg = []
for (t0, w0), (t1, w1) in zip(out, out[1:]):
    if t1 - t0 < 1e-6: continue
    if w0 == w1 and w0 not in ('room', 'held'):
        kind = {'tv': 'p56', 'phone': 'pph', 'tv90': 'p90'}[w0]
        if kind == 'p90':
            cuts = [t0]
            for n, a, b in VID:
                if a < t1 and b > t0: cuts += [max(a, t0), min(b, t1)]
            cuts = sorted(set(cuts + [t1]))
            for x, y in zip(cuts, cuts[1:]):
                inside = [n for n, a, b in VID if a <= x + 1e-6 and y <= b + 1e-6]
                seg.append({'k': 'v90' if inside else 'p90', 'a': r(x), 'b': r(y), 'v': inside[0] if inside else None})
        else:
            seg.append({'k': kind, 'a': r(t0), 'b': r(t1)})
    else:
        seg.append({'k': 's', 'a': r(t0), 'b': r(t1)})
# merge neighbours of the same kind (the 3D ones come one after the other when two glides touch)
m = []
for s in seg:
    if m and m[-1]['k'] == s['k'] and s['k'] == 's' and abs(m[-1]['b'] - s['a']) < 1e-6: m[-1]['b'] = s['b']
    else: m.append(s)
json.dump(m, open(f'{D}/seg.json', 'w'))
ev = [(round(s['a'] * 12) * 2, round(s['b'] * 12) * 2) for s in m if s['k'] == 's']
print('ONLY=' + ','.join(f'{f0 // 2}:{(f1 + 5) // 2 + 3}' for f0, f1 in ev))
pad = lambda a, b: f'{round(a - 0.1, 2)}:{round(b + 0.3, 2)}'
print('RANGES=' + ','.join(pad(s['a'], s['b']) for s in m if s['k'] in ('p56', 'p90')))
print('PHRANGES=' + ','.join(pad(s['a'], s['b']) for s in m if s['k'] == 'pph'))
print('FRAMES', ev and sum((f1 - f0 + 5) // 2 + 3 for f0, f1 in ev))
print('delta', delta, 'total', total, 'film', film_from, '->', film_end, 'cv', cv_from, cv_end, 'cand', cand_from, cand_end, 'pick', P0)
print(','.join(f'{t}:{w}' for t, w in out))
p = f'{D}/screens.html'
s = open(p, encoding='utf-8').read()
i = s.index('const EV='); j = s.index('\n', i)
s = s[:i] + 'const EV=' + json.dumps(EV) + ', CAPS=' + json.dumps(CAPS, ensure_ascii=False) + ', T=' + json.dumps({k: T[k] for k in order}) + ';' + s[j:]
open(p, 'w', encoding='utf-8').write(s)
