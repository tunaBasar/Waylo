#!/usr/bin/env python3
"""Static check: every literal /api/... path the web and mobile clients call must match a gateway Path predicate.

Template literals are cut at the first ${...}; the remaining static prefix must be matchable (a dynamic tail is
assumed to be a path segment or query). Exit code 1 and one line per unmatched path.
"""
import pathlib
import re
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent
CLIENT_DIRS = ['frontend/src', 'mobile/src']
EXTS = {'.ts', '.tsx', '.js', '.jsx'}
LITERAL = re.compile(r"""(['"`])(/api/[^'"`\s]*)""")


def find_routes(node):
    if isinstance(node, dict):
        if isinstance(node.get('routes'), list):
            yield from node['routes']
        for v in node.values():
            yield from find_routes(v)


def gateway_patterns() -> list:
    cfg = yaml.safe_load((ROOT / 'api-gateway/src/main/resources/application.yml').read_text()) or {}
    patterns = []
    for route in find_routes(cfg):
        for pred in route.get('predicates') or []:
            if str(pred).startswith('Path='):
                patterns += [p.strip() for p in str(pred)[5:].split(',') if p.strip()]
    return patterns


def to_regex(pattern: str, prefix_mode: bool) -> re.Pattern:
    out = ''
    i = 0
    while i < len(pattern):
        if pattern.startswith('/**', i):
            out += '(?:/.*)?'
            i += 3
        elif pattern[i] == '*':
            out += '[^/]*'
            i += 1
        else:
            out += re.escape(pattern[i])
            i += 1
    return re.compile(out + '$')


def matches(path: str, dynamic: bool, regexes: list, patterns: list) -> bool:
    if any(r.match(path) for r in regexes):
        return True
    if dynamic:
        # Static prefix of a template: accept when a pattern could match a continuation of it.
        base = path.rstrip('/')
        for p in patterns:
            lit = re.split(r'\*', p, maxsplit=1)[0]
            if lit.startswith(path) or path.startswith(lit) and p.endswith('**') or lit.startswith(base + '/'):
                return True
            if any(r.match(base + '/x') or r.match(base + '/x/y') for r in regexes):
                return True
    return False


def main() -> int:
    patterns = gateway_patterns()
    regexes = [to_regex(p, False) for p in patterns]
    problems = []
    for d in CLIENT_DIRS:
        for f in sorted((ROOT / d).rglob('*')):
            if f.suffix not in EXTS or not f.is_file():
                continue
            for n, line in enumerate(f.read_text(errors='ignore').splitlines(), 1):
                for m in LITERAL.finditer(line):
                    raw = m.group(2)
                    dynamic = '${' in raw
                    path = raw.split('${', 1)[0]
                    path = re.split(r'[?#]', path, maxsplit=1)[0]
                    if not matches(path, dynamic, regexes, patterns):
                        problems.append(f'{f.relative_to(ROOT)}:{n}: {raw} matches no gateway Path predicate')
    for p in problems:
        print(p)
    print(f'check-gateway-routes: {len(problems)} problem(s), {len(patterns)} gateway patterns')
    return 1 if problems else 0


if __name__ == '__main__':
    sys.exit(main())
