# An application video: « what I would do, concretely » for ONE real job ad (Salesforce Business Analyst, Frontiers Media SA, Lausanne, seen on the aggregator Nejo; to check that the ad is still open before sending).
# Only what the CV says about the person and what the ad says about the job; what is proposed is a proposal, not a claim about the company.
# Run from the repository root: python3 docs/videos/jean-candidature/build.py   (then video-kit render / assemble, voice lowered like Roger's: see make.sh)
import json, subprocess, tempfile, os
D = 'docs/videos/jean-candidature'
LINES = [("Bonjour, je suis Jean da Cunha, business analyst à Lausanne.", 'hello'),
         ("Votre annonce cherche quelqu'un qui transforme les besoins des équipes en tableaux de bord Salesforce.", 'need'),
         ("Voici ce que je ferais, concrètement.", 'plan'),
         ("Un : rencontrer les équipes ventes, service et marketing, pour cartographier ce qu'elles veulent mesurer.", 's1'),
         ("Deux : construire les tableaux de bord dans Salesforce, et vérifier chaque chiffre avec elles.", 's2'),
         ("Trois : automatiser le suivi avec des agents, toujours avec une validation humaine.", 's3'),
         ("J'ai déjà piloté l'intégration ERP, CRM et point de vente de Salesforce chez Dray.", 'done'),
         ("Parlons-en. Je suis disponible immédiatement.", 'end')]


def measure(text):
    with tempfile.TemporaryDirectory() as t:
        open(f'{t}/c.json', 'w').write(json.dumps([{'at': 1.0, 'text': text}], ensure_ascii=False))
        out = subprocess.run(['python3', 'video-kit/src/voice.py', f'{t}/c.json', f'{t}/o.wav', '40', '--lang=fr'], capture_output=True, text=True).stdout
        return round(json.loads(out)['lines'][0]['ends'] - 1.0, 2)


cache = f'{D}/measured.json'
dur = json.load(open(cache)) if os.path.exists(cache) else None
if dur is None:
    dur = [measure(t) for t, _ in LINES]; json.dump(dur, open(cache, 'w'))
t = 0.7; sched = []
for (text, key), d in zip(LINES, dur):
    sched.append({'at': round(t, 2), 'end': round(t + d, 2), 'text': text, 'key': key}); t += d + (0.35 if key in ('hello', 'plan') else 0.5)
total = round(t + 1.2, 2)
json.dump([{'at': s['at'], 'text': s['text']} for s in sched], open(f'{D}/cues.json', 'w'), ensure_ascii=False)
src = open(f'{D}/page.html', encoding='utf-8').read()
open(f'{D}/video.html', 'w', encoding='utf-8').write(src.replace('/*SCHED*/', 'const SCHED=' + json.dumps(sched, ensure_ascii=False) + ',TOTAL=' + str(total) + ';'))
print('total', total, [(s['key'], s['at'], s['end']) for s in sched])
