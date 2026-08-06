#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
EVIDENCE_DIR="${EVIDENCE_DIR:-$ROOT/verification/reliability_drills_$TIMESTAMP}"

DB_USER="${DRILL_DB_USER:-ravan}"
DB_NAME="${DRILL_DB_NAME:-spring_boot_lab}"

AUTH_URL="${AUTH_URL:-http://localhost:8080/api/auth/login}"
LOGIN_ATTEMPTS="${LOGIN_ATTEMPTS:-50}"

# Increase this if Prometheus rules use a long `for:` duration.
ALERT_WAIT_SECONDS="${ALERT_WAIT_SECONDS:-180}"
RECOVERY_WAIT_SECONDS="${RECOVERY_WAIT_SECONDS:-90}"

mkdir -p "$EVIDENCE_DIR"

log() {
    printf '[%s] %s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$*"
}

psql_query() {
    docker compose exec -T postgres \
        psql \
        --no-psqlrc \
        --set ON_ERROR_STOP=1 \
        -U "$DB_USER" \
        -d "$DB_NAME" \
        "$@"
}

capture_prometheus_alerts() {
    local output="$1"

    curl --fail --silent \
        http://localhost:9090/api/v1/alerts \
        > "$output"
}

capture_service_status() {
    local output="$1"

    {
        echo "Timestamp UTC: $(date -u +'%Y-%m-%dT%H:%M:%SZ')"
        docker compose ps
        echo
        echo "Application health:"
        curl --silent --show-error \
            http://localhost:8080/actuator/health || true
        echo
    } > "$output"
}

capture_outbox_counts() {
    local output="$1"

    psql_query -c "
        SELECT
            status,
            COUNT(*) AS event_count,
            MIN(next_attempt_at) AS earliest_next_attempt,
            MAX(next_attempt_at) AS latest_next_attempt
        FROM outbox_events
        GROUP BY status
        ORDER BY status;
    " > "$output"
}

insert_synthetic_outbox_events() {
    local count="$1"
    local phase="$2"

    psql_query -c "
        INSERT INTO outbox_events (
            id,
            aggregate_type,
            aggregate_id,
            event_type,
            topic,
            payload,
            status,
            retry_count,
            created_at,
            next_attempt_at,
            correlation_id
        )
        SELECT
            md5(
                random()::text
                || clock_timestamp()::text
                || sequence_number::text
            )::uuid,
            'RELIABILITY_DRILL',
            '${phase}-' || sequence_number,
            'RELIABILITY_DRILL_EVENT',
            'reliability-drill',
            json_build_object(
                'phase', '${phase}',
                'sequence', sequence_number,
                'synthetic', true
            )::text,
            'PENDING',
            0,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            '${phase}-' || sequence_number
        FROM generate_series(1, ${count}) AS sequence_number;
    "
}

cleanup_synthetic_events() {
    psql_query -c "
        DELETE FROM outbox_events
        WHERE aggregate_type = 'RELIABILITY_DRILL';
    " >/dev/null
}

cleanup() {
    log "Running cleanup"

    docker compose start kafka >/dev/null 2>&1 || true

    # Allow any in-flight synchronous Kafka send to finish before deleting
    # synthetic records, preventing a late publisher update from recreating
    # observable drill residue.
    sleep 5

    cleanup_synthetic_events || true
}

trap cleanup EXIT

log "Evidence directory: $EVIDENCE_DIR"

# ---------------------------------------------------------------------------
# Preflight
# ---------------------------------------------------------------------------

log "Running preflight checks"

docker compose up -d \
    postgres redis kafka tempo otel-collector app \
    alertmanager prometheus grafana loki alloy

sleep 20

capture_service_status "$EVIDENCE_DIR/00_preflight_status.txt"

docker inspect spring_boot_lab_app \
    --format '{{.State.Health.Status}}' \
    > "$EVIDENCE_DIR/00_app_health.txt"

if ! grep -qx 'healthy' "$EVIDENCE_DIR/00_app_health.txt"; then
    echo "Application was not healthy before the drill." >&2
    exit 1
fi

capture_outbox_counts "$EVIDENCE_DIR/00_outbox_before.txt"
capture_prometheus_alerts "$EVIDENCE_DIR/00_alerts_before.json"

psql_query -tAc \
    "SELECT COUNT(*) FROM auth_audit_logs;" \
    > "$EVIDENCE_DIR/00_auth_audit_count_before.txt"

START_TIME="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

# ---------------------------------------------------------------------------
# Drill 1: Kafka outage
# ---------------------------------------------------------------------------

log "Starting Kafka outage drill"

docker compose stop kafka

insert_synthetic_outbox_events 5 "kafka-outage"

KAFKA_RETRY_COUNT=0
KAFKA_OUTAGE_WAIT_SECONDS="${KAFKA_OUTAGE_WAIT_SECONDS:-150}"
KAFKA_OUTAGE_POLL_SECONDS="${KAFKA_OUTAGE_POLL_SECONDS:-5}"
elapsed=0

log "Waiting up to ${KAFKA_OUTAGE_WAIT_SECONDS}s for Kafka failure evidence"

while (( elapsed < KAFKA_OUTAGE_WAIT_SECONDS )); do
    KAFKA_RETRY_COUNT="$(
        psql_query -tAc "
            SELECT COUNT(*)
            FROM outbox_events
            WHERE aggregate_type = 'RELIABILITY_DRILL'
              AND aggregate_id LIKE 'kafka-outage-%'
              AND retry_count > 0
              AND next_attempt_at > created_at;
        " | tr -d '[:space:]'
    )"

    PROCESSING_COUNT="$(
        psql_query -tAc "
            SELECT COUNT(*)
            FROM outbox_events
            WHERE aggregate_type = 'RELIABILITY_DRILL'
              AND aggregate_id LIKE 'kafka-outage-%'
              AND status = 'PROCESSING';
        " | tr -d '[:space:]'
    )"

    PUBLISHED_COUNT="$(
        psql_query -tAc "
            SELECT COUNT(*)
            FROM outbox_events
            WHERE aggregate_type = 'RELIABILITY_DRILL'
              AND aggregate_id LIKE 'kafka-outage-%'
              AND status = 'PUBLISHED';
        " | tr -d '[:space:]'
    )"

    log "Kafka outage state: retries=${KAFKA_RETRY_COUNT:-0}, processing=${PROCESSING_COUNT:-0}, published=${PUBLISHED_COUNT:-0}"

    if [[ "${KAFKA_RETRY_COUNT:-0}" -ge 1 ]]; then
        break
    fi

    sleep "$KAFKA_OUTAGE_POLL_SECONDS"
    elapsed=$((elapsed + KAFKA_OUTAGE_POLL_SECONDS))
done

capture_outbox_counts \
    "$EVIDENCE_DIR/01_kafka_outage_outbox.txt"

docker compose logs --since="$START_TIME" app \
    > "$EVIDENCE_DIR/01_kafka_outage_app.log"

capture_prometheus_alerts \
    "$EVIDENCE_DIR/01_kafka_outage_alerts.json"

psql_query -c "
    SELECT
        aggregate_id,
        status,
        retry_count,
        processing_at,
        processing_by,
        next_attempt_at,
        last_error,
        correlation_id
    FROM outbox_events
    WHERE aggregate_type = 'RELIABILITY_DRILL'
      AND aggregate_id LIKE 'kafka-outage-%'
    ORDER BY aggregate_id;
" > "$EVIDENCE_DIR/01_kafka_outage_events.txt"

if [[ "${KAFKA_RETRY_COUNT:-0}" -lt 1 ]]; then
    echo "Kafka outage did not produce a scheduled retry within ${KAFKA_OUTAGE_WAIT_SECONDS}s." >&2
    cat "$EVIDENCE_DIR/01_kafka_outage_events.txt" >&2
    tail -n 150 "$EVIDENCE_DIR/01_kafka_outage_app.log" >&2
    exit 1
fi

log "Kafka outage produced ${KAFKA_RETRY_COUNT} scheduled retry event(s)"

# ---------------------------------------------------------------------------
# Drill 2: Outbox backlog
# ---------------------------------------------------------------------------

log "Starting outbox backlog drill"

insert_synthetic_outbox_events 50 "outbox-backlog"

capture_outbox_counts \
    "$EVIDENCE_DIR/02_backlog_initial.txt"

log "Waiting ${ALERT_WAIT_SECONDS}s for backlog metrics and alerts"
sleep "$ALERT_WAIT_SECONDS"

capture_outbox_counts \
    "$EVIDENCE_DIR/02_backlog_after_wait.txt"

capture_prometheus_alerts \
    "$EVIDENCE_DIR/02_backlog_alerts.json"

curl --fail --silent \
    'http://localhost:9090/api/v1/query?query=outbox_pending_events' \
    > "$EVIDENCE_DIR/02_outbox_pending_metric.json" || true

curl --fail --silent \
    'http://localhost:9090/api/v1/rules' \
    > "$EVIDENCE_DIR/02_prometheus_rules.json"

psql_query -c "
    SELECT
        COUNT(*) AS synthetic_pending,
        MIN(retry_count) AS minimum_retry_count,
        MAX(retry_count) AS maximum_retry_count,
        MIN(next_attempt_at) AS earliest_retry,
        MAX(next_attempt_at) AS latest_retry
    FROM outbox_events
    WHERE aggregate_type = 'RELIABILITY_DRILL'
      AND status = 'PENDING';
" > "$EVIDENCE_DIR/02_backlog_database_evidence.txt"

BACKLOG_COUNT="$(
    psql_query -tAc "
        SELECT COUNT(*)
        FROM outbox_events
        WHERE aggregate_type = 'RELIABILITY_DRILL'
          AND status = 'PENDING';
    " | tr -d '[:space:]'
)"

if [[ "${BACKLOG_COUNT:-0}" -lt 10 ]]; then
    echo "Expected an outbox backlog, but fewer than 10 events remained pending." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Recover Kafka and drain backlog
# ---------------------------------------------------------------------------

log "Restoring Kafka"

docker compose start kafka

log "Waiting ${RECOVERY_WAIT_SECONDS}s for outbox recovery"
sleep "$RECOVERY_WAIT_SECONDS"

capture_outbox_counts \
    "$EVIDENCE_DIR/03_recovery_outbox.txt"

capture_prometheus_alerts \
    "$EVIDENCE_DIR/03_recovery_alerts.json"

docker compose logs --since="$START_TIME" app \
    > "$EVIDENCE_DIR/03_recovery_app.log"

RECOVERED_COUNT="$(
    psql_query -tAc "
        SELECT COUNT(*)
        FROM outbox_events
        WHERE aggregate_type = 'RELIABILITY_DRILL'
          AND status = 'PUBLISHED';
    " | tr -d '[:space:]'
)"

REMAINING_PROCESSABLE="$(
    psql_query -tAc "
        SELECT COUNT(*)
        FROM outbox_events
        WHERE aggregate_type = 'RELIABILITY_DRILL'
          AND status IN ('PENDING', 'PROCESSING');
    " | tr -d '[:space:]'
)"

if [[ "${RECOVERED_COUNT:-0}" -lt 1 ]]; then
    echo "No synthetic outbox events were published after Kafka recovery." >&2
    exit 1
fi

if [[ "${REMAINING_PROCESSABLE:-0}" -gt 0 ]]; then
    log "Some events remain scheduled; waiting one additional recovery window"
    sleep "$RECOVERY_WAIT_SECONDS"

    capture_outbox_counts \
        "$EVIDENCE_DIR/03_recovery_second_window.txt"
fi

# ---------------------------------------------------------------------------
# Drill 3: Failed-login attack
# ---------------------------------------------------------------------------

log "Starting failed-login attack drill"

AUTH_BEFORE="$(
    psql_query -tAc \
        "SELECT COUNT(*) FROM auth_audit_logs;" |
    tr -d '[:space:]'
)"

: > "$EVIDENCE_DIR/04_login_http_statuses.txt"

for attempt in $(seq 1 "$LOGIN_ATTEMPTS"); do
    correlation_id="login-attack-drill-${TIMESTAMP}-${attempt}"

    status_code="$(
        curl --silent \
            --output /dev/null \
            --write-out '%{http_code}' \
            --request POST "$AUTH_URL" \
            --header 'Content-Type: application/json' \
            --header "X-Correlation-ID: ${correlation_id}" \
            --data '{
                "email": "login-attack-drill@example.invalid",
                "password": "deliberately-invalid-password"
            }' || true
    )"

    printf '%s %s\n' "$attempt" "$status_code" \
        >> "$EVIDENCE_DIR/04_login_http_statuses.txt"
done

sleep 20

AUTH_AFTER="$(
    psql_query -tAc \
        "SELECT COUNT(*) FROM auth_audit_logs;" |
    tr -d '[:space:]'
)"

{
    echo "Before: $AUTH_BEFORE"
    echo "After: $AUTH_AFTER"
    echo "Increase: $((AUTH_AFTER - AUTH_BEFORE))"
} > "$EVIDENCE_DIR/04_auth_audit_delta.txt"

psql_query -c "
    SELECT *
    FROM auth_audit_logs
    ORDER BY id DESC
    LIMIT 60;
" > "$EVIDENCE_DIR/04_auth_audit_recent.txt"

docker compose logs --since="$START_TIME" app |
    grep -E \
        'login-attack-drill|authentication|login|credential|unauthorized' \
    > "$EVIDENCE_DIR/04_login_attack_app.log" || true

capture_prometheus_alerts \
    "$EVIDENCE_DIR/04_login_attack_alerts.json"

curl --fail --silent \
    'http://localhost:9090/api/v1/rules' \
    > "$EVIDENCE_DIR/04_login_attack_rules.json"

LOGIN_FAILURES="$((AUTH_AFTER - AUTH_BEFORE))"

if [[ "$LOGIN_FAILURES" -lt 1 ]]; then
    echo "Failed-login requests did not create authentication audit records." >&2
    exit 1
fi

# ---------------------------------------------------------------------------
# Final health and summary
# ---------------------------------------------------------------------------

log "Capturing final service health"

capture_service_status "$EVIDENCE_DIR/05_final_status.txt"

docker compose logs --since="$START_TIME" \
    > "$EVIDENCE_DIR/05_all_service_logs.txt"

docker compose logs --since="$START_TIME" |
    grep -Ei \
        'FATAL|permission denied|OutOfMemoryError|Application run failed' \
    > "$EVIDENCE_DIR/05_critical_errors.txt" || true

APP_FINAL_HEALTH="$(
    docker inspect spring_boot_lab_app \
        --format '{{.State.Health.Status}}'
)"

cat > "$EVIDENCE_DIR/SUMMARY.txt" <<EOF
Reliability Incident Drill Summary

Timestamp UTC: $(date -u +'%Y-%m-%dT%H:%M:%SZ')
Branch: $(git branch --show-current)
Commit: $(git rev-parse HEAD)

Kafka outage drill: PASS
Kafka events with scheduled retry: $KAFKA_RETRY_COUNT

Outbox backlog drill: PASS
Pending events observed during outage: $BACKLOG_COUNT
Events published after recovery: $RECOVERED_COUNT

Login attack drill: PASS
Failed-login audit increase: $LOGIN_FAILURES

Application final health: $APP_FINAL_HEALTH
Evidence directory: $EVIDENCE_DIR
EOF

if [[ "$APP_FINAL_HEALTH" != "healthy" ]]; then
    echo "Application was not healthy after the drills." >&2
    exit 1
fi

log "All reliability drills passed"
cat "$EVIDENCE_DIR/SUMMARY.txt"
