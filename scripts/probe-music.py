#!/usr/bin/env python3
"""One-off probe (to be deleted): where the audio file and the tags are on an OpenGameArt page, and the address of an Incompetech file. Read only, a few requests."""
import re, sys, time, urllib.request, urllib.parse

UA = 'Mozilla/5.0 (compatible; AiwaStoreProbe/1.0; +https://github.com/theodoreyong9/Aiwa_store)'


def req(url, method='GET'):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}, method=method), timeout=30) as r:
            return r.status, (r.read(2000000).decode('utf-8', 'replace') if method == 'GET' else dict(r.headers))
    except Exception as e:
        return getattr(e, 'code', 0) or 0, f'ERR {e}'[:200]


def esc(s):
    return s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def say(title, text):
    print(f'::notice title={title}::{esc(str(text)[:3500])}')


if sys.argv[1] == 'incompetech':
    for u in ['https://incompetech.com/music/royalty-free/mp3-royaltyfree/Sneaky%20Snitch.mp3', 'https://incompetech.com/music/royalty-free/music.html']:
        st, h = req(u, 'HEAD')
        say(f'inc head {st} {u}', {k: h[k] for k in h if k.lower() in ('content-type', 'content-length')} if isinstance(h, dict) else h)
else:
    st, page = req('https://opengameart.org/content/battle-theme-a')
    say('oga audio hrefs', ' '.join(dict.fromkeys(re.findall(r'(?:href|src)="([^"]+\.(?:mp3|ogg|wav|flac)[^"]*)"', page))))
    i = page.find('field-name-field-art-tags')
    say('oga tag block', re.sub(r'\s+', ' ', page[i:i + 600]) if i >= 0 else 'none')
    say('oga title', ' '.join(re.findall(r'<title>([^<]+)</title>', page)))
    i = page.find('field-name-field-art-files')
    say('oga files block', re.sub(r'\s+', ' ', page[i:i + 900]) if i >= 0 else 'none')
    time.sleep(10)
    st, lst = req('https://opengameart.org/art-search-advanced?keys=&field_art_type_tid%5B%5D=12&field_art_licenses_tid%5B%5D=4&sort_by=count&sort_order=DESC')
    say(f'oga cc0 list {st}', ' '.join(list(dict.fromkeys(re.findall(r'href="(/content/[^"#?]+)"', lst)))[:12]) + ' || pager: ' + ' '.join(re.findall(r'href="([^"]*page=\d+[^"]*)"', lst)[:3]))
