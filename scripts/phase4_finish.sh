#!/usr/bin/env bash
set -Eeuo pipefail

NS="spring-boot-lab"
CLUSTER="postgres-ha"
RW_SERVICE="postgres-ha-rw"

WORKDIR="/tmp/phase4-final"
mkdir -p "$WORKDIR"

cleanup() {
  if [ -n "${OLD_PRIMARY_NODE:-}" ]; then
    running=$(
      docker inspect -f '{{.State.Running}}' "$OLD_PRIMARY_NODE"         2>/dev/null || true
    )

    if [ "$running" != "true" ]; then
      docker start "$OLD_PRIMARY_NODE" >/dev/null 2>&1 || true
    fi
  fi
}

trap cleanup EXIT

echo "============================================================"
echo " Phase 4 FINAL — synchronous primary hard-failure validation"
echo "============================================================"
echo

# ------------------------------------------------------------
# 0. Baseline validation
# ------------------------------------------------------------

echo "[0/9] Validating clean synchronous baseline..."

READY=$(
  kubectl get cluster "$CLUSTER" \
    -n "$NS" \
    -o jsonpath='{.status.readyInstances}'
)

INSTANCES=$(
  kubectl get cluster "$CLUSTER" \
    -n "$NS" \
    -o jsonpath='{.status.instances}'
)

PRIMARY=$(
  kubectl get cluster "$CLUSTER" \
    -n "$NS" \
    -o jsonpath='{.status.currentPrimary}'
)

if [ "$READY" != "3" ] || [ "$INSTANCES" != "3" ]; then
  echo "ERROR: cluster baseline is not 3/3."
  kubectl get cluster "$CLUSTER" -n "$NS"
  exit 1
fi

PRIMARY_NODE=$(
  kubectl get pod "$PRIMARY" \
    -n "$NS" \
    -o jsonpath='{.spec.nodeName}'
)

echo "Primary      : $PRIMARY"
echo "Primary node : $PRIMARY_NODE"

SYNC_NAMES=$(
  kubectl exec \
    -n "$NS" \
    "$PRIMARY" \
    -- \
    psql -U postgres -d postgres \
      -tAc 'SHOW synchronous_standby_names;' \
      2>/dev/null \
    | xargs
)

SYNC_COMMIT=$(
  kubectl exec \
    -n "$NS" \
    "$PRIMARY" \
    -- \
    psql -U postgres -d postgres \
      -tAc 'SHOW synchronous_commit;' \
      2>/dev/null \
    | xargs
)

echo "synchronous_standby_names = $SYNC_NAMES"
echo "synchronous_commit        = $SYNC_COMMIT"

if [ "$SYNC_COMMIT" != "on" ]; then
  echo "ERROR: synchronous_commit is not ON."
  exit 1
fi

echo
kubectl cnpg status "$CLUSTER" -n "$NS"
echo

# ------------------------------------------------------------
# 1. Pick a client node different from the primary node
# ------------------------------------------------------------

echo "[1/9] Selecting independent probe node..."

CLIENT_NODE=""

while IFS='|' read -r pod node; do
  [ -n "$pod" ] || continue
  [ "$pod" = "$PRIMARY" ] && continue
  [ "$node" = "$PRIMARY_NODE" ] && continue

  CLIENT_NODE="$node"
  break
done < <(
  kubectl get pods \
    -n "$NS" \
    -l cnpg.io/cluster="$CLUSTER" \
    -o jsonpath='{range .items[*]}{.metadata.name}{"|"}{.spec.nodeName}{"\n"}{end}'
)

if [ -z "$CLIENT_NODE" ]; then
  echo "ERROR: could not find a surviving node for the client probe."
  exit 1
fi

echo "Probe node: $CLIENT_NODE"

RUN_ID="phase43-sync-primary-$(date +%s)"
echo "$RUN_ID" > "$WORKDIR/run_id"

echo "Run ID: $RUN_ID"

# ------------------------------------------------------------
# 2. Ensure benchmark table exists
# ------------------------------------------------------------

echo
echo "[2/9] Preparing durable failover probe table..."

kubectl exec \
  -n "$NS" \
  "$PRIMARY" \
  -- \
  psql -U postgres -d spring_boot_lab \
  -v ON_ERROR_STOP=1 \
  -c "
CREATE TABLE IF NOT EXISTS phase4_primary_failover_probe (
    id BIGSERIAL PRIMARY KEY,
    run_id TEXT NOT NULL,
    seq BIGINT NOT NULL,
    client_ts TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE(run_id, seq)
);

ALTER TABLE phase4_primary_failover_probe OWNER TO ravan;
ALTER SEQUENCE phase4_primary_failover_probe_id_seq OWNER TO ravan;
"

# ------------------------------------------------------------
# 3. Create continuous write probe
# ------------------------------------------------------------

echo
echo "[3/9] Starting continuous synchronous write probe..."

kubectl delete pod phase4-primary-failover-client \
  -n "$NS" \
  --ignore-not-found \
  --wait=true >/dev/null 2>&1 || true

cat > "$WORKDIR/probe.yaml" <<YAML
apiVersion: v1
kind: Pod
metadata:
  name: phase4-primary-failover-client
  namespace: ${NS}
spec:
  restartPolicy: Never

  nodeSelector:
    kubernetes.io/hostname: ${CLIENT_NODE}

  containers:
    - name: client
      image: postgres:17

      env:
        - name: PGUSER
          valueFrom:
            secretKeyRef:
              name: postgres-ha-app
              key: username

        - name: PGPASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-ha-app
              key: password

      command:
        - bash
        - -c
        - |
          seq=1

          while [ ! -f /tmp/stop ]; do
            ts=\$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")

            if output=\$(
              timeout 5 \
              psql \
                -h ${RW_SERVICE} \
                -U "\$PGUSER" \
                -d spring_boot_lab \
                -v ON_ERROR_STOP=1 \
                -Atc "
                  INSERT INTO phase4_primary_failover_probe(
                      run_id,
                      seq
                  )
                  VALUES (
                      '${RUN_ID}',
                      \$seq
                  );

                  SELECT \$seq;
                " 2>&1
            ); then

              echo "\${ts}|seq=\${seq}|STATUS=COMMITTED|\${output}"

            else

              rc=\$?
              compact=\$(printf '%s' "\$output" | tr '\n' ' ')

              echo "\${ts}|seq=\${seq}|STATUS=FAILED|rc=\${rc}|\${compact}"
            fi

            seq=\$((seq + 1))
            sleep 0.25
          done

          echo "\$(date -u +"%Y-%m-%dT%H:%M:%S.%3NZ")|PROBE_STOPPED"
YAML

kubectl apply -f "$WORKDIR/probe.yaml"

kubectl wait \
  -n "$NS" \
  --for=condition=Ready \
  pod/phase4-primary-failover-client \
  --timeout=90s

sleep 8

kubectl logs \
  -n "$NS" \
  phase4-primary-failover-client \
  --tail=10

BASELINE_COMMITS=$(
  kubectl logs \
    -n "$NS" \
    phase4-primary-failover-client \
  | grep -c 'STATUS=COMMITTED' || true
)

if [ "$BASELINE_COMMITS" -lt 5 ]; then
  echo "ERROR: insufficient successful baseline writes."
  exit 1
fi

echo "Baseline commits observed: $BASELINE_COMMITS"

# ------------------------------------------------------------
# 4. Hard stop the PRIMARY NODE
# ------------------------------------------------------------

echo
echo "[4/9] Injecting hard PRIMARY NODE failure..."

OLD_PRIMARY="$PRIMARY"
OLD_PRIMARY_NODE="$PRIMARY_NODE"

date +%s > "$WORKDIR/failure_epoch"
date -u +"%Y-%m-%dT%H:%M:%SZ" > "$WORKDIR/failure_iso"

echo "Failure UTC : $(cat "$WORKDIR/failure_iso")"
echo "Old primary : $OLD_PRIMARY"
echo "Victim node : $OLD_PRIMARY_NODE"

docker stop "$OLD_PRIMARY_NODE"

echo
echo "Waiting for first recovered committed write..."

# ------------------------------------------------------------
# 5. Wait for actual client recovery
# ------------------------------------------------------------

RECOVERED="false"
DEADLINE=$(( $(date +%s) + 300 ))

while [ "$(date +%s)" -lt "$DEADLINE" ]; do

  kubectl logs \
    -n "$NS" \
    phase4-primary-failover-client \
    > "$WORKDIR/live.log" 2>/dev/null || true

  FAILURE_EPOCH=$(cat "$WORKDIR/failure_epoch")

  if python3 - "$WORKDIR/live.log" "$FAILURE_EPOCH" <<'PY'
import re
import sys
from datetime import datetime, timezone

path = sys.argv[1]
failure_epoch = int(sys.argv[2])

pat = re.compile(
    r'^([^|]+)\|seq=(\d+)\|STATUS=(COMMITTED|FAILED)\|'
)

records = []

try:
    lines = open(path).read().splitlines()
except FileNotFoundError:
    sys.exit(1)

for line in lines:
    m = pat.match(line)
    if not m:
        continue

    try:
        dt = datetime.fromisoformat(
            m.group(1).replace("Z", "+00:00")
        )
    except ValueError:
        continue

    if dt.timestamp() >= failure_epoch:
        records.append((dt, m.group(3)))

failure_seen = False

for dt, status in records:
    if status == "FAILED":
        failure_seen = True
        continue

    if failure_seen and status == "COMMITTED":
        sys.exit(0)

sys.exit(1)
PY
  then
    RECOVERED="true"
    break
  fi

  sleep 2
done

if [ "$RECOVERED" != "true" ]; then
  echo "ERROR: no recovered client commit within 300 seconds."
  kubectl get cluster "$CLUSTER" -n "$NS" || true
  exit 1
fi

echo "Recovered client commit detected."

# Let recovery stabilize and collect a few more successful commits.
sleep 10

# Stop the client cleanly. This prevents DB/log race during reconciliation.
kubectl exec \
  -n "$NS" \
  phase4-primary-failover-client \
  -- \
  touch /tmp/stop

kubectl wait \
  -n "$NS" \
  --for=jsonpath='{.status.phase}'=Succeeded \
  pod/phase4-primary-failover-client \
  --timeout=30s || true

kubectl logs \
  -n "$NS" \
  phase4-primary-failover-client \
  > "$WORKDIR/client.log"

# ------------------------------------------------------------
# 6. Measure client-visible RTO
# ------------------------------------------------------------

echo
echo "[5/9] Calculating client-visible availability..."

python3 - "$WORKDIR/client.log" "$WORKDIR/failure_epoch" \
  > "$WORKDIR/client-analysis.txt" <<'PY'
import re
import sys
from datetime import datetime, timezone

log_path = sys.argv[1]
failure_epoch = int(open(sys.argv[2]).read().strip())
failure = datetime.fromtimestamp(
    failure_epoch,
    tz=timezone.utc
)

pattern = re.compile(
    r'^([^|]+)\|seq=(\d+)\|STATUS=(COMMITTED|FAILED)\|'
)

records = []

for line in open(log_path):
    m = pattern.match(line.strip())
    if not m:
        continue

    try:
        ts = datetime.fromisoformat(
            m.group(1).replace("Z", "+00:00")
        )
    except ValueError:
        continue

    records.append({
        "ts": ts,
        "seq": int(m.group(2)),
        "status": m.group(3),
    })

after = [r for r in records if r["ts"] >= failure]
failures = [r for r in after if r["status"] == "FAILED"]
commits = [r for r in after if r["status"] == "COMMITTED"]

print("=== Phase 4.3-C Client Availability ===")
print("Failure injection:", failure.isoformat())
print("Parsed records:", len(records))
print("Post-failure attempts:", len(after))
print("Post-failure failures:", len(failures))
print("Post-failure commits:", len(commits))

if failures:
    first_failure = failures[0]
    last_failure = failures[-1]

    recovered = [
        r for r in commits
        if r["ts"] > last_failure["ts"]
    ]

    print()
    print(
        "First failure:",
        first_failure["ts"].isoformat(),
        "seq=",
        first_failure["seq"]
    )
    print(
        "Last failure:",
        last_failure["ts"].isoformat(),
        "seq=",
        last_failure["seq"]
    )

    if recovered:
        first_recovered = recovered[0]

        print(
            "First recovered commit:",
            first_recovered["ts"].isoformat(),
            "seq=",
            first_recovered["seq"]
        )

        print(
            "Client-visible RTO:",
            round(
                (
                    first_recovered["ts"]
                    - first_failure["ts"]
                ).total_seconds(),
                3
            ),
            "seconds"
        )

        print(
            "Failure-injection-to-recovery:",
            round(
                (
                    first_recovered["ts"]
                    - failure
                ).total_seconds(),
                3
            ),
            "seconds"
        )

else:
    print()
    print("No failed client operations were observed.")

    if commits:
        print(
            "First post-failure commit:",
            commits[0]["ts"].isoformat()
        )
PY

cat "$WORKDIR/client-analysis.txt"

# ------------------------------------------------------------
# 7. Identify new primary + RPO reconciliation
# ------------------------------------------------------------

echo
echo "[6/9] Reconciling acknowledged writes against promoted primary..."

echo "Waiting for CloudNativePG primary promotion..."

NEW_PRIMARY=""
DEADLINE=$(( $(date +%s) + 300 ))

while [ "$(date +%s)" -lt "$DEADLINE" ]; do

  CANDIDATE=$(
    kubectl get cluster "$CLUSTER" \
      -n "$NS" \
      -o jsonpath='{.status.currentPrimary}' \
      2>/dev/null || true
  )

  if [ -n "$CANDIDATE" ] && [ "$CANDIDATE" != "$OLD_PRIMARY" ]; then

    if kubectl exec \
      -n "$NS" \
      "$CANDIDATE" \
      -- \
      psql -U postgres -d postgres \
      -tAc 'SELECT NOT pg_is_in_recovery();' \
      2>/dev/null \
      | grep -qx 't'
    then
      NEW_PRIMARY="$CANDIDATE"
      break
    fi
  fi

  sleep 2
done

if [ -z "$NEW_PRIMARY" ]; then
  echo "ERROR: no promoted primary became reachable within 300 seconds."
  kubectl get cluster "$CLUSTER" -n "$NS" || true
  kubectl get pods \
    -n "$NS" \
    -l cnpg.io/cluster="$CLUSTER" \
    -o wide || true
  exit 1
fi

echo "Old primary: $OLD_PRIMARY"
echo "New primary: $NEW_PRIMARY"

echo "$NEW_PRIMARY" > "$WORKDIR/new_primary"

kubectl exec \
  -n "$NS" \
  "$NEW_PRIMARY" \
  -- \
  psql \
    -U postgres \
    -d spring_boot_lab \
    -Atc "
SELECT seq
FROM phase4_primary_failover_probe
WHERE run_id='${RUN_ID}'
ORDER BY seq;
" \
  > "$WORKDIR/db-sequences.txt"

if [ ! -s "$WORKDIR/db-sequences.txt" ]; then
  echo "ERROR: DB reconciliation export is empty."
  exit 1
fi

python3 - \
  "$WORKDIR/client.log" \
  "$WORKDIR/db-sequences.txt" \
  > "$WORKDIR/rpo-analysis.txt" <<'PY'
import re
import sys

log_path = sys.argv[1]
db_path = sys.argv[2]

pattern = re.compile(
    r'\|seq=(\d+)\|STATUS=(COMMITTED|FAILED)\|'
)

acked = set()
failed = set()

for line in open(log_path):
    m = pattern.search(line)
    if not m:
        continue

    seq = int(m.group(1))

    if m.group(2) == "COMMITTED":
        acked.add(seq)
    else:
        failed.add(seq)

db = {
    int(line.strip())
    for line in open(db_path)
    if line.strip().isdigit()
}

lost_acknowledged = acked - db
present_without_ack = db - acked

print("=== Phase 4.3-C RPO Reconciliation ===")
print("Client acknowledged :", len(acked))
print("Client failed       :", len(failed))
print("DB rows             :", len(db))

if acked:
    print("Last acknowledged seq:", max(acked))

if db:
    print("Highest DB seq       :", max(db))

print()
print(
    "Acknowledged but missing from DB:",
    sorted(lost_acknowledged)
)

print(
    "Present in DB without COMMITTED acknowledgement:",
    sorted(present_without_ack)
)

print()

if lost_acknowledged:
    print(
        "RESULT: DATA LOSS OBSERVED — "
        f"{len(lost_acknowledged)} acknowledged transaction(s) missing"
    )
    sys.exit(2)

print(
    "RESULT: OBSERVED RPO = "
    "0 ACKNOWLEDGED TRANSACTIONS LOST"
)
PY

RPO_RC=$?

cat "$WORKDIR/rpo-analysis.txt"

if [ "$RPO_RC" -eq 2 ]; then
  echo
  echo "WARNING: acknowledged data loss detected."
  echo "Evidence retained; continuing cluster restoration."
fi

# ------------------------------------------------------------
# 8. Restore failed primary node / wait 3-of-3
# ------------------------------------------------------------

echo
echo "[7/9] Restoring failed worker node..."

docker start "$OLD_PRIMARY_NODE"

kubectl wait \
  --for=condition=Ready \
  node/"$OLD_PRIMARY_NODE" \
  --timeout=180s

echo "Waiting for CNPG to return to 3/3 healthy..."

DEADLINE=$(( $(date +%s) + 300 ))

while [ "$(date +%s)" -lt "$DEADLINE" ]; do

  READY=$(
    kubectl get cluster "$CLUSTER" \
      -n "$NS" \
      -o jsonpath='{.status.readyInstances}' \
      2>/dev/null || true
  )

  STATUS=$(
    kubectl get cluster "$CLUSTER" \
      -n "$NS" \
      -o jsonpath='{.status.phase}' \
      2>/dev/null || true
  )

  printf 'ready=%s status=%s\n' "$READY" "$STATUS"

  if [ "$READY" = "3" ] && [ "$STATUS" = "Cluster in healthy state" ]; then
    break
  fi

  sleep 5
done

READY=$(
  kubectl get cluster "$CLUSTER" \
    -n "$NS" \
    -o jsonpath='{.status.readyInstances}'
)

if [ "$READY" != "3" ]; then
  echo "ERROR: cluster did not return to 3/3."
  exit 1
fi

kubectl cnpg status "$CLUSTER" \
  -n "$NS" \
  > "$WORKDIR/final-cnpg-status.txt"

cat "$WORKDIR/final-cnpg-status.txt"

# ------------------------------------------------------------
# 9. Generate durable verification evidence
# ------------------------------------------------------------

echo
echo "[8/9] Writing Phase 4 verification evidence..."

FAILURE_ISO=$(cat "$WORKDIR/failure_iso")
NEW_PRIMARY=$(cat "$WORKDIR/new_primary")

CLIENT_SUMMARY=$(
  sed 's/^/> /' "$WORKDIR/client-analysis.txt"
)

RPO_SUMMARY=$(
  sed 's/^/> /' "$WORKDIR/rpo-analysis.txt"
)

cat > verification/phase4/postgresql_synchronous_primary_failover.md <<EOF
# Phase 4.3 — PostgreSQL Synchronous Primary Hard-Failure Verification

## Scope

This experiment validates client-visible write availability and
acknowledged-transaction durability when the worker hosting the
CloudNativePG primary instance is abruptly stopped.

The experiment was executed in the local multi-worker kind environment.
It is not a claim of production multi-zone availability.

## Baseline

- CloudNativePG cluster: \`${CLUSTER}\`
- PostgreSQL instances: 3
- Ready instances: 3
- Original primary: \`${OLD_PRIMARY}\`
- Original primary worker: \`${OLD_PRIMARY_NODE}\`
- Synchronous commit: \`${SYNC_COMMIT}\`
- Synchronous standby policy: \`${SYNC_NAMES}\`
- Probe worker: \`${CLIENT_NODE}\`
- Run ID: \`${RUN_ID}\`

Prior to failure injection, both replicas were streaming and the cluster
was healthy.

## Failure Injection

Failure timestamp:

\`${FAILURE_ISO}\`

The Docker container representing worker:

\`${OLD_PRIMARY_NODE}\`

was stopped directly, without drain or graceful database switchover.

This represents an abrupt primary-node loss in the tested kind
environment.

## Primary Recovery

Old primary:

\`${OLD_PRIMARY}\`

Promoted primary:

\`${NEW_PRIMARY}\`

The client remained connected through the CloudNativePG read/write
service \`${RW_SERVICE}\`.

## Client-Visible Availability

${CLIENT_SUMMARY}

Client RTO is defined here as the interval from the first observed failed
write attempt to the first subsequently acknowledged write.

It is intentionally distinct from Kubernetes node detection,
CloudNativePG promotion, endpoint convergence, and full 3-instance
capacity restoration.

## RPO Reconciliation

${RPO_SUMMARY}

Observed RPO is evaluated only against writes for which the client
received a successful COMMITTED acknowledgement.

## Final State

After failover evidence was captured, the original worker was restarted.

CloudNativePG returned to:

- instances: 3
- ready instances: 3
- status: Cluster in healthy state

## Interpretation

This test demonstrates the behavior of quorum synchronous replication
under one abrupt primary-node failure in the tested local environment.

The result should not be generalized to cross-zone latency, managed
Kubernetes control planes, network partitions, storage-system failures,
or disaster recovery.

Normal-operation synchronous write latency was measured separately with
200 writes:

- mean: 22.380 ms
- median: 22.145 ms
- p95: 23.939 ms
- p99: 33.732 ms
- minimum: 19.202 ms
- maximum: 52.201 ms

A separate standby-loss experiment verified that the \`ANY 1\`
synchronous quorum policy continued accepting writes after one
synchronous standby became unavailable.
EOF

# Also retain compact machine-oriented results.
cp "$WORKDIR/client-analysis.txt" \
  verification/phase4/phase43_client_availability.txt

cp "$WORKDIR/rpo-analysis.txt" \
  verification/phase4/phase43_rpo_reconciliation.txt

cp "$WORKDIR/final-cnpg-status.txt" \
  verification/phase4/phase43_final_cnpg_status.txt

echo
echo "[9/9] Final checks..."

kubectl get cluster "$CLUSTER" -n "$NS"
echo
kubectl get pods \
  -n "$NS" \
  -l cnpg.io/cluster="$CLUSTER" \
  -o wide

echo
echo "Generated:"
find verification/phase4 -maxdepth 1 -type f -print | sort

echo
echo "============================================================"
echo " Phase 4 final experiment completed"
echo "============================================================"
