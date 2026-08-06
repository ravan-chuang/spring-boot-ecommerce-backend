# High error rate and latency runbook

## Trigger

Use this runbook for availability error-budget burns or sustained P95 latency above 750 ms.

## Triage

1. Confirm request volume so low-traffic ratios are interpreted correctly.
2. Break down 5xx rate and latency by endpoint, outcome, and exception without creating high-cardinality labels.
3. Follow the affected trace from Grafana to structured logs through `traceId` or `correlationId`.
4. Check database saturation/locks, Redis, Kafka, JVM memory/GC, and recent deployments.

## Recovery

Roll back or disable the smallest responsible change when safe. Scale or repair a saturated dependency, then verify both the short and long alert windows are recovering. Do not close the incident on a single successful request.
