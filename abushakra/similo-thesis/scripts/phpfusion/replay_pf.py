"""Replay the per-release Selenese suites (ICST16/AddressBook/<rel>) against the Dockerised release (abk-web) in a Selenium Chromium container.
Per step: ok/fail, resolved element (outerHTML, absolute xpath, bounding box), url, full page source. usage: replay.py <rel> [outdir]"""
import sys, re, json, os, glob, subprocess, time, html
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import Select
from selenium.common.exceptions import NoAlertPresentException, TimeoutException
SUITES=os.environ.get('SUITES','/Users/stocco/repos/tools/phpfusion-work/suites')
# Optional element capture (CAPTURE_JS = the benchmark's javascript directory): same candidates as bewt-work's CaptureDriver (all visible elements, SVG internals excluded)
SNAP_JS=None
if os.environ.get('CAPTURE_JS'):
    _ex=''.join(open(os.path.join(os.environ['CAPTURE_JS'],f),encoding='utf8').read()+'\n' for f in ('attribute_extractor.js','css_attribute_extractor.js','xpath_extractor.js','css_selector_extractor.js','direct_text_extractor.js','elements_extractor.js'))
    SNAP_JS=_ex+("var full=arguments[0];var els=Array.prototype.slice.call(arguments,1);"
      "var skip={SCRIPT:1,STYLE:1,HEAD:1,META:1,LINK:1,TITLE:1,NOSCRIPT:1,TEMPLATE:1,HTML:1,BR:1,BASE:1};"
      "function inSvg(el){return el.namespaceURI==='http://www.w3.org/2000/svg'&&el.tagName.toLowerCase()!=='svg';}"
      "var cand=[];if(full){document.querySelectorAll('*').forEach(function(el){if(!skip[el.tagName.toUpperCase()]&&!inSvg(el)&&elementIsVisible(el))cand.push(element_extractor(el));});}"
      "var t=els.map(function(el){return{visible:elementIsVisible(el),inSvg:inSvg(el),element:element_extractor(el)};});"
      "return JSON.stringify({scroll:[Math.round(scrollX),Math.round(scrollY)],viewport:[innerWidth,innerHeight],elements:cand,targets:t});")
rel=sys.argv[1]; out=sys.argv[2] if len(sys.argv)>2 else '/Users/stocco/repos/tools/phpfusion-work/out'
dbname=f'pf_v{rel}'; root=f'http://pf-web/pf_v{rel}/'
SD=glob.glob(f'{SUITES}/v{rel}/*')[0]
JS_XPATH="""function(e){var p=[];for(;e&&e.nodeType==1;e=e.parentNode){var i=1,s=e.previousSibling;for(;s;s=s.previousSibling)if(s.nodeType==1&&s.nodeName==e.nodeName)i++;p.unshift(e.nodeName.toLowerCase()+'['+i+']');}return '/'+p.join('/');}"""
def reset_db():
    subprocess.run(f'docker exec pf-db mysql -uroot -proot -e "DROP DATABASE IF EXISTS {dbname}; CREATE DATABASE {dbname} CHARACTER SET utf8;" 2>/dev/null',shell=True)
    open(f'/Users/stocco/repos/tools/phpfusion-work/www/pf_v{rel}/config.php','w').close()   # empty config: the suite's install tests (setup.php) create tables and admin
def parse(f):
    t=open(f,encoding='utf8',errors='ignore').read()
    return [(html.unescape(re.sub(r'<[^>]+>','',a)).strip(),html.unescape(re.sub(r'<[^>]+>','',b)).strip(),html.unescape(re.sub(r'<[^>]+>','',c)).strip()) for a,b,c in re.findall(r"<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>",t,re.S)]
def find(d,loc):
    last=None
    for k in range(4):
        try: return find1(d,loc)
        except Exception as ex:
            last=ex
            if isinstance(ex,ValueError): break
            time.sleep(1.0)
    raise last
def find1(d,loc):
    m=re.match(r'(\w+)=(.*)$',loc,re.S)
    if loc.startswith('/') or loc.startswith('('): return d.find_element(By.XPATH,loc)
    if loc.startswith('document.') or loc.startswith('dom='):
        e=d.execute_script('return '+(loc[4:] if loc.startswith('dom=') else loc))
        if e is None: raise Exception('dom locator not found: '+loc)
        return e
    if m is None: raise ValueError('locator '+loc)
    k,v=m.group(1),m.group(2)
    if k=='xpath': return d.find_element(By.XPATH,v)
    if k=='css': return d.find_element(By.CSS_SELECTOR,v)
    if k=='name': return d.find_element(By.NAME,v.split(' ')[0] if ' ' in v and 'value=' in v else v)
    if k=='id': return d.find_element(By.ID,v)
    if k=='link': return d.find_element(By.LINK_TEXT,re.sub(r'^exact:','',v))
    if k=='identifier':
        try: return d.find_element(By.ID,v)
        except Exception: return d.find_element(By.NAME,v)
    raise ValueError('locator '+loc)
def open_url(d,u):
    m=re.search(r'PHP-Fusion[\d.]+/(.*)$',u)
    p=m.group(1) if m else u.split('/',3)[-1]
    p=re.sub(r'^php-files/','',p); p=re.sub(r'^html/fusion_admin/','administration/',p); p=re.sub(r'^html/','',p); p=re.sub(r'^fusion_admin/','administration/',p); p=re.sub(r'^fusion_forum/','forum/',p)
    d.get(root+p)
suite=[]
for sd in os.environ.get('ORDER','1-InstallTestSuite,2-LoggedOut,3-LoggedIn,4-Admin').split(','):
    sf=[x for x in os.listdir(f'{SD}/{sd}') if x.endswith('TestSuite')]
    if not sf: continue
    for a in re.findall(r'<a href="([^"]+)"',open(f'{SD}/{sd}/{sf[0]}',encoding='utf8',errors='ignore').read()):
        suite.append(f'{sd}/'+(a if a.endswith('.html') else a+'.html'))
# dependency-driven order: install, login (session), admin (creates the content), then the content-reading tests
def _key(t):
    d,n=t.split('/')
    if d.startswith('1-'): return 0
    if n.startswith('Login+Regiser'): return 1
    if d.startswith('4-'): return 2
    return 3 if d.startswith('2-') else 4
suite=[t for _,t in sorted(((_key(t),t) for t in suite),key=lambda x:x[0])] if os.environ.get('REORDER') else suite
opts=webdriver.ChromeOptions(); opts.add_argument('--window-size=1280,1600'); opts.set_capability('unhandledPromptBehavior','ignore')
d=webdriver.Remote('http://localhost:'+os.environ.get('SEL_PORT','4448'),options=opts); d.implicitly_wait(2)
SNAPD=f'/Users/stocco/repos/tools/phpfusion-work/snap/{rel}'; os.makedirs(SNAPD,exist_ok=True); seq=0
if os.environ.get('RESTORE'):   # restore DB + config.php saved after an earlier passing test
    _r=os.environ['RESTORE']
    subprocess.run(f'docker exec pf-db mysql -uroot -proot -e "DROP DATABASE IF EXISTS {dbname}; CREATE DATABASE {dbname} CHARACTER SET utf8;" 2>/dev/null',shell=True)
    subprocess.run(f'docker exec -i pf-db mysql -uroot -proot {dbname} < {_r}.sql 2>/dev/null; cp {_r}.config.php /Users/stocco/repos/tools/phpfusion-work/www/pf_v{rel}/config.php',shell=True)
    os.environ['NORESET']='1'
if not os.environ.get('NORESET'): reset_db()
res=[]
subprocess.run(f'cp /Users/stocco/repos/tools/phpfusion-work/seed/test.php /Users/stocco/repos/tools/phpfusion-work/www/pf_v{rel}/test.php',shell=True)   # fixture seed the suites open (replacement for the authors\' test.php)
if os.environ.get('NORESET'):   # continue on the current app state: log in as admin again
    d.get(root+'index.php')
    if not d.find_elements(By.LINK_TEXT,'Logout'):
        try: d.find_element(By.NAME,'user_name').send_keys('mouna'); d.find_element(By.NAME,'user_pass').send_keys('Maths2069'); d.find_element(By.NAME,'login').click(); time.sleep(1)
        except Exception as exl: print('login failed',exl)
for rel_path in suite:
    f=f'{SD}/{rel_path}'
    if not os.path.exists(f): res.append((rel_path,'missing')); continue
    name=os.path.basename(os.path.dirname(f))[:1]+'_'+os.path.basename(f)[:-5]
    if os.environ.get('ONLY') and name not in os.environ['ONLY'].split(','): continue
    if name in os.environ.get('SKIP','').split(','): continue   # diagnostic only: skip named tests
    od=f'{out}/{rel}/{name}'; os.makedirs(od,exist_ok=True)
    DBV={('name=db_host','cse.unl.edu'):'pf-db',('name=db_user','mouna'):'root',('name=db_pass','Maths2069'):'root',('name=db_name','mouna'):dbname}
    steps=[(c.split()[-1] if c.split() else c,t,(DBV.get((t,v),v) if c.split() and c.split()[-1]=='type' else v)) for c,t,v in parse(f)]
    status='pass'; log=[]
    for i,(cmd,tg,val) in enumerate(steps):
        rec=dict(i=i,cmd=cmd,target=tg,value=val,ok=True)
        try:
            if cmd=='open': open_url(d,tg)
            elif cmd in('click','clickAndWait','type','sendKeys','select','selectAndWait','waitForElementPresent'):
                for _ in range(20):
                    try:
                        if d.execute_script('return document.readyState')=='complete': break
                    except Exception: pass
                    time.sleep(0.25)
                e=find(d,tg)
                rec['xpath']=d.execute_script('return ('+JS_XPATH+')(arguments[0])',e); rec['outer']=e.get_attribute('outerHTML')[:600]; rec['rect']=e.rect
                rec['tag']=e.tag_name; rec['text']=(e.text or '')[:200]
                try:
                    open(f'{od}/pre_{i:02d}.html','w',encoding='utf8').write(d.page_source)
                    d.execute_script("arguments[0].scrollIntoView({block:'center',inline:'center'});",e)
                    if SNAP_JS:   # clean screenshot and element snapshot in the same scroll state, before the highlight changes the element
                        d.save_screenshot(f'{od}/clean_{i:02d}.png')
                        __import__('gzip').open(f'{od}/snap_{i:02d}.json.gz','wt',encoding='utf8').write(d.execute_script(SNAP_JS,True,e))
                    d.execute_script("arguments[0].scrollIntoView({block:'center',inline:'center'});arguments[0].setAttribute('data-abk-old-style',arguments[0].getAttribute('style')||'');arguments[0].style.outline='4px solid #ff2d55';arguments[0].style.outlineOffset='3px';",e)
                    d.save_screenshot(f'{od}/shot_{i:02d}.png')
                    d.execute_script("var o=arguments[0].getAttribute('data-abk-old-style');arguments[0].removeAttribute('data-abk-old-style');if(o)arguments[0].setAttribute('style',o);else arguments[0].removeAttribute('style');",e)
                except Exception as exs: rec['shot_error']=str(exs)[:100]
                try: e=find(d,tg)   # the page may have navigated while the screenshot was taken
                except Exception: pass
                # Selenium IDE semantics: click works through the DOM even on hidden elements (the live search filter of 5.x hides rows).
                if cmd.startswith('click'):
                    try: e.click()
                    except Exception as exc:
                        if 'not interactable' in str(exc) or 'intercepted' in str(exc): d.execute_script('arguments[0].click()',e); rec['js_click']=True
                        else: raise
                elif cmd=='type': e.clear(); e.send_keys(val)   # key events fire (live search filter), as the suites expect
                elif cmd=='sendKeys': e.send_keys(val.replace('${KEY_ENTER}','\n'))
                elif cmd.startswith('select'):
                    s=Select(e); v=val
                    if v.startswith('label='): s.select_by_visible_text(v[6:])
                    elif v.startswith('value='): s.select_by_value(v[6:])
                    elif v.startswith('index='): s.select_by_index(int(v[6:]))
                    else: s.select_by_visible_text(v)
            elif cmd=='assertAlert':
                try:
                    from selenium.webdriver.support.ui import WebDriverWait
                    from selenium.webdriver.support import expected_conditions as EC
                    a=WebDriverWait(d,3).until(EC.alert_is_present()); rec['alert']=a.text; a.accept()
                except TimeoutException: raise Exception('no alert')
            elif cmd=='assertText':
                e=find(d,tg); rec['text']=e.text
            elif cmd=='setSpeed': pass
            elif cmd=='pause': time.sleep(min(int(tg or 0),5000)/1000.0)
            elif cmd=='goBackAndWait': d.back()
            elif cmd=='assertConfirmation':
                try:
                    from selenium.webdriver.support.ui import WebDriverWait
                    from selenium.webdriver.support import expected_conditions as EC
                    a=WebDriverWait(d,3).until(EC.alert_is_present()); rec['alert']=a.text; a.accept()
                except TimeoutException: raise Exception('no confirmation')
            else: rec['skipped']=True
        except Exception as ex:
            rec['ok']=False; rec['error']=str(ex)[:300].split('\n')[0]; status='fail'
            try: d.switch_to.alert.accept()
            except Exception: pass
        if cmd in('click','clickAndWait','selectAndWait','sendKeys'): time.sleep(0.6)
        nxt=steps[i+1][0] if i+1<len(steps) else ''
        if rec['ok'] and not nxt.startswith('assert') and not nxt.startswith('verify'):   # Selenium IDE accepts confirm dialogs on its own
            for _k in range(3):
                try: al=d.switch_to.alert; rec['auto_alert']=al.text; al.accept(); time.sleep(0.5)
                except Exception: break
        if cmd in('click','clickAndWait','selectAndWait','sendKeys'): pass   # let a submit start navigating before waiting for load
        for _ in range(20):
            try:
                if d.execute_script('return document.readyState')=='complete': break
            except Exception: break
            time.sleep(0.25)
        try:
            rec['url']=d.current_url; last_url=rec['url']
            open(f'{od}/step_{i:02d}.html','w',encoding='utf8').write(d.page_source)
        except Exception as ex2:
            rec['url']=locals().get('last_url',''); rec['pending_alert']=True
        log.append(rec)
        if not rec['ok']: break
    json.dump(log,open(f'{od}/steps.json','w'),indent=1)
    res.append((name,status,len(log),len(steps)))
    if name.endswith('Login+Regiser'):   # session setup: the test's own final login step is never reached when registration fails
        try:
            d.get(root+'index.php')
            if not d.find_elements(By.LINK_TEXT,'Logout'):
                d.find_element(By.NAME,'user_name').send_keys('mouna'); d.find_element(By.NAME,'user_pass').send_keys('Maths2069'); d.find_element(By.NAME,'login').click(); time.sleep(1)
        except Exception as exl: print('harness login failed',exl)
    if status=='pass' and os.environ.get('SNAP'):
        seq+=1; _b=f'{SNAPD}/{name}'
        subprocess.run(f'docker exec pf-db mysqldump -uroot -proot {dbname} > "{_b}.sql" 2>/dev/null; cp /Users/stocco/repos/tools/phpfusion-work/www/pf_v{rel}/config.php "{_b}.config.php"',shell=True)
    print(rel,name,status,f'{len(log)}/{len(steps)}',(log[-1].get('error','') if status=='fail' else ''),flush=True)
d.quit()
json.dump(res,open(f'{out}/{rel}/summary.json','w'))
