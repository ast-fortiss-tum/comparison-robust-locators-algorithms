#!/usr/bin/env python3
"""Re-points the SetupPage module-activation locators of wd_vN at the row whose module name matches the field's meaning (identity-faithful repair, versions >= 3.3.0 with the common/other/interfaces tabs).
usage: fix_module_rows.py N [N ...]   (needs app dolibarr_N installed)"""
import sys, re
sys.argv_saved = sys.argv; 
exec(open('/Users/stocco/repos/tools/dolibarr-work/verify_module_rows.py').read().split("bad = 0")[0])
for N in [int(x) for x in sys.argv_saved[1:]]:
    f = f'{W}/run/wd_v{N}/src/main/java/pageobjects/SetupPage.java'; src = open(f, encoding='utf8').read(); pages = {}; changed = []
    def repl(m):
        xp, name = m.group(1), m.group(2)
        if name not in EXPECT or 'td[5]/a/img' not in xp: return m.group(0)
        mode, rx = EXPECT[name]
        if mode not in pages: pages[mode] = page(N, mode)
        t = pages[mode]; rows = t.xpath("//div[@id='id-right']/div/div[2]/table/tr") or t.xpath("//table[@id='undertopmenu']/tr/td[2]/div/div[2]/table/tr")
        hits = [i for i, r in enumerate(rows, 1) if r.xpath('count(td)') >= 5 and r.xpath('.//a/img') and re.search(rx, re.sub(r'\s+', ' ', r.xpath('string(td[2])')).strip())]
        if len(hits) != 1: print(f'v{N} {name}: {len(hits)} candidate rows {hits}, left as is'); return m.group(0)
        new = re.sub(r'tr\[\d+\](/td\[5\]/a/img)', f'tr[{hits[0]}]\\1', xp)
        if new != xp: changed.append((name, xp.split('tr[')[-1][:5], hits[0]))
        return m.group(0).replace(xp, new)
    out = re.sub(r'@FindBy\(xpath="([^"]*)"\)\s*private WebElement (\w+);', repl, src)
    open(f, 'w', encoding='utf8').write(out); print(f'v{N}: re-pointed {changed}')
