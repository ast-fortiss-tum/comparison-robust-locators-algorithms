"""Deploy PHP-Fusion SIR version N (release zip -> www/pf_vN) without DB content; the suite's own install tests create tables.
Shim: MySQL >=5.5 dropped TYPE=MyISAM (-> ENGINE=MyISAM). usage: deploy_pf.py N [N..]"""
import sys,os,subprocess,shutil,re,glob
W='/Users/stocco/repos/tools/phpfusion-work'
rels={int(v[1:]):r for v,r in (l.split() for l in open(f'{W}/rels.txt'))}
for n in map(int,sys.argv[1:]):
    r=rels[n]; src=f'{W}/src/v{n}'
    if not os.path.isdir(src): subprocess.run(f'unzip -oq {W}/dl/{r}.zip -d {src}',shell=True)
    c=glob.glob(f'{src}/php-files')+glob.glob(f'{src}/*/php-files')+glob.glob(f'{src}/files')+glob.glob(f'{src}/*/files')
    if not c: print('NO php-files in',n,os.listdir(src)[:10]); continue
    dst=f'{W}/www/pf_v{n}'; shutil.rmtree(dst,ignore_errors=True); shutil.copytree(c[0],dst)
    for root_,_,fs in os.walk(dst):
        for f_ in fs:
            if f_.endswith('.php'):
                fp=os.path.join(root_,f_); t=open(fp,encoding='latin-1').read(); t2=re.sub(r'TYPE ?= ?MyISAM','ENGINE=MyISAM',t)
                if t2!=t: open(fp,'w',encoding='latin-1').write(t2)
    open(f'{dst}/config.php','w').close()
    subprocess.run(f'chmod -R a+rwX {dst}',shell=True)
    print('deployed',n,r,os.listdir(dst)[:6])
