#!/usr/bin/env python3
"""Static gate for the modern-v3 Django 2.2 bridge."""
from pathlib import Path
import ast
import sys

ROOTS = [Path('opentreemap'), Path('scripts')]
errors = []

banned_text = {
    'django.core.urlresolvers': 'removed URL resolver import',
    'from django.conf.urls import url': 'legacy url() import',
    'models.GeoManager': 'GeoManager removed in Django 2.0',
    '.is_authenticated()': 'is_authenticated is a property in Django 2.x',
    'field.rel.to': 'Field.rel removed in Django 2.0',
    '.rel.to': 'Field.rel removed in Django 2.0',
    'urllib2.URLError': 'Python 2 urllib2 reference',
    'ugettext(': 'legacy translation function',
    'ugettext_lazy': 'legacy translation function',
    'ungettext(': 'legacy plural translation function',
}

for root in ROOTS:
    for path in root.rglob('*.py'):
        if path.name == 'modern_v3_static_check.py':
            continue
        text = path.read_text(encoding='utf-8')
        try:
            tree = ast.parse(text, filename=str(path))
        except SyntaxError as exc:
            errors.append(f'{path}:{exc.lineno}: syntax error: {exc.msg}')
            continue

        for needle, reason in banned_text.items():
            if needle in text:
                errors.append(f'{path}: {reason}: {needle}')

        for node in ast.walk(tree):
            if (isinstance(node, ast.Call)
                    and isinstance(node.func, ast.Attribute)
                    and node.func.attr in ('ForeignKey', 'OneToOneField')):
                if not any(k.arg == 'on_delete' for k in node.keywords):
                    errors.append(
                        f'{path}:{node.lineno}: {node.func.attr} missing on_delete')

# Detect common Python-2 implicit relative imports that compile on Python 3 but
# fail at runtime. External `celery` inside opentreemap/opentreemap/celery.py is
# intentionally excluded.
for path in Path('opentreemap').rglob('*.py'):
    siblings = {p.stem for p in path.parent.glob('*.py')}
    siblings |= {
        p.name for p in path.parent.iterdir()
        if p.is_dir() and (p / '__init__.py').exists()
    }
    try:
        tree = ast.parse(path.read_text(encoding='utf-8'))
    except SyntaxError:
        continue
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            first = node.module.split('.')[0]
            if first in siblings and not (
                    path.as_posix().endswith('opentreemap/celery.py')
                    and first == 'celery'):
                errors.append(
                    f'{path}:{node.lineno}: possible implicit relative import: {node.module}')
        elif isinstance(node, ast.Import):
            for alias in node.names:
                first = alias.name.split('.')[0]
                if first in siblings and not (
                        path.as_posix().endswith('opentreemap/celery.py')
                        and first == 'celery'):
                    errors.append(
                        f'{path}:{node.lineno}: possible implicit relative import: {alias.name}')

if errors:
    print('modern-v3 static check FAILED')
    for error in errors:
        print(' -', error)
    sys.exit(1)

print('modern-v3 static check OK')
