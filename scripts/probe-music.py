#!/usr/bin/env python3
"""One-off probe (to be deleted): how the three allowed music sources list their tracks. Read only, a few requests, polite user agent."""
import re, sys, time, urllib.request, urllib.parse

UA = 'Mozilla/5.0 (compatible; AiwaStoreProbe/1.0; +https://github.com/theodoreyong9/Aiwa_store)'


def get(url):
    try:
        with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': UA}), timeout=30) as r:
            return r.status, r.read(600000).decode('utf-8', 'replace')
    except Exception as e:
        return getattr(e, 'code', 0) or 0, f'ERR {e}'[:200]


def esc(s):
    return s.replace('%', '%25').replace('\r', '').replace('\n', '%0A')


def say(title, text):
    print(f'::notice title={title}::{esc(text[:3500])}')


which = sys.argv[1]
if which == 'incompetech':
    st, body = get('https://incompetech.com/music/royalty-free/licenses/')
    srcs = re.findall(r'src="([^"]+\.js[^"]*)"', body)
    say('inc scripts', f'{st} ' + ' '.join(srcs))
    urls = set(re.findall(r'["\'(]([^"\'()\s]*(?:catalog|json|index)[^"\'()\s]*)["\')]', body))
    say('inc urls in page', ' '.join(sorted(urls))[:1500])
    for s in srcs[:6]:
        u = urllib.parse.urljoin('https://incompetech.com/music/royalty-free/licenses/', s)
        st2, js = get(u)
        found = sorted(set(re.findall(r'["\']([^"\']*(?:catalog|\.json|\.php|api)[^"\']*)["\']', js)))[:25]
        say(f'inc js {st2} {u}', ' '.join(found))
        time.sleep(1)
elif which == 'archive':
    q = 'mediatype:audio AND licenseurl:("http://creativecommons.org/publicdomain/zero/1.0/") AND format:MP3'
    url = 'https://archive.org/advancedsearch.php?' + urllib.parse.urlencode({'q': q, 'fl[]': ['identifier', 'title', 'creator', 'licenseurl', 'downloads'], 'rows': 6, 'sort[]': 'downloads desc', 'output': 'json'}, doseq=True)
    st, body = get(url)
    say(f'archive search {st}', body[:2500])
    m = re.search(r'"identifier":\s*"([^"]+)"', body)
    if m:
        st, body = get(f'https://archive.org/metadata/{m.group(1)}')
        files = re.findall(r'"name":\s*"([^"]+\.mp3)"[^}]*?"size":\s*"?(\d+)', body)
        say(f'archive metadata {st} {m.group(1)}', f'{len(body)} bytes; mp3: {files[:4]}; licenseurl: {re.findall(chr(34)+"licenseurl"+chr(34)+":.*?,", body)[:1]}')
elif which == 'oga':
    st, body = get('https://opengameart.org/art-search-advanced?keys=&field_art_type_tid%5B%5D=12&sort_by=count&sort_order=DESC')
    links = re.findall(r'href="(/content/[^"#?]+)"', body)
    say(f'oga list {st}', ' '.join(list(dict.fromkeys(links))[:15]))
    lic = re.findall(r'<option value="(\d+)"[^>]*>([^<]*(?:CC|GPL|Public)[^<]*)</option>', body)
    say('oga licence filter ids', str(lic[:12]))
    time.sleep(10)
    if links:
        st, page = get('https://opengameart.org' + list(dict.fromkeys(links))[0])
        mp3 = re.findall(r'href="([^"]+\.(?:mp3|ogg|wav))"', page)
        lic = re.findall(r'(CC0|CC-BY[^<"]{0,12}|GPL[^<"]{0,8}|OGA-BY[^<"]{0,6})', page)
        say(f'oga item {st}', f'files {mp3[:4]} licences {lic[:6]} tags {re.findall(chr(114)+"el=.tag.[^>]*>([^<]+)<", page)[:8]}')
