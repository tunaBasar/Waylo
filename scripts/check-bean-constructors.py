#!/usr/bin/env python3
"""Fail if a Spring bean class has more than one public constructor and none is @Autowired.

Spring then cannot pick a constructor and the service crashes at startup
("No default constructor found"). Unit tests that call `new X(...)` never see it.
Usage: python3 scripts/check-bean-constructors.py   (exit 1 on findings)
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BEAN = re.compile(r'^@(Service|Component|RestController|Controller|Repository|Configuration)\b', re.M)
CLASS = re.compile(r'\b(?:public\s+)?(?:final\s+)?class\s+(\w+)')

findings = []
for path in ROOT.glob('*/src/main/java/**/*.java'):
    src = path.read_text(encoding='utf-8')
    if not BEAN.search(src):
        continue
    m = CLASS.search(src)
    if not m:
        continue
    name = m.group(1)
    ctors = list(re.finditer(r'(@Autowired\s+)?public\s+' + re.escape(name) + r'\s*\(', src))
    if len(ctors) > 1 and not any(c.group(1) for c in ctors):
        findings.append(f'{path.relative_to(ROOT)}: {name} has {len(ctors)} public constructors and none is @Autowired')

for f in findings:
    print(f)
print(f'{len(findings)} problem(s)')
sys.exit(1 if findings else 0)
