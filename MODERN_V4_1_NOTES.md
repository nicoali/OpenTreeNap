# OpenTreeMap modern-v4.1

`modern-v4.1` is the first runtime-fix iteration after the Ubuntu smoke test of v4.

## Why v4.1 exists

The v4 test reached Docker successfully but failed before Python dependencies were installed because Debian 11 (Bullseye) reached LTS end-of-life on 2026-08-31. During September 2026 the Bullseye security repository entered a transition state where repository indexes can still reference package files that have already been removed, causing repeated HTTP 404 errors during `apt-get install`.

v4.1 does not work around that broken repository with `--fix-missing` or by disabling security checks. Instead it removes Bullseye apt from the Python runtime.

## Bridge stack

- CPython 3.10 on Debian 12 Bookworm
- Django 3.2.25 (still a temporary compatibility bridge)
- PostgreSQL 14 + PostGIS 3.5
- Redis 6.2
- Celery 5.2.7
- Gunicorn 20.1
- Node 14.21.3 isolated only for the original Webpack 1 / node-sass 4 frontend

Django 3.2 supports Python 3.10 as of Django 3.2.9. The legacy frontend remains on Node 14 because upgrading it to a current Node/Webpack/Sass stack is a separate migration step.

## Docker change

`Dockerfile.modern-v4.1` is multi-stage:

1. `node:14.21.3-bullseye` resolves the legacy JavaScript dependency tree. It does not run Bullseye `apt-get`.
2. `python:3.10-slim-bookworm` is the actual application runtime and installs system packages from Debian 12.
3. Only the Node 14 executable and resolved `node_modules` needed by the legacy bundle are carried into the Python runtime.

The runtime therefore no longer depends on Bullseye package repositories.

## Ubuntu test

Extract v4.1 beside the old v4 directory. Then:

```bash
cd ~/opentree/otm-core-modern-v4.1
chmod +x modern-v4.1.sh
./modern-v4.1.sh doctor
./modern-v4.1.sh build
```

If the image builds, start the stack:

```bash
./modern-v4.1.sh up
./modern-v4.1.sh logs
```

In another terminal:

```bash
./modern-v4.1.sh status
./modern-v4.1.sh smoke
./modern-v4.1.sh check
```

Expected health response:

```json
{"status":"ok","database":"ok"}
```

## Important

This is still a development/migration bridge. Django 3.2 and Node 14 are no longer supported upstream. They are retained only to cross the legacy compatibility gap in smaller, testable steps. Do not expose this stack directly to the public Internet.
