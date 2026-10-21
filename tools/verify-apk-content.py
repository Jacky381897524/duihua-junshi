"""Verify the final APK really contains every indexed knowledge document and notice."""
import json
import sys
from pathlib import Path
from zipfile import ZipFile
root=Path(__file__).resolve().parents[1]
assets=root/'android/app/src/main/assets'
expected=json.loads((assets/'knowledge/index.json').read_text(encoding='utf-8'))
assert len({r['id'] for r in expected})==len(expected)
assert {r['path'] for r in expected}=={p.relative_to(assets).as_posix() for p in (assets/'knowledge').rglob('*.md')}
with ZipFile(Path(sys.argv[1]) if len(sys.argv)>1 else root/'output/love-advisor-0.2.0-debug.apk') as apk:
    # AAPT writes UTF-8 entry bytes without always setting ZIP language flag 11.
    # Python otherwise interprets them as CP437; use the actual wire path bytes.
    entries={}
    for info in apk.infolist():
        name=info.filename if info.flag_bits & 0x800 else info.filename.encode('cp437').decode('utf-8')
        entries[name]=info
    rows=json.loads(apk.read('assets/knowledge/index.json').decode('utf-8'))
    assert rows==expected, 'Packaged knowledge index differs from verified sources'
    for row in rows:
        body=apk.read(entries['assets/'+row['path']])
        assert body.decode('utf-8').strip(), row['path']
        assert body==(assets/row['path']).read_bytes(), row['path']
    for name in ('Jev-MIT.txt','Goutou-MIT.txt','HingeProfileOptimizer-MIT.txt','LiquidGlass-MIT.txt'):
        assert 'Permission is hereby granted' in apk.read('assets/notices/'+name).decode('utf-8')
        assert apk.read('assets/notices/'+name)==(assets/'notices'/name).read_bytes()
    sources=apk.read('assets/notices/SOURCES.json').decode('utf-8')
    assert '23b5ca56930960cfac470815ae582cd713eda95a' in sources
    assert '6db7354a4002dc7c448a9c87ffdad8132570c9d3' in sources
    assert '55575ffbc257b3e8a753086ea11f3b89796ff4d6' in sources
    assert json.loads(sources)==json.loads((assets/'notices/SOURCES.json').read_text(encoding='utf-8'))
    assert json.loads(sources)==json.loads((root/'android/notices/SOURCES.json').read_text(encoding='utf-8'))
print(f'Final APK: {len(expected)} knowledge documents + original licenses/commits PASS')
