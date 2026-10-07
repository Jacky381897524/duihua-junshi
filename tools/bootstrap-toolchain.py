"""Fetch official build tools to a task-specific ASCII temp directory; keep downloads locally."""
import concurrent.futures, filecmp, hashlib, json, os, pathlib, shutil, tempfile, time, urllib.request, zipfile
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
CACHE = ROOT / '.toolchain'
ACTIVE = pathlib.Path(tempfile.gettempdir()) / 'jev-love-advisor-01a0f2f8'
BASE = 'https://dl.google.com/android/repository/'
CACHE.mkdir(exist_ok=True)
ACTIVE.mkdir(exist_ok=True)

def get(url):
    for attempt in range(3):
        try:
            request=urllib.request.Request(url,headers={'User-Agent':'JevLoveAdvisorBuild/1.0'})
            with urllib.request.urlopen(request, timeout=30) as r: return r.read()
        except Exception:
            if attempt==2: raise
            time.sleep(attempt+1)

def download(url, name, checksum=None, algorithm='sha256', chunk_bytes=32*1024):
    target = CACHE / name
    def valid():
        if not target.exists(): return False
        return not checksum or hashlib.new(algorithm, target.read_bytes()).hexdigest() == checksum
    if not valid():
        print('Downloading', name, flush=True)
        headers = {'User-Agent': 'JevLoveAdvisorBuild/1.0', 'Range': 'bytes=0-0'}
        with urllib.request.urlopen(urllib.request.Request(url, headers=headers), timeout=30) as r:
            cr = r.headers.get('Content-Range')
            total = int(cr.split('/')[-1]) if cr else int(r.headers['Content-Length'])
        if not cr: raise RuntimeError('Range downloads required for this connection: ' + name)
        with target.open('wb') as f: f.truncate(total)
        chunk = chunk_bytes
        def part(start):
            end = min(total-1, start+chunk-1)
            for attempt in range(3):
                try:
                    request = urllib.request.Request(url, headers={'User-Agent':headers['User-Agent'], 'Range':f'bytes={start}-{end}'})
                    with urllib.request.urlopen(request, timeout=30) as r:
                        if r.status != 206 or not r.headers.get('Content-Range', '').startswith(f'bytes {start}-{end}/'): raise RuntimeError('Bad Content-Range')
                        data = r.read(end-start+2)
                    if len(data) != end-start+1: raise RuntimeError('Incomplete range')
                    with target.open('r+b') as f: f.seek(start); f.write(data)
                    return
                except Exception:
                    if attempt == 2:
                        print('Range failed',name,start,flush=True)
                        raise
                    time.sleep(attempt+1)
        with concurrent.futures.ThreadPoolExecutor(8) as pool: list(pool.map(part, range(0,total,chunk)))
    if not valid(): raise RuntimeError('Checksum mismatch: ' + name)
    print('Verified', name, target.stat().st_size, flush=True)
    return target

def extract(archive, destination):
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for n in z.namelist():
            path = (destination / n).resolve()
            if not path.is_relative_to(destination.resolve()): raise RuntimeError('Invalid zip path')
        z.extractall(destination)

def main():
    xml = get(BASE + 'repository2-1.xml')
    (CACHE / 'repository.xml').write_bytes(xml)
    tree = ET.fromstring(xml)
    jobs = []
    for package, folder in [('platforms;android-35', 'platforms'), ('build-tools;35.0.0', 'build-tools'), ('platform-tools', '')]:
        candidates = [e for e in tree if e.tag.endswith('remotePackage') and e.attrib.get('path') == package]
        node = max(candidates, key=lambda e: int(e.findtext('revision/major','0')))
        archives = node.find('archives')
        archive = next(a for a in archives if a.findtext('host-os') in (None, 'windows'))
        complete = archive.find('complete')
        checksum = complete.find('checksum')
        url = complete.findtext('url')
        download_url = BASE + url + ('?download=1' if package == 'platforms;android-35' else '')
        jobs.append((package, download_url, url, checksum.text, checksum.attrib.get('type', 'sha1'), folder))
    package = json.loads((CACHE / 'jdk-package.json').read_text(encoding='utf-8'))
    jobs.append(('jdk', package['link'], package['name'], package['checksum'], 'sha256', 'jdk'))
    jobs.append(('gradle', 'https://repo.huaweicloud.com/gradle/gradle-8.9-bin.zip', 'gradle-8.9-bin.zip', 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab', 'sha256', 'gradle'))
    def install(job):
        ident, url, name, checksum, algorithm, folder = job
        archive = download(url, name, checksum, algorithm)
        temp = ACTIVE / ('extract-' + ident.replace(';', '-'))
        extract(archive, temp)
        children = list(temp.iterdir())
        src = children[0] if len(children) == 1 and children[0].is_dir() else temp
        if ident == 'jdk': dest = ACTIVE / 'jdk'
        elif ident == 'gradle': dest = ACTIVE / 'gradle'
        elif ident == 'platforms;android-35': dest = ACTIVE / 'sdk/platforms/android-35'
        elif ident == 'build-tools;35.0.0': dest = ACTIVE / 'sdk/build-tools/35.0.0'
        else: dest = ACTIVE / 'sdk/platform-tools'
        def copy_changed(source, target):
            if pathlib.Path(target).exists() and filecmp.cmp(source,target,shallow=False): return target
            return shutil.copy2(source,target)
        shutil.copytree(src, dest, dirs_exist_ok=True, copy_function=copy_changed)
        return {'id': ident, 'url': url, 'checksum': checksum, 'path': str(dest)}
    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
        installed = list(pool.map(install, jobs))
    licenses = ACTIVE / 'sdk/licenses'
    licenses.mkdir(exist_ok=True)
    for e in tree:
        if e.tag.endswith('license'):
            text = ''.join(e.itertext()).strip()
            (licenses/e.attrib['id']).write_text(hashlib.sha1(text.encode()).hexdigest()+'\n', encoding='utf-8')
    config = {'active': str(ACTIVE), 'java': str(ACTIVE/'jdk'), 'sdk': str(ACTIVE/'sdk'), 'gradle': str(ACTIVE/'gradle'), 'installed': installed}
    (CACHE/'config.json').write_text(json.dumps(config, indent=2), encoding='utf-8')
    print(json.dumps({'active': str(ACTIVE), 'packages': [j['id'] for j in installed]}), flush=True)

if __name__ == '__main__': main()
