#!/usr/bin/env python3
"""Fail if any service application*.yml has a duplicate mapping key.

Spring's SnakeYAML refuses to start on duplicate keys ("found duplicate key"),
and unit tests that do not load application.yml never see it.
Usage: python3 scripts/check-yaml-duplicates.py   (exit 1 on findings)
"""
import pathlib
import sys

import yaml

ROOT = pathlib.Path(__file__).resolve().parent.parent


class Loader(yaml.SafeLoader):
    pass


def construct_mapping(loader, node, deep=False):
    keys = [loader.construct_object(k, deep=deep) for k, _ in node.value]
    dups = sorted({str(k) for k in keys if keys.count(k) > 1})
    if dups:
        raise ValueError(f"duplicate key(s) {dups} at line {node.start_mark.line + 1}")
    return loader.construct_mapping(node, deep)


Loader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, construct_mapping)

problems = []
for path in sorted(ROOT.glob('*/src/main/resources/application*.yml')) + sorted(ROOT.glob('config-service/src/main/resources/configs/*.yml')):
    try:
        list(yaml.load_all(path.read_text(encoding='utf-8'), Loader=Loader))
    except Exception as exc:  # noqa: BLE001 - report every parse problem
        problems.append(f"{path.relative_to(ROOT)}: {exc}")

for p in problems:
    print(p)
print(f"{len(problems)} problem(s)")
sys.exit(1 if problems else 0)
