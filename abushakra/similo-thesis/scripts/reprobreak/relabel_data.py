"""Build seed docs (one JSON per pair) and highlighted screenshots for the ReproBreak relabelling page.
usage: make_data.py <similo-thesis dir> <plan.json> <out dir>"""
import csv, json, os, sys, collections
import cv2
root, plan_path, out = sys.argv[1:]
plan = {e['id']: e for e in json.load(open(plan_path))}
rows = [r for r in csv.DictReader(open(f'{root}/sites/reprobreak_manifest.csv'), delimiter=';') if r['status'] == 'ok']
agree = collections.defaultdict(int); algos = set()
for l in open(f'{root}/out/rb_eval.log'):
    if '@rb_' in l and not l.startswith('VISTA@'):
        a, s = l.split(',')[0].split('@'); algos.add(a); agree[s] += int(l.split(',')[2])
outside = 0
for r in rows:
    s = r['site']; e = plan[int(r['id'])]
    imgs = {}
    for side, date, xp in (('old', '2001-01-01', r['old_xpath']), ('new', '2001-01-02', r['new_xpath'])):
        els = json.load(open(f'{root}/sites/{s}/{date}/elements.json'))
        t = next(x for x in els if x['xpath']['chrome'] == xp)
        im = cv2.imread(f'{root}/sites/{s}/{date}/screenshot.png')
        H, W = im.shape[:2]
        (x, y), (w, h) = t['location'], t['dimension']
        inside = 0 <= x < W and 0 <= y < H
        if inside:
            cv2.rectangle(im, (x - 3, y - 3), (x + w + 3, y + h + 3), (40, 40, 230), 3)
        else:
            outside += 1
        scale = min(1.0, 1100 / W)
        im = cv2.resize(im, (int(W * scale), int(H * scale)), interpolation=cv2.INTER_AREA)
        cv2.imwrite(f'{out}/img/{s}-{side}.jpg', im, [cv2.IMWRITE_JPEG_QUALITY, 72])
        imgs[side] = dict(inside=inside, box=[round(x * scale), round(y * scale), round(w * scale), round(h * scale)],
                          size=[im.shape[1], im.shape[0]])
    repo = e['repo']
    doc = dict(site=s, breakId=e['id'], repo=repo, repoShort=repo.split('/')[1], framework=e['framework'],
               commit=e['commit'], parent=e['parent'], file=e['file'],
               oldLine=e['old_line'], newLine=e['new_line'], oldLocator=e['old_locator'], newLocator=e['new_locator'],
               oldTag=r['old_tag'], newTag=r['new_tag'], oldText=r['old_text'], newText=r['new_text'],
               oldXpath=r['old_xpath'], newXpath=r['new_xpath'],
               subset='same position' if r['old_xpath'] == r['new_xpath'] else 'moved',
               agree=agree[s], algorithms=len(algos), oldImg=imgs['old'], newImg=imgs['new'])
    json.dump(doc, open(f'{out}/seed/{s}.json', 'w'))
print(len(rows), 'pairs;', outside, 'targets outside their screenshot;', len(algos), 'algorithms')
