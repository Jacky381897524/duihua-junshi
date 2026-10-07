"""Verify the final APK really contains every indexed knowledge document and notice."""
import json
import sys
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
with ZipFile(Path(sys.argv[1]) if len(sys.argv)>1 else root/'output/love-advisor-0.2.0-debug.apk') as apk:
    # AAPT writes UTF-8 entry bytes without always setting ZIP language flag 11.
    # Python otherwise interprets them as CP437; use the actual wire path bytes.
    entries={}
    for info in apk.infolist():
        name=info.filename if info.flag_bits & 0x800 else info.filename.encode('cp437').decode('utf-8')
        entries[name]=info
    rows=json.loads(apk.read('assets/knowledge/index.json').decode('utf-8'))
    assert len(rows)==45 and len({r['id'] for r in rows})==45
    for row in rows:
        assert apk.read(entries['assets/'+row['path']]).decode('utf-8').strip(), row['path']
    for name in ('Jev-MIT.txt','Goutou-MIT.txt'):
        assert 'Permission is hereby granted' in apk.read('assets/notices/'+name).decode('utf-8')
    sources=apk.read('assets/notices/SOURCES.json').decode('utf-8')
    assert '23b5ca56930960cfac470815ae582cd713eda95a' in sources
    assert '6db7354a4002dc7c448a9c87ffdad8132570c9d3' in sources
print('Final APK: 45 knowledge documents + original licenses/commits PASS')
