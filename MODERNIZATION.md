# OpenTreeMap modernization roadmap

This working copy starts from `otm-core-develop` and is being modernized incrementally.
The original project is preserved separately.

## Baseline audit

- Backend: Django 1.11.16 / legacy Python 2-era code
- GIS: GeoDjango + PostgreSQL/PostGIS
- Async/cache: Celery 4.1 + Redis
- Frontend: Leaflet 1.0.3 + Webpack 1 + legacy Sass/Node toolchain
- Python source files: 332
- JavaScript source files: 132
- Django migrations: 86
- HTML/Mustache templates: 188
- Test modules: 44

### Important legacy areas

- Python 2 runtime idioms remain (`iteritems`, `xrange`, `basestring`, `unicode`, old urllib/urlparse APIs).
- Django APIs removed after 1.x are used throughout (`django.conf.urls.url`, `django.core.urlresolvers`, `ugettext`, function based auth views, etc.).
- Many model `ForeignKey` declarations predate the mandatory `on_delete` argument.
- Several third-party Django packages are obsolete or unmaintained and should be replaced rather than simply version-bumped.
- Celery configuration uses legacy names and pickle task serialization.
- Frontend build tooling is far behind currently supported Node/Webpack versions.

## Migration strategy

### Phase 0 - Baseline and safety

1. Preserve original source and database schema.
2. Make the Python source syntactically valid under Python 3.
3. Establish repeatable local/container development environment.
4. Establish smoke tests before changing framework behavior.

### Phase 1 - Python 3 runtime compatibility

Convert Python 2-only runtime behavior while keeping application behavior as close as possible:

- dictionary iterator methods
- `xrange`
- `basestring` / `unicode`
- `urlparse` / `urllib`
- bytes/text handling, especially hashing, CSV, JSON and database adapters
- old exception syntax and remaining Python 2 syntax

### Phase 2 - Django bridge

Move through a controlled Django compatibility bridge rather than jumping directly from 1.11 to a current release. Update:

- URL routing
- authentication views
- translations
- middleware/settings
- model `on_delete`
- template APIs
- GIS/PostGIS integration
- migrations and custom fields

### Phase 3 - Replace legacy dependencies

Review or replace registration, comments/threading, recaptcha, storage, webpack integration, CSV, Redis/Celery and other abandoned packages.

### Phase 4 - Current supported backend

Target a currently supported Python/Django/PostgreSQL/PostGIS stack, with modern Celery/Redis configuration and a reproducible deployment image.

### Phase 5 - Frontend modernization

Modernize the build chain and mapping UI independently from the backend. Preserve application behavior first, then evaluate Leaflet upgrade versus MapLibre/OpenStreetMap architecture.

## modern-v1 changes

- Fixed Python 2 exception syntax in `manage_treemap/views/__init__.py`.
- Replaced one runtime `iteritems()` in the same path with `items()`.
- Fixed Python 2 `print` syntax in `otm1_migrator/data_util.py`.
- Entire `opentreemap` Python tree now passes Python 3 `compileall` syntax compilation (warnings about legacy regex string escapes remain and will be addressed later).

## modern-v2 changes

This release performs the broad Python 3 source/runtime compatibility pass while intentionally keeping the Django major version and database schema unchanged.

- Replaced Python 2 dictionary APIs (`iteritems`, `itervalues`, `iterkeys`) throughout application and test code.
- Replaced `xrange`, `basestring`, `unicode` and `long` runtime usage with Python 3 equivalents.
- Migrated `__unicode__` model methods to `__str__`.
- Converted legacy `urlparse`, `urllib`, and `urllib2` usage to `urllib.parse`, `urllib.request`, and `urllib.error`.
- Corrected Python 3 bytes/text handling in API HMAC signing, Base64 credentials, request bodies, ecoservice HTTP calls, image uploads and in-memory file streams.
- Replaced `StringIO` / `cStringIO` with `io.StringIO` or `io.BytesIO` according to whether the call site handles text or binary data.
- Added `functools.reduce` where Python 2 previously provided it as a builtin.
- Replaced Python 2-only `file()`, iterator `.next()`, and direct indexing/addition of dictionary views.
- Converted the `UDFModel` metaclass declaration to Python 3 syntax and preserved the custom UDF dictionary iteration behavior.
- Fixed Python 3 regular-expression escape warnings in URL/validation expressions.
- A scan for the targeted Python 2 runtime constructs now returns zero source hits.
- The complete `opentreemap` + `scripts` Python source passes:

  `python3 -W error::SyntaxWarning -m compileall -q opentreemap scripts`

  using Python 3.13 in the modernization workspace.

### Important limitation

Passing compilation does **not** mean the application is ready to run on Python 3.13. The project still pins Django 1.11 and many 2017-era dependencies. Django/framework compatibility is deliberately the next phase so source-language issues and framework issues remain separated. No database migration was added or rewritten in modern-v2.

## modern-v3 changes

This release crosses the Django 2.0 compatibility boundary and defines a reproducible Django 2.2 bridge runtime.

- Target bridge stack: CPython 3.8 + Django 2.2.28.
- Replaced removed URL resolver/import APIs with `django.urls` and `re_path`.
- Replaced function-based login/logout and JavaScript i18n views with class-based equivalents.
- Migrated translation calls to `gettext*` / `ngettext`.
- Converted `is_authenticated()` to the property API.
- Added explicit `on_delete=models.CASCADE` to 110 legacy relationship declarations, including historical migrations, preserving the old default cascade semantics.
- Replaced removed `Field.rel.to` access with `remote_field.model`.
- Replaced removed GeoDjango `GeoManager` usage with `Manager`.
- Rewrote legacy GeoQuerySet `distance()` annotations with the `Distance` database function.
- Fixed remaining Python-2 implicit relative imports and a leftover `urllib2.URLError` runtime reference.
- Replaced the old dependency set with a Python-3/Django-2.2 bridge set while preserving the old list as `requirements-legacy-v2.txt`.
- Added environment-driven PostGIS/Redis local settings plus Docker bridge scaffolding.
- Added `scripts/modern_v3_static_check.py` as a repeatable modernization gate.

Validation in the modernization workspace:

- `python3 scripts/modern_v3_static_check.py` -> OK
- `python3 -W error::SyntaxWarning -m compileall -q opentreemap scripts` -> OK

The full Django runtime check was not executed in this workspace because package download access is unavailable and the workspace interpreter is Python 3.13, which is outside the Django 2.2 bridge target.

## Next milestone

`modern-v4` supersedes this planned milestone below: it raises the bridge to Django 3.2 and packages the first Ubuntu/Docker runtime candidate.

## modern-v4 changes

This is the first milestone designed for an end-to-end Ubuntu/Docker smoke test.

- Raised the controlled bridge to CPython 3.8 + Django 3.2.25.
- Containerized the GIS layer with PostgreSQL 14 + PostGIS 3.5; the Python bridge image uses Bullseye GDAL 3.2.
- Fixed Django 3 removals in custom fields, templates, translations/utilities and URL namespacing.
- Added an environment-driven Docker Compose stack with PostGIS, Redis, Gunicorn and an optional Celery worker.
- Added migration/startup orchestration and a database-aware `/healthz/` endpoint.
- Fixed the Celery broker setting namespace and separated Redis cache/broker databases.
- Added a Node 12 / node-sass 4.14 bridge for the legacy Webpack 1 frontend and fixed the production plugin configuration bug.
- Added `scripts/modern_v4_static_check.py` as a Django-3.2 migration gate.
- Added `MODERN_V4_NOTES.md` with the exact Ubuntu smoke-test procedure.

Validation performed in the modernization workspace:

- `python3 scripts/modern_v4_static_check.py` -> OK (332 Python files, 188 templates)
- `python3 -W error::SyntaxWarning -m compileall -q opentreemap scripts` -> OK
- shell syntax checks for all v4 entry/helper scripts -> OK
- Compose YAML parses successfully -> OK

A full Docker build cannot be run in the modernization workspace because Docker and outbound package downloads are unavailable there. The first Ubuntu container run is therefore the next explicit runtime gate.

## Next milestone

After the first `modern-v4` Ubuntu run, fix only the concrete runtime/package failures it surfaces. Once web + PostGIS + the main map render successfully, move the backend away from this unsupported bridge to a supported target (Django 5.2 LTS or newer supported line) and then replace the Webpack 1 / node-sass frontend toolchain.
