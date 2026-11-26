import sys,re,html,glob,os; sys.path.insert(0,'/Users/stocco/repos/tools/phpfusion-work')
from fixtest import Fix,ROW
fx=Fix(1)
TAB={'Members':'User Admin','Shoutbox':'User Admin','Submissions':'User Admin','Main Settings':'System Admin','Site Links':'System Admin','Time and Date':'System Admin'}
REN={'Site Settings':'Main Settings','Member Management':'Members','Shoutbox Admin':'Shoutbox','Image Uploads':'Images'}
for f in sorted(glob.glob(fx.root+'/4-Admin/*.html')):
    test='4-Admin/'+os.path.basename(f)[:-5]
    if test.endswith('AdminTestSuite') or test.endswith('AdminLeftMenu'): continue
    rows=[[html.unescape(re.sub(r'<[^>]+>','',x)).strip() for x in m.groups()] for m in ROW.finditer(open(f,encoding='utf8',errors='ignore').read())]
    for i in range(len(rows)-1,-1,-1):           # high to low so indices stay valid
        c,t,v=rows[i]
        if t=='link=Admin Home':
            fx.rep(test,i,c,'link=Admin Panel',v,why='6.00.207 has no "Admin Home" link; the user panel link "Admin Panel" opens the admin index')
        elif t.startswith('link=') and t[5:] in REN:
            nm=REN[t[5:]]
            fx.rep(test,i,c,'link='+nm,v,why=f'admin icon is titled "{nm}" in 6.00.207')
            t='link='+nm
        if t.startswith('link=') and t[5:] in TAB and i>0 and rows[i-1][1]!='link='+TAB[t[5:]] and c=='clickAndWait' and rows[i-1][1].endswith('index.php'):
            fx.ins(test,i,'clickAndWait','link='+TAB[t[5:]],'',why='the icon is on another admin tab')
print('done')
