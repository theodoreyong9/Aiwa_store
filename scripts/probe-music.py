#!/usr/bin/env python3
"""One-off probe (to be deleted): the track list of Incompetech and the page of an OpenGameArt track. Read only, a few requests, polite user agent."""
import json, re, sys, time, urllib.request

UA = 'Mozilla/5.0 (compatible; AiwaStoreProbe/1.0; +https://github.com/theodoreyong9/Aiwa_store)'


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=30) as r:
            return r.status, r.read(3000000).decode('utf-8', 'replace')
    except Exception as e:
        return getattr(e, 'code', 0) or 0, f'ERR {e}'[:200]


def esc(s):
    return s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def say(title, text):
    print(f'::notice title={title}::{esc(text[:3500])}')


if sys.argv[1] == 'incompetech':
    st, body = get('https://incompetech.com/music/royalty-free/pieces.json')
    say(f'inc pieces {st} {len(body)} bytes', body[:1200])
    try:
        d = json.loads(body)
        items = d if isinstance(d, list) else next(v for v in d.values() if isinstance(v, list))
        say(f'inc count {len(items)}', json.dumps(items[0], ensure_ascii=False)[:900] + ' ... ' + json.dumps(items[len(items) // 2], ensure_ascii=False)[:900])
        keys = sorted({k for it in items for k in it})
        say('inc keys', ' '.join(keys))
        feels = {}
        for it in items:
            feels[it.get('feel', it.get('Feel', '?'))] = feels.get(it.get('feel', it.get('Feel', '?')), 0) + 1
        say('inc feels', str(sorted(feels.items(), key=lambda x: -x[1])[:25]))
    except Exception as e:
        say('inc parse', f'ERR {e}')
else:
    st, page = get('https://opengameart.org/content/battle-theme-a')
    out = []
    for m in re.finditer(r'(sites/default/files[^"\'<>\s]+)', page):
        out.append(m.group(1))
    say(f'oga files {st}', ' '.join(list(dict.fromkeys(out))[:8]))
    i = page.find('field-name-field-art-licenses')
    say('oga licence block', re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', page[i:i + 700])) if i >= 0 else 'none')
    say('oga tags', ' '.join(re.findall(r'href="/art-search\?[^"]*keys=[^"]*"[^>]*>([^<]+)<', page)[:10]) + ' | ' + ' '.join(re.findall(r'taxonomy/term/\d+[^>]*>([^<]+)<', page)[:12]))
    say('oga title/author', ' '.join(re.findall(r'<h1[^>]*>([^<]+)<', page)[:1]) + ' | ' + ' '.join(re.findall(r'href="/users/[^"]+"[^>]*>([^<]+)<', page)[:2]))
