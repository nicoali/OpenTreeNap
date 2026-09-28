#!/usr/bin/env python3
"""Static gate for the modern-v4.1 Django 3.2 bridge.

This deliberately avoids importing Django so it can run before dependencies
are installed. Runtime verification is performed by docker/entrypoint.sh on
an Ubuntu host via `python manage.py check`.
"""
from __future__ import annotations

import ast
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CODE_ROOT = ROOT / "opentreemap"

BANNED_TEXT = {
    "django.utils.six": "Django 3 removed django.utils.six",
    "from django.utils import six": "Django 3 removed django.utils.six",
    "force_text": "Use force_str on the Django 3.2 bridge",
    "django.core.urlresolvers": "Use django.urls",
    "GeoManager": "Removed from modern GeoDjango",
    "is_authenticated()": "is_authenticated is a property",
    "{% load staticfiles %}": "Use the static template tag library",
    "_get_val_from_obj": "Removed Field API",
}

errors = []
py_files = list(CODE_ROOT.rglob("*.py"))
template_files = list(CODE_ROOT.rglob("*.html"))

# Syntax + textual blockers.
for path in py_files:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8", errors="replace")
    try:
        tree = ast.parse(text, filename=str(rel))
    except SyntaxError as exc:
        errors.append(f"{rel}:{exc.lineno}: syntax error: {exc.msg}")
        continue

    for needle, why in BANNED_TEXT.items():
        if needle in text:
            # Historical comments/tests can mention removed names; only fail
            # executable source for known textual patterns.
            for lineno, line in enumerate(text.splitlines(), 1):
                if needle in line and not line.lstrip().startswith("#"):
                    errors.append(f"{rel}:{lineno}: {why}: {needle}")

    # Django >=2 requires on_delete on FK and OneToOne declarations. Check
    # both live models and historical migrations because migration state loads them.
    for node in ast.walk(tree):
        if not isinstance(node, ast.Call):
            continue
        func = node.func
        name = None
        if isinstance(func, ast.Attribute):
            name = func.attr
        elif isinstance(func, ast.Name):
            name = func.id
        if name in {"ForeignKey", "OneToOneField"}:
            has_kw = any(kw.arg == "on_delete" for kw in node.keywords)
            # Positional on_delete is allowed as the second positional argument.
            has_positional = len(node.args) >= 2
            if not (has_kw or has_positional):
                errors.append(
                    f"{rel}:{getattr(node, 'lineno', '?')}: {name} missing on_delete"
                )

    # Django 3 removed the context argument from Field.from_db_value().
    for node in ast.walk(tree):
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)) and node.name == "from_db_value":
            args = [a.arg for a in node.args.args]
            if "context" in args:
                errors.append(
                    f"{rel}:{node.lineno}: from_db_value still accepts removed context argument"
                )

for path in template_files:
    rel = path.relative_to(ROOT)
    text = path.read_text(encoding="utf-8", errors="replace")
    if "{% load staticfiles %}" in text:
        errors.append(f"{rel}: old staticfiles template tag library")
    if re.search(r"{%-?\s*ifequal\b", text):
        errors.append(f"{rel}: removed ifequal template tag")

# Configuration contract for this milestone.
req = (ROOT / "requirements.txt").read_text()
required_req = [
    "Django==3.2.25",
    "django-contrib-comments==2.2.0",
    "django-js-reverse==0.10.2",
    "django-registration-redux==2.10",
    "django-redis==5.2.0",
    "celery==5.2.7",
]
for item in required_req:
    if item not in req:
        errors.append(f"requirements.txt: expected pin missing: {item}")

package = json.loads((ROOT / "package.json").read_text())
if package.get("devDependencies", {}).get("node-sass") != "4.14.1":
    errors.append("package.json: node-sass must be 4.14.1 for the isolated Node 14 bridge")

for required_path in [
    "Dockerfile.modern-v4.1",
    "docker-compose.modern-v4.1.yml",
    "docker/entrypoint.sh",
    "docker/worker-entrypoint.sh",
    ".env.modern-v4.1.example",
    "modern-v4.1.sh",
]:
    if not (ROOT / required_path).exists():
        errors.append(f"missing deployment file: {required_path}")

compose = (ROOT / "docker-compose.modern-v4.1.yml").read_text()
if "postgis/postgis:14-3.5" not in compose:
    errors.append("compose: expected PostgreSQL 14 / PostGIS 3.5 bridge image")
if "/healthz/" not in compose:
    errors.append("compose: web healthcheck missing /healthz/")

dockerfile = (ROOT / "Dockerfile.modern-v4.1").read_text()
if "FROM python:3.10-slim-bookworm" not in dockerfile:
    errors.append("Dockerfile: expected Python 3.10 Bookworm bridge")
if "FROM node:14.21.3-bullseye AS frontend-deps" not in dockerfile:
    errors.append("Dockerfile: expected isolated Node 14 frontend stage")

settings = (ROOT / "opentreemap/opentreemap/settings/local_settings.py").read_text()
for setting in ["CELERY_BROKER_URL", "OTM_CACHE_URL"]:
    if setting not in settings:
        errors.append(f"local_settings.py: missing {setting}")

if errors:
    print("modern-v4.1 static check FAILED")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)

print("modern-v4.1 static check OK")
print(f"Python files parsed: {len(py_files)}")
print(f"Templates checked: {len(template_files)}")
