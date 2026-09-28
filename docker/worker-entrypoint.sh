#!/usr/bin/env bash
set -euo pipefail
cd /app/opentreemap
python - <<'PY'
import os, time
import psycopg2
import redis

pg = dict(
    dbname=os.environ.get('OTM_DB_NAME', 'otm'),
    user=os.environ.get('OTM_DB_USER', 'otm'),
    password=os.environ.get('OTM_DB_PASSWORD', 'otm'),
    host=os.environ.get('OTM_DB_HOST', 'db'),
    port=os.environ.get('OTM_DB_PORT', '5432'),
)
redis_url = os.environ.get('OTM_REDIS_URL', 'redis://redis:6379/0')
for name, check in (
    ('PostgreSQL', lambda: (lambda c: (c.close(), True)[1])(psycopg2.connect(**pg))),
    ('Redis', lambda: redis.Redis.from_url(redis_url).ping()),
):
    last = None
    for _ in range(90):
        try:
            check()
            print('%s is ready' % name)
            break
        except Exception as exc:
            last = exc
            time.sleep(1)
    else:
        raise SystemExit('%s did not become ready: %s' % (name, last))
PY
exec "$@"
