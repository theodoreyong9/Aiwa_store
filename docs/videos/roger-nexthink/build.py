# Roger, first person, for Nexthink: a DEMONSTRATION (the Nexthink scenario is invented; nothing here is said to be real). One visual language: the glass of the widget, drawn in HTML pages.
# Run from the repository root: python3 docs/videos/roger-nexthink/build.py   (then make.sh: render, voice lowered like Roger's, assemble)
import json, subprocess, tempfile, os
D = 'docs/videos/roger-nexthink'
LINES = [("Ma passion, c'est le service client.", 'ear'),
         ("Et dans ma poche, un widget.", 'widget'),
         ("Je le pilote à la main, à la voix, ou je le laisse travailler seul.", 'modes'),
         ("C'est un système d'exploitation ouvert, bâti sur Claude Code et Termux.", 'os'),
         ("Voilà les boutons : Domaine, Files, Build.", 'buttons'),
         ("Dis au serveur du bureau de produire des vidéos de Noël personnalisées pour Nexthink. Fais-les critiquer par ma session vidéo, puis envoie-les sur ma télé quand elles sont prêtes.", 'cmd'),
         ("En attendant, montre-moi où en est la vidéo Nexthink à venir : la vidéo d'Aiwa.", 'wait'),
         ("Je termine par Nexthink, le DEX qui centralise les cerveaux.", 'dex'),
         ("Pourquoi ? Parce que mon modèle économique est ailleurs.", 'why'),
         ("Il est dans l'autogestion économique des agents, et les contrats hors chaîne.", 'ask'),
         ("Ce que je cherche en échange : un poste de service client dans le DEX Nexthink.", 'hire'),
         ("Service client : des résultats exceptionnels.", 'cs'),
         ("Business analyst : prolifique, et orienté système.", 'ba'),
         ("Et là, on se retrouve chez un client de Nexthink, qui lance sa campagne de refonte de site web, et informe Nexthink d'une amélioration possible du délai de traitement.", 'client')]


def measure(text):
    with tempfile.TemporaryDirectory() as t:
        open(f'{t}/c.json', 'w').write(json.dumps([{'at': 1.0, 'text': text}], ensure_ascii=False))
        out = subprocess.run(['python3', 'video-kit/src/voice.py', f'{t}/c.json', f'{t}/o.wav', '60', '--lang=fr'], capture_output=True, text=True).stdout
        return round(json.loads(out)['lines'][0]['ends'] - 1.0, 2)


cache = f'{D}/measured.json'
dur = json.load(open(cache)) if os.path.exists(cache) else None
if dur is None or len(dur) != len(LINES):
    dur = [measure(t) for t, _ in LINES]; json.dump(dur, open(cache, 'w'))
t = 0.8; sched = []
for (text, key), d in zip(LINES, dur):
    sched.append({'at': round(t, 2), 'end': round(t + d, 2), 'text': text, 'key': key}); t += d + (0.9 if key in ('ear', 'buttons', 'wait', 'dex', 'ba') else 0.5)
total = round(t + 3.0, 2)
json.dump([{'at': s['at'], 'text': s['text']} for s in sched], open(f'{D}/cues.json', 'w'), ensure_ascii=False)
src = open(f'{D}/page.html', encoding='utf-8').read()
open(f'{D}/video.html', 'w', encoding='utf-8').write(src.replace('/*SCHED*/', 'const SCHED=' + json.dumps(sched, ensure_ascii=False) + ',TOTAL=' + str(total) + ';'))
print('total', total, [(s['key'], s['at'], s['end']) for s in sched])
