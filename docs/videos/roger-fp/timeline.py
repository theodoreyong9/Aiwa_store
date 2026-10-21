"""Roger in the first person, version 3: the timeline. The request for the application campaign (CV, cover letters, videos, links of the ads) comes first; what was said before moves later, and the film on the TV is
the new Aiwa film (53.039 s, with the scenarios). Writes schedule.json, timeline.json, the cues of the camera (cues.txt, zoom.txt), the stretches of the picture (seg.json), the two voice scripts (roger.json, agent.json),
and rewrites the line `const EV=…` of screens.html. Run from the repository root:  python3 docs/videos/roger-fp/timeline.py"""
import json, math, re, subprocess, os, tempfile

D = os.path.join('docs', 'videos', 'roger-fp')
LOW = 4.0                       # where the old first line (the veille of the guesthouses) started
FILM_OLD, FILM_NEW = 47.279, 53.039
TXT = {
    'R0': "Mon agent, prends mon CV et fais une campagne de postulation, avec lettre de motivation et vidéo. Donne-moi simplement les fichiers et le lien de l'annonce.",
    'A0': "Compris. Je cherche les annonces et je te prépare tout.",
    'R1': "Mon agent, le serveur du bureau a terminé ma veille des maisons d'hôtes ?", 'A1': "Pas encore.",
    'R2': "Tant pis, modernise les sites internet qui en ont besoin, et propose-leur par mail le résultat avec une vidéo de présentation. Cast ça sur la télé pour que je valide.",
    'A2': "Compris. Je lance ?", 'R3': "Oui.", 'A3': "Ça tourne.", 'A4': "C'est prêt. Premier site, sur la télé.", 'R4': "Envoie.", 'A5': "Envoyé.",
    'A6': "Deuxième site, sur la télé.", 'R5': "Envoie.", 'A7': "Envoyé.", 'R6': "Au fait, ma session Claude vidéo Aiwa est terminée ? Montre-la-moi, s'il te plaît.",
    'A8': "Elle est terminée. Je la passe sur la télé.", 'R7': "Et pour ma vidéo de reportage, mon visage a bien été intégré ?", 'A9': "Ce n'est pas encore terminé.",
    'R8': "D'accord, utilise mes crédits pour accélérer, s'il te plaît.", 'A10': "C'est fait. Environ deux minutes."}
OLD = {'R1': 4.0, 'A1': 8.4, 'R2': 12.6, 'A2': 22.1, 'R3': 24.2, 'A3': 25.2, 'A4': 28.6, 'R4': 36.4, 'A5': 37.4, 'A6': 38.9, 'R5': 47.0, 'A7': 48.2, 'R6': 50.2, 'A8': 55.1, 'R7': 106.2, 'A9': 110.6, 'R8': 112.8, 'A10': 117.1}
DUR = {'R1': 3.78, 'A1': 0.94, 'R2': 8.9, 'A2': 1.57, 'R3': 0.57, 'A3': 0.74, 'A4': 2.65, 'R4': 0.72, 'A5': 0.92, 'A6': 2.01, 'R5': 0.81, 'A7': 0.87, 'R6': 4.31, 'A8': 2.64, 'R7': 3.8, 'A9': 1.51, 'R8': 3.69, 'A10': 2.13}


def measure(text):
    """The length of a line said by the voice (Piper through video-kit/src/voice.py)."""
    with tempfile.TemporaryDirectory() as t:
        open(f'{t}/c.json', 'w').write(json.dumps([{'at': 1.0, 'text': text}], ensure_ascii=False))
        out = subprocess.run(['python3', 'video-kit/src/voice.py', f'{t}/c.json', f'{t}/o.wav', '40', '--lang=fr'], capture_output=True, text=True).stdout
        return round(json.loads(out)['lines'][0]['ends'] - 1.0, 2)


MEASURED = f'{D}/measured.json'      # the lengths of the two new lines, measured once (the voice gives the same line a slightly different length from one run to the next)
if os.path.exists(MEASURED):
    DUR.update(json.load(open(MEASURED)))
else:
    DUR['R0'], DUR['A0'] = measure(TXT['R0']), measure(TXT['A0'])
    json.dump({'R0': DUR['R0'], 'A0': DUR['A0']}, open(MEASURED, 'w'))
START = {'R0': 4.0}
START['A0'] = round(START['R0'] + DUR['R0'] + 0.6, 2)
r1 = START['A0'] + DUR['A0'] + 2.2
delta = math.ceil((r1 - LOW) * 12) / 12            # a multiple of 1/12 s: the 3D frames are 12 a second
delta = round(delta, 4)
FD = round(FILM_NEW - FILM_OLD, 3)
for k, t in OLD.items():
    START[k] = round(t + delta + (FD if t >= 100 else 0), 2)
film_from = round(58.2 + delta, 2)
total = round(126.0 + delta + FD, 2)
T = {k: [START[k], round(START[k] + DUR[k], 2)] for k in START}
EV = {'wake': 5.0, 'glance_in': 7.6, 'glance_hold': 9.4, 'glance_out': 11.0, 'back_tv': round(13.0 + delta, 2), 'work_from': round(25.2 + delta, 2), 'work_to': round(28.6 + delta, 2),
      'site1': [round(28.6 + delta, 2), round(38.9 + delta, 2)], 'site2': [round(38.9 + delta, 2), round(49.9 + delta, 2)], 'film_from': film_from, 'film_len': FILM_NEW,
      'prog_from': START['A9'], 'boost': START['A10'], 'end': round(121.6 + delta + FD, 2), 'total': total, 'ready': round(START['A7'] + 3.0, 2)}
order = ['R0', 'A0', 'R1', 'A1', 'R2', 'A2', 'R3', 'A3', 'A4', 'R4', 'A5', 'A6', 'R5', 'A7', 'R6', 'A8', 'R7', 'A9', 'R8', 'A10']
# the captions: the long lines are cut in two or three, in proportion to the words
SPLIT = {'R0': ["Mon agent, prends mon CV et fais une campagne de postulation,", "avec lettre de motivation et vidéo.", "Donne-moi simplement les fichiers et le lien de l'annonce."],
         'R2': ["Tant pis, modernise les sites internet qui en ont besoin,", "et propose-leur par mail le résultat avec une vidéo de présentation.", "Cast ça sur la télé pour que je valide."]}
CAPS = []
for k in order:
    a, b = T[k]
    if k in SPLIT:
        parts = SPLIT[k]; tot = sum(len(p) for p in parts); x = a
        for p in parts:
            y = round(x + (b - a) * len(p) / tot, 2); CAPS.append([k[0], round(x, 2), y, p]); x = y
    else:
        CAPS.append([k[0], a, b, TXT[k]])
json.dump({'lines': [[k, TXT[k]] for k in order], 'dur': {k: DUR[k] for k in order}, 'start': {k: START[k] for k in order}, 'ev': EV, 'delta': delta}, open(f'{D}/schedule.json', 'w'), ensure_ascii=False, indent=1)
json.dump({'EV': EV, 'T': {k: T[k] for k in order}, 'CAPS': CAPS}, open(f'{D}/timeline.json', 'w'), ensure_ascii=False)
json.dump([{'at': START[k], 'text': TXT[k]} for k in order if k[0] == 'R'], open(f'{D}/roger.json', 'w'), ensure_ascii=False)
json.dump([{'at': START[k], 'text': TXT[k]} for k in order if k[0] == 'A'], open(f'{D}/agent.json', 'w'), ensure_ascii=False)
# the camera (« seconds:where »): the old cues moved with what they follow
d = delta
cues = ['0:room', '3.4:room', '4.4:tv', '6.4:tv', '8.0:phone']      # the phone from the request of the CV to the order of the guesthouses
old_cues = [(17.0, 'phone'), (18.2, 'tv'), (19.8, 'tv'), (21.2, 'phone'), (25.0, 'phone'), (26.4, 'tv'), (33.0, 'tv'), (34.6, 'phone'), (37.2, 'phone'), (38.8, 'tv'), (43.8, 'tv'), (45.4, 'phone'), (55.2, 'phone'), (56.8, 'tv')]
cues += [f'{round(t + d, 2)}:{w}' for t, w in old_cues]
post = FD + d
cues += [f'{round(110.2 + post, 2)}:tv', f'{round(111.8 + post, 2)}:phone', f'{round(119.0 + post, 2)}:phone', f'{round(120.6 + post, 2)}:tv', f'{total}:tv']
fe = film_from + FILM_NEW            # the film ends
zoom = f'{film_from}:56,{round(film_from + 2.2, 2)}:90,{round(fe - 2.28, 2)}:90,{round(fe - 0.01, 2)}:56'
open(f'{D}/cues.txt', 'w').write(','.join(cues)); open(f'{D}/zoom.txt', 'w').write(zoom)
# the stretches (3D, plate at 56 mm, the film on the 90 mm plate): the 3D ones are where the eyes move
seg = [('s', 0, round(27.0 + d, 2)), ('p56', round(27.0 + d, 2), round(33.0 + d, 2)), ('s', round(33.0 + d, 2), round(38.8 + d, 2)), ('p56', round(38.8 + d, 2), round(43.8 + d, 2)), ('s', round(43.8 + d, 2), round(film_from + 2.2, 2)),
       ('film', round(film_from + 2.2, 2), round(fe - 2.28, 2)), ('s', round(fe - 2.28, 2), round(fe + 0.03, 2)), ('p56', round(fe + 0.03, 2), round(110.2 + post, 2)), ('s', round(110.2 + post, 2), round(121.2 + post, 2)), ('p56', round(121.2 + post, 2), total)]
json.dump(seg, open(f'{D}/seg.json', 'w'))
# the page: the line of the events, the captions and the times of the lines
p = f'{D}/screens.html'
s = open(p, encoding='utf-8').read()
i = s.index('const EV='); j = s.index('\n', i)
s = s[:i] + 'const EV=' + json.dumps(EV) + ', CAPS=' + json.dumps(CAPS, ensure_ascii=False) + ', T=' + json.dumps({k: T[k] for k in order}) + ';' + s[j:]
open(p, 'w', encoding='utf-8').write(s)
ev = [(round(x * 12) * 2, round(y * 12) * 2) for k, x, y in seg if k == 's']
print('ONLY=' + ','.join(f'{f0 // 2}:{(f1 + 5) // 2 + 3}' for f0, f1 in ev))      # the 3D frames to render (12 a second), with a tail for each crossfade
pad = lambda a, b: f'{round(a - 0.1, 2)}:{round(b + 0.3, 2)}'
print('RANGES=' + ','.join(pad(a, b) for k, a, b in seg if k == 'p56'))
print('delta', delta, 'total', total, 'film', film_from, '->', round(fe, 2), '\n', ','.join(cues), '\n', zoom, '\n', seg)
