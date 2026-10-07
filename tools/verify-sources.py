import hashlib, json, pathlib, sys
root = pathlib.Path(__file__).resolve().parent.parent
def blob(data): return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
for manifest_name, directory in [('v1.3-source-manifest.json','jev-chat-jarvis-v1.3'),('goutou-source-manifest.json','goutoujunshi')]:
 manifest = json.loads((root/'reference'/manifest_name).read_text(encoding='utf-8'))
 bad=[]; repaired=0
 for f in manifest['files']:
  p=root/'reference'/directory/f['path']; data=p.read_bytes()
  if blob(data)!=f['sha'] and '--repair-goutou' in sys.argv and directory=='goutoujunshi' and data.endswith(b'\n') and blob(data[:-1])==f['sha']:
   data=data[:-1]; p.write_bytes(data); repaired+=1
   if f['path'].startswith('references/'):
    (root/'android/app/src/main/assets/knowledge/goutoujunshi'/f['path']).write_bytes(data)
  if blob(data)!=f['sha']: bad.append(f['path'])
 print(directory,len(manifest['files']),'mismatches',len(bad),'repaired tool-added LF',repaired,flush=True)
 if bad: print(bad); raise SystemExit(1)
