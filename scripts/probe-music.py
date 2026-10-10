#!/usr/bin/env python3
"""One-off probe (to be deleted): reads the robots.txt and the licence / terms pages of a music source and prints, as GitHub notices, what they say about robots,
automation and commercial use. Read only, one request per page, polite user agent."""
import re, sys, urllib.request

UA = 'Mozilla/5.0 (compatible; AiwaStoreProbe/1.0; +https://github.com/theodoreyong9/Aiwa_store)'
SOURCES = {
    'pixabay': ['https://pixabay.com/robots.txt', 'https://pixabay.com/service/license-summary/', 'https://pixabay.com/service/terms/'],
    'incompetech': ['https://incompetech.com/robots.txt', 'https://incompetech.com/music/royalty-free/faq.html', 'https://incompetech.com/music/royalty-free/licenses/'],
    'fma': ['https://freemusicarchive.org/robots.txt', 'https://freemusicarchive.org/terms-of-use/'],
    'freesound': ['https://freesound.org/robots.txt', 'https://freesound.org/help/faq/', 'https://freesound.org/help/tos_web/'],
    'archive': ['https://archive.org/robots.txt', 'https://archive.org/about/terms.php'],
    'musopen': ['https://musopen.org/robots.txt', 'https://musopen.org/terms/'],
    'ccmixter': ['https://ccmixter.org/robots.txt', 'https://ccmixter.org/terms'],
    'opengameart': ['https://opengameart.org/robots.txt', 'https://opengameart.org/content/faq'],
}
KEY = re.compile(r'automat|scrap|crawl|\bbots?\b|spider|commercial|attribution|redistribut|api|download|content id|copyright claim|cc0|public domain|machine', re.I)


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=25) as r:
            return r.status, r.read(400000).decode('utf-8', 'replace')
    except Exception as e:
        code = getattr(e, 'code', None)
        return code or 0, f'ERR {e}'[:200]


def esc(s):
    return s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


for url in SOURCES[sys.argv[1]]:
    status, body = get(url)
    if url.endswith('robots.txt') and status == 200:
        lines = [l.strip() for l in body.splitlines() if l.strip() and not l.startswith('#')]
        out = ' | '.join(lines[:40])
    elif status == 200:
        text = re.sub(r'<(script|style)[\s\S]*?</\1>', ' ', body)
        text = re.sub(r'<[^>]+>', ' ', text)
        text = re.sub(r'\s+', ' ', text)
        sentences = re.split(r'(?<=[.!?])\s+', text)
        hits = [s[:260] for s in sentences if KEY.search(s)]
        out = ' || '.join(hits[:12])
    else:
        out = body[:200]
    print(f'::notice title={sys.argv[1]} {status} {url}::{esc(out[:3500])}')
