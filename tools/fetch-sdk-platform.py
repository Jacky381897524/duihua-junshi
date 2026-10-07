"""Fallback mirror download, accepted only with Google's official platform SHA1."""
from pathlib import Path
import concurrent.futures,hashlib,json,tempfile,urllib.request,zipfile,time,filecmp,shutil
root=Path(__file__).resolve().parent.parent
target=root/'.toolchain/platform-35-mirror.zip'
parts=root/'.toolchain/platform-35-parts'; parts.mkdir(exist_ok=True)
url='https://mirrors.cloud.tencent.com/AndroidSDK/platform-35_r02.zip'
total=64273788; chunk=256*1024
def fetch(start):
 end=min(total-1,start+chunk-1); path=parts/str(start)
 if path.exists() and path.stat().st_size==end-start+1:return
 for attempt in range(4):
  try:
   with urllib.request.urlopen(urllib.request.Request(url,headers={'Range':f'bytes={start}-{end}'}),timeout=20) as r:
    assert r.status==206 and r.headers.get('Content-Range','').startswith(f'bytes {start}-{end}/')
    data=r.read(end-start+2)
   assert len(data)==end-start+1
   path.write_bytes(data); return
  except Exception:
   if attempt==3:raise
   time.sleep(attempt+1)
with concurrent.futures.ThreadPoolExecutor(8) as pool:list(pool.map(fetch,range(0,total,chunk)))
with target.open('wb') as f:
 for start in range(0,total,chunk):f.write((parts/str(start)).read_bytes())
assert hashlib.sha1(target.read_bytes()).hexdigest()=='0bb560a90a7a2cbd0dd8348224d518b638fe7949'
config=json.loads((root/'.toolchain/config.json').read_text())
dest=Path(config['sdk'])/'platforms/android-35'
extract=Path(config['active'])/'extract-platform-35-r02'
with zipfile.ZipFile(target) as z:
 for member in z.namelist():assert (extract/member).resolve().is_relative_to(extract.resolve())
 z.extractall(extract)
children=list(extract.iterdir()); source=children[0] if len(children)==1 and children[0].is_dir() else extract
def copy(source,target):
 if Path(target).exists() and filecmp.cmp(source,target,shallow=False):return target
 return shutil.copy2(source,target)
shutil.copytree(source,dest,dirs_exist_ok=True,copy_function=copy)
generated=dest/'package.xml'
if generated.exists() and 'android-35-ext' in generated.read_text():generated.unlink()
for item in config['installed']:
 if item['id']=='platforms;android-35':item.update(url=url,checksum='0bb560a90a7a2cbd0dd8348224d518b638fe7949')
(root/'.toolchain/config.json').write_text(json.dumps(config,indent=2))
print('SDK platform 35 r02 verified against official SHA1 and installed',flush=True)
