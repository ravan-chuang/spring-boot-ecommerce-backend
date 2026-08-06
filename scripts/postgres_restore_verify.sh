#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf \
    'Usage: ALLOW_RESTORE_VERIFY=true RESTORE_DATABASE=<name>_restore_verify RESTORE_ADMIN_USER=<createdb-role> %s <backup.dump>\n' \
    "$0" >&2
  exit 2
fi

backup_file="$1"
restore_database="${RESTORE_DATABASE:-spring_boot_lab_restore_verify}"
restore_admin_user="${RESTORE_ADMIN_USER:-ravan}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checksum_file="${backup_file}.sha256"

if [[ "${ALLOW_RESTORE_VERIFY:-false}" != "true" ]]; then
  printf \
    'Refusing restore: set ALLOW_RESTORE_VERIFY=true for this disposable-database drill.\n' \
    >&2
  exit 2
fi

if [[ ! "${restore_database}" =~ ^[A-Za-z0-9_]+_restore_verify$ ]]; then
  printf \
    'Refusing restore: database name must end in _restore_verify.\n' \
    >&2
  exit 2
fi

if [[ ! "${restore_admin_user}" =~ ^[A-Za-z0-9_]+$ ]]; then
  printf 'Refusing restore: invalid restore administrator role.\n' >&2
  exit 2
fi

if [[ ! -f "${backup_file}" ]]; then
  printf 'Backup file does not exist: %s\n' "${backup_file}" >&2
  exit 2
fi

if [[ ! -f "${checksum_file}" ]]; then
  printf 'Checksum file does not exist: %s\n' "${checksum_file}" >&2
  exit 2
fi

printf 'Verifying backup checksum...\n'
shasum -a 256 -c "${checksum_file}"

printf 'Checking restore administrator privileges for role %s...\n' \
  "${restore_admin_user}"

can_create_database="$(
  docker compose --project-directory "${repository_root}" exec -T postgres \
    sh -c '
      psql \
        --no-psqlrc \
        --tuples-only \
        --no-align \
        --dbname="$POSTGRES_DB" \
        --username="$1" \
        --command="
          SELECT rolsuper OR rolcreatedb
          FROM pg_roles
          WHERE rolname = current_user;
        "
    ' sh "${restore_admin_user}" |
  tr -d '[:space:]'
)"

if [[ "${can_create_database}" != "t" ]]; then
  printf \
    'Restore administrator %s does not have CREATEDB or SUPERUSER privilege.\n' \
    "${restore_admin_user}" >&2
  exit 2
fi

cleanup() {
  docker compose --project-directory "${repository_root}" exec -T postgres \
    sh -c '
      dropdb \
        --if-exists \
        --force \
        --username="$2" \
        "$1"
    ' sh "${restore_database}" "${restore_admin_user}" \
    >/dev/null
}

trap cleanup EXIT

printf 'Creating disposable database: %s\n' "${restore_database}"
cleanup

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c '
    createdb \
      --username="$2" \
      "$1"
  ' sh "${restore_database}" "${restore_admin_user}"

printf 'Restoring backup...\n'

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c '
    pg_restore \
      --exit-on-error \
      --no-owner \
      --dbname="$1" \
      --username="$2"
  ' sh "${restore_database}" "${restore_admin_user}" \
  < "${backup_file}"

printf 'Checking restored schema and data...\n'

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c '
psql \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --dbname="$1" \
  --username="$2" <<SQL
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;

DO \$\$
DECLARE
    latest_version integer;
BEGIN
    SELECT MAX(version::integer)
    INTO latest_version
    FROM flyway_schema_history
    WHERE success = true;

    IF latest_version <> 11 THEN
        RAISE EXCEPTION
            '\''Expected Flyway version 11, restored version is %'\'',
            latest_version;
    END IF;
END
\$\$;

SELECT '\''users'\'' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT '\''orders'\'', COUNT(*) FROM orders
UNION ALL
SELECT '\''payments'\'', COUNT(*) FROM payments
UNION ALL
SELECT '\''outbox_events'\'', COUNT(*) FROM outbox_events
UNION ALL
SELECT '\''dead_letter_events'\'', COUNT(*) FROM dead_letter_events
UNION ALL
SELECT '\''dead_letter_audit_logs'\'', COUNT(*) FROM dead_letter_audit_logs
UNION ALL
SELECT '\''auth_audit_logs'\'', COUNT(*) FROM auth_audit_logs
ORDER BY table_name;

SELECT
    column_name,
    data_type,
    is_nullable
FROM information_schema.columns
WHERE table_schema = '\''public'\''
  AND table_name = '\''outbox_events'\''
  AND column_name = '\''next_attempt_at'\'';

SELECT
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = '\''public'\''
  AND tablename = '\''outbox_events'\''
  AND indexname = '\''idx_outbox_events_pending_next_attempt'\'';

SELECT
    conrelid::regclass AS table_name,
    conname,
    pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE connamespace = '\''public'\''::regnamespace
  AND conrelid::regclass::text IN (
      '\''idempotency_records'\'',
      '\''dead_letter_events'\'',
      '\''dead_letter_audit_logs'\''
  )
ORDER BY table_name, conname;
SQL
  ' sh "${restore_database}" "${restore_admin_user}"

printf \
  'Restore verification completed successfully for disposable database %s.\n' \
  "${restore_database}"
