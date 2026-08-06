#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup_directory="${BACKUP_DIR:-${repository_root}/backups}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_file="${backup_directory}/spring_boot_lab_${timestamp}.dump"

mkdir -p "${backup_directory}"

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c 'pg_dump --format=custom --dbname="$POSTGRES_DB" --username="$POSTGRES_USER"' \
  > "${backup_file}"

shasum -a 256 "${backup_file}" > "${backup_file}.sha256"
printf 'Backup created: %s\nChecksum: %s\n' "${backup_file}" "${backup_file}.sha256"
