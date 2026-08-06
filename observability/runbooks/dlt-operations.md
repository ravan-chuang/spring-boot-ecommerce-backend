# Dead-letter operations runbook

## State model

`RECEIVED -> QUARANTINED -> REPLAYING -> REPLAYED`. A failed send or expired replay lease returns the record to `QUARANTINED`. Every operator transition is audited with actor, reason, and timestamp.

## Inspection and quarantine

1. List records at `GET /api/admin/dlt/events?status=RECEIVED`.
2. Inspect the original topic/partition/offset, exception, correlation ID, bounded headers, and payload sensitivity.
3. Correct the underlying code, schema, or dependency problem.
4. Quarantine with `POST /api/admin/dlt/events/{id}/quarantine` and JSON `{"reason":"..."}`.

## Replay

Replay only after confirming that the original consumer is fixed and the business operation is idempotent. Call `POST /api/admin/dlt/events/{id}/replay` with a specific reason. Verify `REPLAYED`, inspect the audit endpoint, and confirm downstream business state.

## Safety

- Do not replay directly from the Kafka CLI; that bypasses authorization and audit history.
- Do not modify stored payloads or offsets.
- Treat payloads and headers as potentially sensitive; restrict ADMIN access and incident exports.
- If the replay remains `REPLAYING`, allow lease recovery to return it to quarantine before retrying.
