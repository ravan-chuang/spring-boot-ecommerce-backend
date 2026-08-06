# Outbox failure runbook

## Trigger

Use this runbook for `OutboxFailedEvents`, publish failures, or a persistent PENDING backlog.

## Triage

1. Check Kafka broker health and the producer error in structured application logs.
2. Inspect `/api/admin/outbox/failed` with an ADMIN account.
3. Correlate the outbox event using `correlationId`, `aggregateId`, and the trace in Grafana.
4. Confirm the failure is transient and that replay cannot duplicate an unsafe downstream side effect.

## Recovery

Fix the dependency or payload issue first. Replay only FAILED records through the ADMIN endpoint. Confirm the status becomes PENDING and then PUBLISHED, and verify consumer idempotency/audit evidence.

Never edit an outbox payload directly in the database during incident response.
