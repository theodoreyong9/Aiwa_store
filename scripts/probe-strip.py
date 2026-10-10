#!/usr/bin/env python3
"""Prints the text of the main part of an HTML page (tags stripped), for the probes: probe-strip.py page.html [max-chars]"""
import re, sys
h = open(sys.argv[1], encoding='utf-8', errors='ignore').read()
m = re.search(r'<main.*?</main>', h, re.S) or re.search(r'<article.*?</article>', h, re.S)
body = m.group(0) if m else h
body = re.sub(r'<(script|style)[^>]*>.*?</\1>', '', body, flags=re.S)
body = re.sub(r'<(br|/p|/div|/li|/h[1-6]|/dt|/dd|/tr)[^>]*>', '\n', body)
body = re.sub(r'<[^>]+>', ' ', body)
body = re.sub(r'[ \t]+', ' ', body)
body = re.sub(r'\n\s*\n+', '\n', body)
print(body.strip()[:int(sys.argv[2]) if len(sys.argv) > 2 else 3000])
