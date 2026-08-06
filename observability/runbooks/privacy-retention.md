# Privacy and retention runbook

## Data classes

- User identity data: email, name, authentication audit metadata.
- Commerce data: carts, orders, payments, idempotency fingerprints.
- Operational event data: outbox payloads, processed-event IDs, DLT payloads/headers, correlation and trace IDs.

## Controls

Apply least-privilege database and ADMIN access. Avoid logging payloads, tokens, passwords, or webhook values. DLT data receives the same sensitivity classification as its source event.

The scheduled service currently retains processed-event identifiers for 30 days and order-event audit data for 90 days. Payment idempotency records are retained for 24 hours. Define and automate a DLT retention period after legal/business review; until then, review DLT age and delete only through an approved, audited procedure.

Run `scripts/privacy_retention_check.sh` for a read-only age/count report. Escalate before changing retention or deleting records.
