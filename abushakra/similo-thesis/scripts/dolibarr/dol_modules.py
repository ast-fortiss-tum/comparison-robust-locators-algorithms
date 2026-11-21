#!/usr/bin/env python3
"""Enables the modules the study suites exercise on the installed dolibarr_scan: logs in as admin and follows the activation links of admin/modules.php.
usage: dol_modules.py [dir name, default dolibarr_scan]"""
import sys, re, urllib.request, urllib.parse, http.cookiejar, html
d = sys.argv[1] if len(sys.argv) > 1 else 'dolibarr_scan'
base = f'http://localhost:8888/dolibarr/{d}/htdocs/'
op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def get(u, data=None):
    try: return op.open(urllib.request.Request(base + u if not u.startswith('http') else u, urllib.parse.urlencode(data).encode() if data else None), timeout=120).read().decode('utf8', 'replace')
    except urllib.error.HTTPError as e: return e.read().decode('utf8', 'replace')
WANT = re.compile(r'^mod(Societe|Propale|Facture|Produit|Product|Tax|Holiday|Categorie|Agenda|Banque|Commande|Contrat|Fournisseur|Service|Expedition|Projet|Adherent|Stock|Don|Ficheinter|Mailing)$', re.I)
get('index.php', dict(username='admin', password='admin', loginfunction='loginfunction'))
done = []
for rnd in range(8):   # activating a module can reveal dependants
    page = get('admin/modules.php?mode=other' if False else 'admin/modules.php')
    links = re.findall(r'href="([^"]*admin/modules\.php\?[^"]*action=set[^"]*)"', page) or re.findall(r'href="([^"]*modules\.php\?[^"]*action=set[^"]*)"', page)
    n = 0
    for l in links:
        l = html.unescape(l); m = re.search(r'value=(\w+)', l)
        if not m or not WANT.match(m.group(1)): continue
        u = l if l.startswith('http') else ('http://localhost:8888' + l if l.startswith('/') else base + 'admin/' + l)
        get(u); done.append(m.group(1)); n += 1   # a link is offered only while the module is inactive, so a failed activation is retried next round
    if not n: break
print('activated:', ' '.join(done))
