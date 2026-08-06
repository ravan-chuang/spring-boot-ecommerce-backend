#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

docker compose --project-directory "${repository_root}" exec -T postgres \
  sh -c 'psql --no-psqlrc --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" -c "SELECT COUNT(*) AS processed_events_past_retention FROM processed_events WHERE processed_at < CURRENT_TIMESTAMP - INTERVAL '\''30 days'\''; SELECT COUNT(*) AS order_audits_past_retention FROM order_event_audit WHERE processed_at < CURRENT_TIMESTAMP - INTERVAL '\''90 days'\''; SELECT COUNT(*) AS idempotency_records_past_retention FROM idempotency_records WHERE created_at < CURRENT_TIMESTAMP - INTERVAL '\''24 hours'\''; SELECT COUNT(*) AS dlt_events_total, MIN(received_at) AS oldest_dlt_received_at FROM dead_letter_events;"'
