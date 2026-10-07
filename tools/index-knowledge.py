import json, pathlib, shutil
root=pathlib.Path(__file__).resolve().parent.parent
assets=root/'android/app/src/main/assets/knowledge'
keywords={'invite':['约会','吸引','关系启动','主动表达','第一次','接触'], 'conflict':['冲突','吵架','修复','分手','背叛'], 'comfort':['情绪','依恋','温暖'], 'boundary':['边界','同意','拒绝','失衡','退出','操控'], 'expression':['表达','话术','接话','松弛','自然','尴尬','废话'], 'work':['办事','领导','人脉','孤立','逻辑']}
keywords.update({'personality':['MBTI','依恋','人格'], 'family':['婚姻','家庭','育儿','家务','金钱'], 'evidence':['证据','论文','书单','索引'], 'digital':['在线','数字','ChatLab'], 'theory':['心理学','哲学','社交体系','自然流'], 'safety':['法律','安全','危机'], 'memory':['长期记忆','关系档案']})
docs=[]
for p in sorted(assets.rglob('*.md')):
 text=p.read_text(encoding='utf-8'); title=next((l.lstrip('# ').strip() for l in text.splitlines() if l.startswith('# ')),p.stem)
 tags=[k for k,terms in keywords.items() if any(t in p.stem for t in terms)]
 if p.name=='romance-master.md': tags=['invite','conflict','comfort','boundary','expression']
 if p.name=='jev-copilot.md': tags=['work','comfort','expression']
 docs.append({'id':p.relative_to(assets).as_posix(),'title':title,'path':'knowledge/'+p.relative_to(assets).as_posix(),'tags':tags,'source':'goutoujunshi@6db7354' if 'goutoujunshi' in p.parts else 'Jev v1.3 转译' if p.name=='jev-copilot.md' else '本项目原创','date':'2026-10-02'})
(assets/'index.json').write_text(json.dumps(docs,ensure_ascii=False,indent=2),encoding='utf-8')
notices=root/'android/notices'; notices.mkdir(exist_ok=True)
shutil.copyfile(root/'reference/goutoujunshi/LICENSE',notices/'Goutou-MIT.txt')
(notices/'SOURCES.json').write_text(json.dumps({'jev':'23b5ca56930960cfac470815ae582cd713eda95a','goutoujunshi':'6db7354a4002dc7c448a9c87ffdad8132570c9d3','documents':docs},ensure_ascii=False,indent=2),encoding='utf-8')
print('Indexed',len(docs),'documents; upstream',sum('goutoujunshi' in d['id'] for d in docs))
