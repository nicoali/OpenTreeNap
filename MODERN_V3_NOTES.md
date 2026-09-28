# OpenTreeMap modern-v3

## Goal

`modern-v3` is the Django-2.2/Python-3 bridge release. It is intentionally not
the final modernization target. Its job is to make the legacy OTM core cross
the Django 2.0 compatibility boundary cleanly before moving to newer LTS
releases.

Target runtime:

- CPython 3.8
- Django 2.2.28
- PostgreSQL/PostGIS
- Redis + Celery 4.4 bridge stack

## Main changes from modern-v2

- Replaced removed `django.core.urlresolvers` imports with `django.urls`.
- Replaced URL `url()` declarations with `re_path()`.
- Replaced function-based Django login/logout URLs with `LoginView` and
  `LogoutView`.
- Switched JavaScript i18n URL to `JavaScriptCatalog`.
- Replaced `ugettext*`/`ungettext` names with `gettext*`/`ngettext`.
- Converted `user.is_authenticated()` calls to the property form.
- Added explicit `on_delete=models.CASCADE` to all legacy ForeignKey and
  OneToOneField definitions, including historical migrations.
- Replaced removed GeoDjango `GeoManager` with normal `Manager`.
- Replaced legacy `GeoQuerySet.distance()` calls with `Distance()` annotations.
- Replaced removed `Field.rel.to` usage with `remote_field.model`.
- Fixed remaining implicit Python-2 relative imports.
- Fixed the leftover `urllib2.URLError` runtime reference.
- Added an environment-driven `local_settings.py` with PostGIS/Redis defaults.
- Replaced the old dependency set with a Python-3/Django-2.2 bridge set.
- Retained the previous dependency file as `requirements-legacy-v2.txt`.
- Replaced the obsolete boto2 S3 example with the boto3 storage backend.
- Added Docker/PostGIS/Redis bridge scaffolding.

## Validation performed in this workspace

The entire Python source tree is compiled with Python 3 after the changes.
A static modernization check also verifies the major Django-2.0 removal points.

The full dependency installation and `manage.py check` cannot be executed in
this build environment because outbound package downloads are unavailable and
only Python 3.13 is installed here. Django 2.2 is deliberately targeted at
Python 3.8/3.9, not Python 3.13.

## Local bridge startup

Create your environment file:

    cp .env.modern-v3.example .env.modern-v3

Then build the bridge services:

    docker compose -f docker-compose.modern-v3.yml build

Before first application startup, run migrations:

    docker compose -f docker-compose.modern-v3.yml run --rm web python manage.py migrate

Then start:

    docker compose -f docker-compose.modern-v3.yml up

The backend should be reachable on port 8000 once its dependencies and database
are healthy.

## Known remaining work

The Webpack 1 / Node-era frontend has deliberately not been modernized in v3.
The next milestone should address the frontend build and then advance the
backend to Django 3.2/4.2. Some legacy third-party packages should eventually
be removed rather than carried forward indefinitely.
