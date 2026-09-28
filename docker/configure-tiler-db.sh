#!/bin/bash
set -euo pipefail

DB_NAME="${POSTGRES_DB:-otm}"
DB_ADMIN="${POSTGRES_USER:-otm}"
DB_HOST="${OTM_DB_HOST:-db}"
DB_PORT="${OTM_DB_PORT:-5432}"
TILER_USER="${OTM_TILER_DB_USER:-otm_tiler}"
TILER_PASSWORD="${OTM_TILER_DB_PASSWORD:?OTM_TILER_DB_PASSWORD is required}"

echo "[tiler-db] Configuring PostgreSQL role '${TILER_USER}'..."

# PostgreSQL legacy clients used by OTM Tiler require an MD5 verifier.
MD5_HASH="md5$(printf '%s%s' "$TILER_PASSWORD" "$TILER_USER" | md5sum | awk '{print $1}')"

psql -v ON_ERROR_STOP=1 \
     --host "$DB_HOST" \
     --port "$DB_PORT" \
     --username "$DB_ADMIN" \
     --dbname "$DB_NAME" \
     --set=tiler_user="$TILER_USER" \
     --set=tiler_md5="$MD5_HASH" <<'SQL'
SELECT format(
    'CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L',
    :'tiler_user',
    :'tiler_md5'
)
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'tiler_user'
)\gexec

SELECT format(
    'ALTER ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L',
    :'tiler_user',
    :'tiler_md5'
)\gexec

SELECT format('GRANT CONNECT ON DATABASE %I TO %I',
              current_database(), :'tiler_user')\gexec

SELECT format('GRANT USAGE ON SCHEMA public TO %I',
              :'tiler_user')\gexec

SELECT format('GRANT SELECT ON ALL TABLES IN SCHEMA public TO %I',
              :'tiler_user')\gexec

SELECT format('GRANT SELECT ON ALL SEQUENCES IN SCHEMA public TO %I',
              :'tiler_user')\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %I',
    :'tiler_user'
)\gexec

SELECT format(
    'ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON SEQUENCES TO %I',
    :'tiler_user'
)\gexec
SQL

HBA="${PGDATA}/pg_hba.conf"

if ! grep -Eq "^host[[:space:]]+${DB_NAME}[[:space:]]+${TILER_USER}[[:space:]]+all[[:space:]]+md5([[:space:]]|$)" "$HBA"; then
    echo "[tiler-db] Adding legacy MD5 HBA rule..."

    tmp="$(mktemp)"

    awk -v db="$DB_NAME" -v user="$TILER_USER" '
        !done && $1 == "host" && $5 == "scram-sha-256" {
            print "host    " db "    " user "    all    md5"
            done=1
        }
        { print }
        END {
            if (!done)
                print "host    " db "    " user "    all    md5"
        }
    ' "$HBA" > "$tmp"

    cat "$tmp" > "$HBA"
    rm -f "$tmp"
fi

psql -v ON_ERROR_STOP=1 \
     --host "$DB_HOST" \
     --port "$DB_PORT" \
     --username "$DB_ADMIN" \
     --dbname "$DB_NAME" \
     -c "SELECT pg_reload_conf();" >/dev/null

echo "[tiler-db] Configuration complete."
