#!/usr/bin/env python3
"""Conservative source inventory and reference candidates. No deletion decisions."""
from collections import Counter
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PROD = ROOT / 'src/main/java'
INHERITED = {'CropState.java','FarmingHandler.java','FarmingPatch.java','FarmingRegion.java','FarmingWorld.java','PatchImplementation.java','PatchState.java'}
# Keep string literals when counting references: names may be used by configuration,
# serialized fields, reflection in test harnesses, or external plugin messages.
def strip_comments(text):
    pattern = r'("(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\')|(/\*[\s\S]*?\*/|//[^\n]*)'
    return re.sub(pattern, lambda m: m.group(1) or '\n'*m.group(2).count('\n'), text)
sources = sorted(PROD.rglob('*.java'))
maintained = [p for p in sources if not (p.parent.name == 'queststate' and p.name in INHERITED)]
references = Counter()
for p in sorted((ROOT/'src').rglob('*.java')) + sorted((ROOT/'tools').glob('*.java')):
    references.update(re.findall(r'\b[A-Za-z_$][\w$]*\b',strip_comments(p.read_text(encoding='utf-8'))))
print(f'MAINTAINED_FILES {len(maintained)}')
method = re.compile(r'(?m)^[ \t]*(?:(?:private|static|final|protected|public|synchronized|abstract)\s+)+[\w.<>, ?\[\]]+\s+(\w+)\s*\(')
for p in maintained:
    raw = p.read_text(encoding='utf-8')
    clean = strip_comments(raw)
    count = len(raw.splitlines())
    creations=len(re.findall(r'new (?:HashMap|HashSet|ArrayList|LinkedHashMap|LinkedHashSet|TreeMap|TreeSet|EnumMap|StringJoiner)\b', clean))
    print(f'FILE {p.relative_to(PROD)} lines={count} streams={clean.count(".stream(")} collection_creations={creations}')
    for match in method.finditer(clean):
        name = match.group(1)
        if references[name]==1:
            print(f'REVIEW {p.name}:{clean.count(chr(10),0,match.start())+1} method {name}')
    for match in re.finditer(r'(?m)^import\s+([\w.]+);',clean):
        name=match.group(1).split('.')[-1]
        if len(re.findall(r'\b'+re.escape(name)+r'\b',clean))==1:
            print(f'REVIEW {p.name} import {name}')
print('Candidate references require manual verification of annotations, inherited methods, reflection, config and external integrations.')
