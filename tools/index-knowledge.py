"""Regenerate paths/titles without discarding curated tags, dates or provenance."""
import json
import pathlib
import shutil

root = pathlib.Path(__file__).resolve().parent.parent
assets = root / 'android/app/src/main/assets/knowledge'
notices = root / 'android/notices'
packaged_notices = assets.parent / 'notices'
existing = {row['id']: row for row in json.loads((assets / 'index.json').read_text(encoding='utf-8'))}
manifest = json.loads((notices / 'SOURCES.json').read_text(encoding='utf-8'))
keywords = {'invite': ['约会', '吸引', '关系启动', '主动表达', '第一次', '接触'], 'conflict': ['冲突', '吵架', '修复', '分手', '背叛'], 'comfort': ['情绪', '依恋', '温暖'], 'boundary': ['边界', '同意', '拒绝', '失衡', '退出', '操控'], 'expression': ['表达', '话术', '接话', '松弛', '自然', '尴尬', '废话'], 'work': ['办事', '领导', '人脉', '孤立', '逻辑'], 'personality': ['MBTI', '依恋', '人格'], 'family': ['婚姻', '家庭', '育儿', '家务', '金钱'], 'evidence': ['证据', '论文', '书单', '索引'], 'digital': ['在线', '数字', 'ChatLab'], 'theory': ['心理学', '哲学', '社交体系', '自然流'], 'safety': ['法律', '安全', '危机'], 'memory': ['长期记忆', '关系档案'], 'reciprocity': ['互惠', '有来有回'], 'humor': ['幽默']}
paths = {p.relative_to(assets).as_posix(): p for p in sorted(assets.rglob('*.md'))}
docs = []
# Preserve stable ordering and document-level custom fields.
for doc_id in [i for i in existing if i in paths] + [i for i in paths if i not in existing]:
    p = paths[doc_id]
    text = p.read_text(encoding='utf-8')
    defaults = {
        'tags': [k for k, terms in keywords.items() if any(t in p.stem for t in terms)],
        'source': 'goutoujunshi@6db7354' if 'goutoujunshi' in p.parts else 'Jev v1.3 转译' if p.name == 'jev-copilot.md' else '本项目原创',
        'date': '2026-10-02',
    }
    previous = existing.get(doc_id, {})
    row = {'id': doc_id, 'title': next((line.lstrip('# ').strip() for line in text.splitlines() if line.startswith('# ')), p.stem), 'path': 'knowledge/' + doc_id}
    row.update({**defaults, **{k: v for k, v in previous.items() if k not in row}})
    docs.append(row)
manifest['documents'] = docs
# Validate licenses before writing any generated files.
licenses = [pathlib.PurePosixPath(source['notice']).name for source in manifest.get('reusedSources', [])]
for name in licenses:
    if not (notices / name).is_file():
        raise FileNotFoundError(f'Missing upstream license: {name}')
encoded = json.dumps(manifest, ensure_ascii=False, indent=2)
(assets / 'index.json').write_text(json.dumps(docs, ensure_ascii=False, indent=2), encoding='utf-8', newline='\n')
(notices / 'SOURCES.json').write_text(encoded, encoding='utf-8', newline='\n')
packaged_notices.mkdir(exist_ok=True)
(packaged_notices / 'SOURCES.json').write_text(encoded, encoding='utf-8', newline='\n')
for name in licenses:
    shutil.copyfile(notices / name, packaged_notices / name)
print('Indexed', len(docs), 'documents; preserved source manifest and licenses')
