"""Edit a Selenese test in suites_repaired/vN and log the repair. usage as module:
   fx=Fix(1); fx.ins('2-LoggedOut/Login+Regiser',13,'clickAndWait','link=Please Try Again','', why='...'); fx.rep(test,i,cmd,target,value,why); fx.dele(test,i,why)
Indices are the replay step indices (0-based rows with three cells); apply several edits to one test from the highest index down."""
import re,html,os,json,time
W='/Users/stocco/repos/tools/phpfusion-work'
ROW=re.compile(r"<tr>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*<td[^>]*>(.*?)</td>\s*</tr>",re.S)
class Fix:
    def __init__(s,n): 
        s.n=n; s.base=f'{W}/suites_repaired/v{n}'; s.log=f'{W}/suites_repaired/repairs_v{n}.jsonl'
        s.root=[os.path.join(s.base,x) for x in os.listdir(s.base)][0]
    def _load(s,test):
        p=f'{s.root}/{test}.html'; t=open(p,encoding='utf8',errors='ignore').read(); return p,t,list(ROW.finditer(t))
    def _row(s,c,t,v): return "<tr>\n\t<td>%s</td>\n\t<td>%s</td>\n\t<td>%s</td>\n</tr>"%(c,html.escape(t,quote=False),html.escape(v,quote=False))
    def _w(s,p,t,op,test,i,old,new,why):
        open(p,'w',encoding='utf8').write(t)
        open(s.log,'a').write(json.dumps(dict(ts=time.strftime('%F %T'),op=op,test=test,i=i,old=old,new=new,why=why),ensure_ascii=False)+'\n')
    def show(s,test,lo=0,hi=999):
        for k,m in enumerate(ROW.finditer(s._load(test)[1])):
            if lo<=k<=hi: print(k,*[html.unescape(re.sub(r'<[^>]+>','',x)).strip() for x in m.groups()],sep=' | ')
    def rep(s,test,i,c,t,v,why=''):
        p,txt,rows=s._load(test); m=rows[i]; old=[html.unescape(re.sub(r'<[^>]+>','',x)).strip() for x in m.groups()]
        txt=txt[:m.start()]+s._row(c,t,v)+txt[m.end():]; s._w(p,txt,'rep',test,i,old,[c,t,v],why)
    def ins(s,test,i,c,t,v,why=''):   # insert BEFORE step i
        p,txt,rows=s._load(test); m=rows[i]
        txt=txt[:m.start()]+s._row(c,t,v)+"\n"+txt[m.start():]; s._w(p,txt,'ins',test,i,None,[c,t,v],why)
    def dele(s,test,i,why=''):
        p,txt,rows=s._load(test); m=rows[i]; old=[html.unescape(re.sub(r'<[^>]+>','',x)).strip() for x in m.groups()]
        txt=txt[:m.start()]+txt[m.end():]; s._w(p,txt,'del',test,i,old,None,why)
