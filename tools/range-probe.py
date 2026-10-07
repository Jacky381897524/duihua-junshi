import concurrent.futures, time, urllib.request
urls = [
 ('builder-mirror','https://maven.aliyun.com/repository/google/com/android/tools/build/builder/8.7.3/builder-8.7.3.jar'),
 ('bundletool-mirror','https://maven.aliyun.com/repository/google/com/android/tools/build/bundletool/1.17.1/bundletool-1.17.1.jar'),
]
def probe(item):
 name,u=item; start=time.monotonic()
 try:
  with urllib.request.urlopen(urllib.request.Request(u,headers={'Range':'bytes=0-1023','User-Agent':'JevLoveAdvisorBuild/1.0'}),timeout=15) as r:
   print(name,r.status,r.headers.get('Content-Length'),r.headers.get('Content-Range'),r.read(1024) if name.endswith('sha') else len(r.read(1024)),round(time.monotonic()-start,1),flush=True)
 except Exception as e: print(name,type(e).__name__,str(e),flush=True)
with concurrent.futures.ThreadPoolExecutor(4) as p: list(p.map(probe,urls))
