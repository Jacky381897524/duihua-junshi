"""Prime a local Maven cache with checksum-verified official Google artifacts."""
import concurrent.futures, importlib.util, pathlib, re, urllib.request, xml.etree.ElementTree as ET
spec=importlib.util.spec_from_file_location('bootstrap',pathlib.Path(__file__).with_name('bootstrap-toolchain.py'))
bootstrap=importlib.util.module_from_spec(spec); spec.loader.exec_module(bootstrap)
root=pathlib.Path(__file__).resolve().parent.parent
urls=set()
cache=bootstrap.ACTIVE/'gradle-cache/caches/modules-2/files-2.1'
for group in ['com.google.mlkit','com.google.android.gms','androidx.core','com.android.tools.lint']:
 for pom in (cache/group).glob('*/*/*/*.pom'):
  try:
   doc=ET.fromstring(pom.read_bytes()); ns={'m':'http://maven.apache.org/POM/4.0.0'}
   packaging=doc.findtext('m:packaging',namespaces=ns) or 'jar'
   if packaging=='aar' or group=='com.android.tools.lint':
    artifact=pom.parents[2].name; version=pom.parents[1].name
    if any((pom.parents[1]).glob(f'*/{artifact}-{version}.{packaging}')):continue
    urls.add(f'https://dl.google.com/dl/android/maven2/{group.replace(".","/")}/{artifact}/{version}/{artifact}-{version}.{packaging}')
  except ET.ParseError: pass
for p in (root/'.superpowers/sdd/2026-10-02-love-advisor-android').glob('build-*.log'):
 urls.update(re.findall(r'(https://dl\.google\.com/dl/android/maven2/[^\s\x27\x22]+\.(?:jar|aar))',p.read_text(encoding='utf-8-sig',errors='replace')))
def fetch(url):
 rel=url.split('/maven2/')[1]; dest=bootstrap.ACTIVE/'maven'/rel; dest.parent.mkdir(parents=True,exist_ok=True)
 checksum=bootstrap.get(url+'.sha1').decode().strip()
 if not re.fullmatch('[a-fA-F0-9]{40}',checksum): raise RuntimeError('Bad official checksum')
 mirror='https://maven.aliyun.com/repository/google/'+rel
 cached=bootstrap.download(mirror,pathlib.Path(rel).name,checksum,'sha1',chunk_bytes=256*1024)
 dest.write_bytes(cached.read_bytes())
 stem=dest.stem
 for classifier in ['-windows','-linux','-osx']:
  if stem.endswith(classifier):stem=stem.removesuffix(classifier)
 pom_url=url.rsplit('/',1)[0]+'/'+stem+'.pom'; pom_dest=dest.parent/(stem+'.pom'); pom_dest.write_bytes(bootstrap.get(pom_url))
 print('Cached official artifact',rel,flush=True)
with concurrent.futures.ThreadPoolExecutor(3) as p: list(p.map(fetch,sorted(urls)))
