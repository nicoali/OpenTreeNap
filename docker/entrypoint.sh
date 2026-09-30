#!/usr/bin/env bash
set -euo pipefail

cd /app/opentreemap

log() { printf '[modern-v4.1] %s\n' "$*"; }

wait_for_postgres() {
  log "Waiting for PostgreSQL at ${OTM_DB_HOST:-db}:${OTM_DB_PORT:-5432}..."
  python - <<'PY'
import os, time
import psycopg2

params = dict(
    dbname=os.environ.get('OTM_DB_NAME', 'otm'),
    user=os.environ.get('OTM_DB_USER', 'otm'),
    password=os.environ.get('OTM_DB_PASSWORD', 'otm'),
    host=os.environ.get('OTM_DB_HOST', 'db'),
    port=os.environ.get('OTM_DB_PORT', '5432'),
)
last = None
for _ in range(90):
    try:
        conn = psycopg2.connect(**params)
        conn.close()
        print('PostgreSQL is ready')
        break
    except Exception as exc:
        last = exc
        time.sleep(1)
else:
    raise SystemExit('PostgreSQL did not become ready: %s' % last)
PY
}

wait_for_redis() {
  log "Waiting for Redis..."
  python - <<'PY'
import os, time
import redis
url = os.environ.get('OTM_REDIS_URL', 'redis://redis:6379/0')
last = None
for _ in range(90):
    try:
        client = redis.Redis.from_url(url)
        client.ping()
        print('Redis is ready')
        break
    except Exception as exc:
        last = exc
        time.sleep(1)
else:
    raise SystemExit('Redis did not become ready: %s' % last)
PY
}

wait_for_postgres
wait_for_redis

log "Running Django migrations..."
python manage.py migrate --noinput

log "Ensuring the OpenTreeMap system user exists..."
python manage.py create_system_user

# django-js-reverse must generate assets/js/shim/reverse.js before Webpack.
log "Generating JavaScript URL reverse table..."
python manage.py collectstatic_js_reverse

if [[ "${OTM_SKIP_FRONTEND_BUILD:-0}" != "1" ]]; then
  if [[ ! -s /app/static/webpack-stats.json || "${OTM_FORCE_FRONTEND_BUILD:-0}" == "1" ]]; then
    log "Building legacy frontend bundle with the v4.1 Node 14 bridge..."
    cd /app
    ./node_modules/.bin/webpack --config webpack.prod.config.js
    cd /app/opentreemap
  else
    log "Frontend bundle already present; skipping rebuild."
  fi
else
  log "Skipping frontend build because OTM_SKIP_FRONTEND_BUILD=1"
fi

log "Collecting static files..."
log "Compiling Italian/English translation catalogs..."
python manage.py compilemessages --locale it --locale en

python manage.py collectstatic --noinput

log "Running Django system checks..."
python manage.py check

log "Starting web process..."
exec "$@"
