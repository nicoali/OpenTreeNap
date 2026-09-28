# OpenTreeMap modern-v2

## Goal

Remove the Python 2-era runtime assumptions from the source without changing the database schema or performing a Django major-version upgrade yet.

## Validation completed

- All Python source under `opentreemap/` and `scripts/` compiles with Python 3.13.
- Compilation also succeeds with `SyntaxWarning` promoted to an error.
- Targeted scans find no remaining runtime use of:
  - `xrange`
  - `basestring`
  - `unicode`
  - `long`
  - `dict.iteritems()` / `itervalues()` / `iterkeys()`
  - Python 2 `StringIO` / `cStringIO`
  - `urllib2`
  - Python 2 `urlparse`
  - `__metaclass__`
  - iterator `.next()`
  - the Python 2 `file()` builtin

## Areas changed

The compatibility pass touches API/authentication helpers, importer/exporter code, tree models/auditing/UDFs, management views, geocoding tests/helpers, image handling, ecoservice communication and tests.

The most important non-mechanical changes are:

1. HMAC and Base64 operations now explicitly cross the text/bytes boundary.
2. Ecoservice HTTP requests use Python 3 `urllib.request`/`urllib.error` and decode HTTP response bodies before JSON/regex handling.
3. Image/file paths use `BytesIO` for binary content.
4. `UDFModel` now declares `UDFModelBase` with Python 3 metaclass syntax.
5. `UDFDictionary.items()` preserves the old custom `iteritems()` behavior, including collection UDF values.

## Deliberately not changed yet

- Django major version
- PostgreSQL/PostGIS schema
- historical Django migrations
- Celery major version
- Node/Webpack/Leaflet stack
- obsolete third-party Django packages

Those will be handled behind tests in later versions.

## Current status

`modern-v2` is a **source compatibility milestone**, not a production deployment release. The existing dependency pins still belong to the Django 1.11 era and must be dealt with in modern-v3 before claiming a working modern runtime.
