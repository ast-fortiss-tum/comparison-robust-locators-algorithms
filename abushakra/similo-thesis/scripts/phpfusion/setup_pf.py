"""Deploy PHP-Fusion SIR version N (1..41): unzip release, generate config.php + SQL with the SIR nsetup.php (PHP 5.6 container), create DB pf_vN.
Admin = mouna / Maths2069, email mouna@cse.unl.edu (as the Selenese suites expect). usage: setup_pf.py N [N...]"""
import sys,os,subprocess,shutil,re,glob
W='/Users/stocco/repos/tools/phpfusion-work'
rels={int(v[1:]):r for v,r in (l.split() for l in open(f'{W}/rels.txt'))}
def sh(c): return subprocess.run(c,shell=True,capture_output=True,text=True)
for n in map(int,sys.argv[1:]):
    r=rels[n]; src=f'{W}/src/v{n}'
    if not os.path.isdir(src): sh(f'unzip -oq {W}/dl/{r}.zip -d {src}')
    web=glob.glob(f'{src}/php-files')[0] if glob.glob(f'{src}/php-files') else glob.glob(f'{src}/*/php-files')[0]
    g=f'{W}/gen/v{n}'; shutil.rmtree(g,ignore_errors=True); os.makedirs(g)
    for f in glob.glob(f'{W}/inst/phpfusion/scripts/SETUPS/v{n}/*.php'): shutil.copy(f,g)
    ns=open(f'{g}/nsetup.php',encoding='utf8',errors='ignore').read().replace('you@yourdomain.com','mouna@cse.unl.edu')
    open(f'{g}/nsetup.php','w',encoding='utf8').write(ns)
    p=sh(f'docker run --rm -v {g}:/g -w /g abk-php:5.6 php nsetup.php pf-db root root pf_v{n} "" mouna Maths2069'); 
    if p.returncode: print('nsetup failed',n,p.stdout,p.stderr); continue
    dst=f'{W}/www/pf_v{n}'; shutil.rmtree(dst,ignore_errors=True); shutil.copytree(web,dst)
    shutil.copy(f'{g}/nconfig.php',f'{dst}/config.php')
    for root_,_,fs in os.walk(dst):   # environment shim: MySQL >=5.5 dropped the TYPE= syntax
        for f_ in fs:
            if f_.endswith('.php'):
                fp=os.path.join(root_,f_); t=open(fp,encoding='latin-1').read(); t2=re.sub(r'TYPE ?= ?MyISAM','ENGINE=MyISAM',t)
                if t2!=t: open(fp,'w',encoding='latin-1').write(t2)
    sh(f'chmod -R a+rwX {dst}')
    sh(f'docker exec pf-db mysql -uroot -proot -e "DROP DATABASE IF EXISTS pf_v{n}; CREATE DATABASE pf_v{n} CHARACTER SET utf8;"')
    q=sh(f'docker exec -i pf-db mysql -uroot -proot --force pf_v{n} < {g}/eff_phpfusion.sql')
    c=sh(f'curl -sL -m 30 -o /dev/null -w "%{{http_code}}" http://localhost:8082/pf_v{n}/')
    print('ready',n,r,c.stdout,'sql errors:',len([l for l in q.stderr.splitlines() if 'ERROR' in l]))
