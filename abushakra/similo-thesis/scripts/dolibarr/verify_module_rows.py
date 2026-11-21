#!/usr/bin/env python3
"""Identity check of the SetupPage module-activation locators: for each version N (app dolibarr_N installed), resolve the PO xpath on the modules page and compare the module name in that row with the field's meaning."""
import sys, re, subprocess, http.cookiejar, urllib.request, urllib.parse
from lxml import html
W = '/Users/stocco/repos/tools/dolibarr-work'
EXPECT = {'third_parties': ('common', r'Third part'), 'accounting': ('common', r'Accounting'), 'leave_requests': ('common', r'Holiday|Leave'), 'members': ('common', r'Members'), 'products': ('common', r'^Products'),
          'cron': ('other', r'Cron|Scheduled'), 'logs': ('other', r'Log|Syslog'), 'margins': ('other', r'Margin'), 'resource': ('other', r'Categor'), 'tags': ('other', r'Bookmark'),
          'external': ('interfaces', r'External|site'), 'ftp': ('interfaces', r'FTP'), 'paypal': ('interfaces', r'Pay[Pp]al'), 'lpdap': ('interfaces', r'LDAP')}
def page(N, mode):
    cj = http.cookiejar.CookieJar(); op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj)); B = f'http://localhost:8888/dolibarr/dolibarr_{N}/htdocs/'
    op.open(urllib.request.Request(B + 'index.php', urllib.parse.urlencode(dict(username='admin', password='admin', loginfunction='loginfunction')).encode()), timeout=60).read()
    for m in ({'common': ['common', 'base', '']}[mode] if mode == 'common' else [mode]):
        try: return html.fromstring(op.open(B + 'admin/modules.php' + (f'?mode={m}' if m else ''), timeout=60).read())
        except Exception: pass
bad = 0
for N in [int(x) for x in sys.argv[1:]]:
    src = open(f'{W}/run/wd_v{N}/src/main/java/pageobjects/SetupPage.java', encoding='utf8').read(); pages = {}
    for m in re.finditer(r'@FindBy\(xpath="([^"]*)"\)\s*private WebElement (\w+);', src):
        xp, name = m.groups()
        if name not in EXPECT or 'td[5]/a/img' not in xp: continue
        mode, rx = EXPECT[name]
        if mode not in pages: pages[mode] = page(N, mode)
        t = pages[mode]; rel = re.sub(r"^\.//\*\[@id='[\w-]+'\]", "//div[@id='id-right']", xp) if "id-right" in xp else re.sub(r"^\.//\*\[@id='undertopmenu'\]/tbody/tr/td\[2\]", "//table[@id='undertopmenu']/tbody/tr/td[2]", xp)
        rel = rel.replace("//div[@id='id-right']/div/div", "//div[@id='id-right']/div/div")
        rel = rel.replace('/tbody', '')   # raw HTML has no tbody (browsers insert it)
        r = t.xpath(rel) or t.xpath(rel.replace("//div[@id='id-right']", "//table[@id='undertopmenu']/tr/td[2]"))
        r = r or []; nm = re.sub(r'\s+', ' ', r[0].xpath('ancestor::tr[1]')[0].xpath('string(td[2])')).strip()[:28] if r else None
        ok = bool(nm and re.search(rx, nm)); bad += (not ok)
        print(f'v{N:<2} {name:14} {mode:10} -> {nm!s:30} {"ok" if ok else "MISMATCH"}')
print('mismatches:', bad)
