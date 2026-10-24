# Jean, quality max: the film of the remote, in the first person (« J'ai créé une télécommande sur Android… »), for the application to Nexthink.
# One page (page.html) holds the whole film: the 3D plates (plates.py) with the live screens laid on their corners, and the pages without a plate. Run from the repository root:
#   python3 docs/videos/jean-max/build.py [/tmp/jean/plates]     (then make.sh)
import json, subprocess, tempfile, os, sys
D = 'docs/videos/jean-max'; PL = sys.argv[1] if len(sys.argv) > 1 else '/tmp/jean/plates'
LINES = [("J'ai créé une télécommande sur Android.", 'remote', 1.3),
         ("À la fois manuelle, et vocale.", 'modes', 1.4),
         ("Elle pilote mes comptes GitHub, et Claude Code.", 'accounts', 1.2),
         ("Grâce à un cerveau Linux.", 'brain', 3.0),
         ("Elle crée et gère des sessions multiples.", 'sessions', 2.6),
         ("Elle peut même utiliser sa propre télécommande.", 'self', 1.6),
         ("Même écran éteint, elle continue de créer avec toi.", 'dark', 1.6),
         ("Dans un univers de créativité sans limite, où tu gardes la propriété, et où la distribution existe sans permission.", 'universe', 1.2),
         ("Je m'appelle Jean, et ma passion, c'est le service client.", 'me', 0.5),
         ("Je suis extrêmement efficace : vibe builder, Nexthinker.", 'eff', 0.5),
         ("Et j'aimerais consacrer le développement du cerveau de mon projet à l'intégration Nexthink, et à sa commercialisation.", 'nex', 0.4)]


def measure(text):
    with tempfile.TemporaryDirectory() as t:
        open(f'{t}/c.json', 'w').write(json.dumps([{'at': 1.0, 'text': text}], ensure_ascii=False))
        out = subprocess.run(['python3', 'video-kit/src/voice.py', f'{t}/c.json', f'{t}/o.wav', '60', '--lang=fr'], capture_output=True, text=True).stdout
        return round(json.loads(out)['lines'][0]['ends'] - 1.0, 2)


cache = f'{D}/measured.json'
dur = json.load(open(cache)) if os.path.exists(cache) else None
if dur is None or len(dur) != len(LINES):
    dur = [measure(t) for t, _, _ in LINES]; json.dump(dur, open(cache, 'w'))
t = 0.9; sched = []
for (text, key, gap), d in zip(LINES, dur):
    sched.append({'at': round(t, 2), 'end': round(t + d, 2), 'text': text, 'key': key}); t += d + gap
total = round(t + 3.4, 2)
json.dump([{'at': s['at'], 'text': s['text']} for s in sched], open(f'{D}/cues.json', 'w'), ensure_ascii=False)
corners = {}
for k in 'ABCD':
    f = f'{PL}/corners-{k}.json'
    corners[k] = json.load(open(f)) if os.path.exists(f) else {'phone': [[.4, .3], [.6, .3], [.6, .7], [.4, .7]], 'tv': [[.4, .3], [.6, .3], [.6, .6], [.4, .6]]}
src = open(f'{D}/page.html', encoding='utf-8').read()
for k in 'ABCD':
    src = src.replace(f'PLATE_{k}', f'file://{PL}/plate-{k}.png')
src = src.replace('/*SCHED*/', 'const SCHED=' + json.dumps(sched, ensure_ascii=False) + ',TOTAL=' + str(total) + ';').replace('/*CORNERS*/', 'const CORNERS=' + json.dumps(corners) + ';')
open(f'{D}/video.html', 'w', encoding='utf-8').write(src)
print('total', total, [(s['key'], s['at'], s['end']) for s in sched])
