#!/usr/bin/env python3
"""For each distinct failing lookup of a suite run (fail_dump_vN/), prints the page and what could be the moved element: fields (name/id), notification/error boxes, matching links.
usage: repair_hint.py N"""
import sys, re
from lxml import html
D = f'/Users/stocco/repos/tools/dolibarr-work/fail_dump_v{sys.argv[1]}'
seen = set()
for l in open(D + '/index.txt'):
    i, url, by = l.rstrip('\n').split('\t'); key = (re.sub(r'\?.*', '', url), by)
    if key in seen: continue
    seen.add(key); t = html.parse(f'{D}/{i}.html')
    print(f'\n## {i} {key[0].split("htdocs/")[-1]}  {by}')
    kind, _, val = by.partition(': ')
    if kind in ('By.name', 'By.id', 'By.cssSelector') or 'selector' in kind:
        for e in t.xpath('//input[not(@type="hidden")]|//select|//textarea')[:40]:
            print('   field', e.getroottree().getpath(e).replace('/html/body/', ''), {k: v for k, v in e.attrib.items() if k in ('name', 'id', 'type', 'value')})
    elif kind == 'By.linkText':
        for e in t.xpath('//a')[:200]:
            tx = (e.text_content() or '').strip()
            if tx and (val.lower()[:4] in tx.lower() or tx.lower()[:4] in val.lower()): print('   link', e.getroottree().getpath(e).replace('/html/body/', ''), tx[:40], e.get('href', '')[:60])
    else:
        for e in t.xpath('//*[contains(@class,"jnotify") or contains(@class,"error") or contains(@class,"warning") or contains(@class,"ok")]')[:6]:
            print('   box', e.getroottree().getpath(e), e.get('class'), (e.text_content() or '').strip()[:70].replace('\n', ' '))
        for e in t.xpath('//input[@type="submit" or @type="image" or @type="button"]|//a[contains(@class,"butAction")]|//a[contains(@class,"butActionDelete")]')[:8]:
            print('   button', e.getroottree().getpath(e).replace('/html/body/', ''), {k: v for k, v in e.attrib.items() if k in ('name', 'id', 'value', 'class')}, (e.text_content() or '').strip()[:30])
