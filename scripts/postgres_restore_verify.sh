#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  printf 'Usage: ALLOW_RESTORE_VERIFY=true RESTORE_DATABASE=<name>_restore_verify %s <backup.dump>\n' "$0" >&2
  exit 2
fi

backup_file="$1"
restore_database="${RESTORE_DATABASE:-spring_boot_lab_restore_verify}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ "${ALLOW_RESTORE_VERIFY:-false}" != "true" ]]; then
  printf 'Refusing restore: set ALLOW_RESTORE_VERIFY=true for this disposable-database drill.\n' >&2
  exit 2
fi

if [[ ! "${restore_database}" =~ ^[A-Za-z0-9_]+_restore_verify$ ]]; then
  printf 'Refusing restore: database name must end in _restore_verify.\n' >&2
  exit 2
fi

if [[ ! -f "${backup_file}" ]]; then
  printf 'Backup file does not exist: %s\n' "${backup_file}" >&2
  exit 2
fi

cleanup() {
  docker compose --project-directory "${repository_root}" exec -T postgres \
    sh -c 'dropdb --if-exists --force --username="$POSTGRES_USER" "$1"' sh "${restore_database}" >/dev/null
}
trap cleanup EXIT

cleanup
docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c 'createdb --username="$POSTGRES_USER" "$1"' sh "${restore_database}"

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c 'pg_restore --exit-on-error --no-owner --dbname="$1" --username="$POSTGRES_USER"' sh "${restore_database}" \
  < "${backup_file}"

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c 'psql --no-psqlrc --tuples-only --dbname="$1" --username="$POSTGRES_USER" -c "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1; SELECT COUNT(*) AS users FROM users; SELECT COUNT(*) AS orders FROM orders;"' sh "${restore_database}"

printf 'Restore verification completed successfully for disposable database %s.\n' "${restore_database}"
