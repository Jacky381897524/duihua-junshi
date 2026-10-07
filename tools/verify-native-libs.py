"""Verify APK zip offsets and ARM64 ELF PT_LOAD alignment without installing the APK."""
import hashlib,json,pathlib,struct,sys,zipfile
apk=pathlib.Path(sys.argv[1]); report={'apk':apk.name,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'libraries':[]}
with apk.open('rb') as stream,zipfile.ZipFile(apk) as archive:
 for item in archive.infolist():
  if not item.filename.startswith('lib/') or not item.filename.endswith('.so'):continue
  stream.seek(item.header_offset); header=stream.read(30); name,extra=struct.unpack_from('<HH',header,26)
  offset=item.header_offset+30+name+extra
  data=archive.read(item); assert data[:4]==b'\x7fELF' and data[4]==2 and data[5]==1
  machine=struct.unpack_from('<H',data,18)[0]; phoff=struct.unpack_from('<Q',data,32)[0]; size,count=struct.unpack_from('<HH',data,54)
  alignments=[]; compatible=True
  for index in range(count):
   base=phoff+index*size
   if struct.unpack_from('<I',data,base)[0]==1:
    p_offset,p_vaddr=struct.unpack_from('<QQ',data,base+8); align=struct.unpack_from('<Q',data,base+48)[0]
    alignments.append(align); compatible &= align>=16384 and p_offset%16384==p_vaddr%16384
  record={'path':item.filename,'machine':machine,'compressed':item.compress_type!=zipfile.ZIP_STORED,'dataOffset':offset,'zip16kAligned':offset%16384==0,'loadAlignments':alignments,'elf16kCompatible':compatible}
  report['libraries'].append(record)
report['passed']=bool(report['libraries']) and all(r['machine']==183 and not r['compressed'] and r['zip16kAligned'] and r['elf16kCompatible'] for r in report['libraries'])
print(json.dumps(report,indent=2)); sys.exit(0 if report['passed'] else 1)
