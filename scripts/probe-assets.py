#!/usr/bin/env python3
"""One-off probe (to be deleted): what the Poly Haven API gives (models, hdris, textures), its licence and its rules for programs. Read only, a few requests."""
import json, re, sys, urllib.request

UA = 'aiwa-asset-research (+https://github.com/theodoreyong9/Aiwa_store)'


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=30) as r:
            return r.status, r.read(3000000).decode('utf-8', 'replace')
    except Exception as e:
        return getattr(e, 'code', 0) or 0, f'ERR {e}'[:200]


def esc(s):
    return s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def say(title, text):
    print(f'::notice title={title}::{esc(str(text)[:3500])}')


for t in ('models', 'hdris', 'textures'):
    st, body = get(f'https://api.polyhaven.com/assets?t={t}')
    try:
        d = json.loads(body)
        k = next(iter(d))
        say(f'ph {t} {st} count={len(d)}', f'{k}: {json.dumps(d[k])[:700]}')
        if t == 'models':
            first = next(iter(d))
            st2, i = get(f'https://api.polyhaven.com/info/{first}')
            say(f'ph info {first}', i[:900])
            st3, f = get(f'https://api.polyhaven.com/files/{first}')
            try:
                fj = json.loads(f)
                say('ph files keys', json.dumps({k2: (list(v.keys())[:6] if isinstance(v, dict) else str(v)[:60]) for k2, v in fj.items()})[:900])
                g = fj.get('gltf', {})
                res = next(iter(g)) if g else None
                say('ph gltf entry', json.dumps(g.get(res))[:900] if res else 'none')
            except Exception as e:
                say('ph files', f'{st3} {f[:200]} {e}')
        cats = {}
        for v in d.values():
            for c in v.get('categories', []): cats[c] = cats.get(c, 0) + 1
        say(f'ph {t} categories', json.dumps(sorted(cats.items(), key=lambda x: -x[1])[:25]))
    except Exception as e:
        say(f'ph {t} {st}', f'{body[:300]} {e}')
for url in ('https://polyhaven.com/license', 'https://polyhaven.com/our-api'):
    st, body = get(url)
    text = re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', re.sub(r'<(script|style)[\s\S]*?</\1>', ' ', body)))
    hits = [s[:240] for s in re.split(r'(?<=[.!?])\s+', text) if re.search(r'CC0|attribution|API|user.?agent|rate|scrap|automat|commercial|redistribut|AI', s, re.I)]
    say(f'ph page {st} {url}', ' || '.join(hits[:12]))
