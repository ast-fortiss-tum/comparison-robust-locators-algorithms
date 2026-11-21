#!/usr/bin/env python3
"""Mapping scan: installs each Dolibarr release in turn (dir dolibarr_30, db dol_30), logs in as admin/admin and counts how many page-object locators of the
v1 and v30 WebDriver suites resolve on the login page and on the home/menu page. Writes scan_results.jsonl (one line per release). Needs the dol-* containers.
usage: scan_releases.py [release ...]     (default: every .tgz in the two Drive archives)"""
import sys, os, re, json, glob, time, subprocess, urllib.request, urllib.parse, http.cookiejar, zipfile, shutil
W = '/Users/stocco/repos/tools/dolibarr-work'; Z = '/Users/stocco/repos/tools/comparison-robust-locators-algorithms/web-apps-zip-files-20261118T115055Z-1-00%d.zip'
from selenium import webdriver
from selenium.webdriver.common.by import By
D = 'dolibarr_30'; ROOT = f'{W}/www/dolibarr/{D}'
def sh(c): return subprocess.run(c, shell=True, capture_output=True, text=True)
def fetch_tgz(rel):
    p = f'{W}/tgz/web-apps-zip-files/dolibarr/dolibarr-{rel}.tgz'
    if os.path.exists(p): return p
    for i in (1, 2):
        z = zipfile.ZipFile(Z % i)
        n = f'web-apps-zip-files/dolibarr/dolibarr-{rel}.tgz'
        if n in z.namelist(): z.extract(n, f'{W}/tgz'); return p
    return None
def install(rel):
    shutil.rmtree(ROOT, ignore_errors=True)
    p = '/Users/stocco/repos/tools/dolibarr-work/tgz/git/dolibarr-3eb8d31.tar.gz'
    if not p: return 'no archive'
    sh(f'cd {W}/www/dolibarr && tar -xzf {p} && mv dolibarr-3eb8d3105a2f8a6c886c8474220c4b58e77ce04c {D}')
    if not os.path.isdir(ROOT): return 'extract failed'
    os.makedirs(f'{ROOT}/documents', exist_ok=True)
    sh(f'grep -rli "type=innodb" {ROOT}/mysql {ROOT}/htdocs/install/mysql 2>/dev/null | while read f; do LC_ALL=C sed -i "" -E "s/[Tt][Yy][Pp][Ee][ ]*=[ ]*[Ii][Nn][Nn][Oo][Dd][Bb]/ENGINE=innodb/g" "$f"; done')
    open(f'{ROOT}/htdocs/conf/conf.php', 'w').close(); sh(f'chmod -R 777 {ROOT}/htdocs/conf {ROOT}/documents')
    sh('docker exec dol-db mysql -uroot -proot -e "DROP DATABASE IF EXISTS dol_30; "')
    names = os.listdir(f'{ROOT}/htdocs/install'); pre = 'etape' if 'etape1.php' in names else 'step'
    base = f'http://localhost:8888/dolibarr/{D}/htdocs/install/'
    cj = http.cookiejar.CookieJar(); op = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    def call(page, data=None):
        try: r = op.open(urllib.request.Request(base + page, urllib.parse.urlencode(data).encode() if data is not None else None), timeout=300); return r.read().decode('utf8', 'replace')
        except urllib.error.HTTPError as e: return e.read().decode('utf8', 'replace')
        except Exception as e: return 'ERR ' + str(e)
    def fields(body, target):
        m = re.search(r'(?is)<form[^>]*action="[^"]*%s[^"]*"[^>]*>(.*?)</form>' % target, body)
        return dict(re.findall(r'(?is)<input[^>]*name="([^"]+)"[^>]*value="([^"]*)"', m.group(1))) if m else {}
    call('check.php?testget=ok'); last = ''
    for dbtype in ('mysql', 'mysqli'):
        sh('docker exec dol-db mysql -uroot -proot -e "DROP DATABASE IF EXISTS dol_30; "'); open(f'{ROOT}/htdocs/conf/conf.php', 'w').close()
        b = call(f'{pre}1.php', dict(action='set', testpost='ok', selectlang='en_US', main_dir=f'/var/www/html/dolibarr/{D}/htdocs', main_data_dir=f'/var/www/html/dolibarr/{D}/documents',
            main_url=f'http://dol-web/dolibarr/{D}/htdocs', db_type=dbtype, db_host='dol-db', db_port='3306', db_name='dol_30', db_prefix='llx_', db_create_database='on',
            db_user='root', db_pass='root', db_user_root='root', db_pass_root='root', main_force_https=''))
        f2 = fields(b, f'{pre}2') or dict(action='set', selectlang='en_US')
        b = call(f'{pre}2.php', f2); f4 = fields(b, f'{pre}4') or fields(b, f'{pre}5')
        b = call(f'{pre}4.php', f4 or dict(action='set', selectlang='en_US', testpost='ok')); f5 = fields(b, f'{pre}5')
        d5 = dict(f5); d5.update(action=d5.get('action', 'set'), login='admin'); d5.update({'pass': 'admin', 'pass_verif': 'admin'})
        b = call(f'{pre}5.php', d5); last = re.sub(r'\s+', ' ', re.sub(r'<[^>]+>', ' ', b))[:200]
        ntab = sh('docker exec dol-db mysql -uroot -proot -N -e "select count(*) from information_schema.tables where table_schema=\'dol_30\'"').stdout.strip()
        if ntab.isdigit() and int(ntab) > 100:
            # no pre-configuration: the suite's own tests set up the company and the modules
            # v30 only: the suite never selects a company country, so the authors' app had a default one; preset just that setting (Italy)
            sh('''docker exec dol-db mysql -uroot -proot dol_30 -e "insert into llx_const(name,value,type,visible,entity) select 'MAIN_INFO_SOCIETE_COUNTRY', concat(rowid,':IT:Italy'), 'chaine', 0, 1 from llx_c_country where code='IT';"''')
            return 'ok(%s,%s tables)' % (dbtype, ntab)
    return 'install failed: ' + last
BY = {'id': By.ID, 'name': By.NAME, 'xpath': By.XPATH, 'linkText': By.LINK_TEXT, 'partialLinkText': By.PARTIAL_LINK_TEXT, 'css': By.CSS_SELECTOR, 'className': By.CLASS_NAME, 'tagName': By.TAG_NAME}
def locs(root, pages):
    out = {}
    for p in pages:
        f = glob.glob(f'{root}/**/pageobjects/{p}.java', recursive=True)[0]; t = open(f, encoding='utf8', errors='ignore').read()
        for m in re.finditer(r'@FindBy\(\s*(\w+)\s*=\s*"((?:[^"\\]|\\.)*)"\s*\)\s*(?:@\w+(?:\([^)]*\))?\s*)*(?:private|public|protected)?\s*(?:WebElement|List<WebElement>)\s+(\w+)\s*;', t):
            out[(p, m.group(3))] = (m.group(1), m.group(2).replace('\\"', '"'))
    return out
L = {v: {'index': locs(f'{W}/suites/{v}', ['IndexPage']), 'home': locs(f'{W}/suites/{v}', ['HomePage', 'MenuPage'])} for v in ('wd_v1', 'wd_v30')}
def count(drv, locd):
    ok = 0
    for (how, v) in locd.values():
        try: ok += bool(drv.find_elements(BY.get(how, By.XPATH), v))
        except Exception: pass
    return ok
def probe():
    res = {}
    drv = webdriver.Remote('http://localhost:4455', options=webdriver.ChromeOptions()); drv.implicitly_wait(0)
    try:
        drv.get(f'http://dol-web/dolibarr/{D}/htdocs/index.php'); time.sleep(1.5)
        for v in L: res[f'login_{v}'] = count(drv, L[v]['index'])
        pw = drv.find_elements(By.CSS_SELECTOR, "input[type='password']")
        if not pw: res['login'] = 'no password field'; return res
        txt = drv.find_elements(By.CSS_SELECTOR, "input[type='text']")
        (txt[0] if txt else pw[0]).send_keys('admin'); pw[0].send_keys('admin'); pw[0].submit(); time.sleep(3)
        res['login'] = 'ok' if 'login' not in drv.current_url.lower() else 'stayed on login'
        for v in L: res[f'home_{v}'] = count(drv, L[v]['home'])
        res['title'] = drv.title[:40]
    finally: drv.quit()
    return res
def order(n):
    m = re.match(r'dolibarr-(\d+(?:\.\d+)*)', n); return [int(x) for x in m.group(1).split('.')]
rels = sys.argv[1:]
if not rels:
    names = set()
    for i in (1, 2): names |= {n.split('/')[-1][len('dolibarr-'):-4] for n in zipfile.ZipFile(Z % i).namelist() if n.endswith('.tgz') and '/dolibarr/' in n}
    rels = sorted(names, key=lambda r: order('dolibarr-' + r))
done = set()
if os.path.exists(f'{W}/scan_results.jsonl'): done = {json.loads(l)['release'] for l in open(f'{W}/scan_results.jsonl')}
for rel in rels:
    pass
    t0 = time.time(); r = dict(release=rel)
    try:
        r['install'] = install(rel)
        pass
    except Exception as e: r['error'] = str(e)[:150]
    r['secs'] = round(time.time() - t0); print(json.dumps(r), flush=True); open(f'{W}/install_dbg.jsonl', 'a').write(json.dumps(r) + '\n')
pass
