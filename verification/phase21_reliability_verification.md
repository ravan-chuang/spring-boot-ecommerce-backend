# Phase 2.1 Reliability Verification

## Automated verification

- Maven tests: 146 passed
- Failures: 0
- Errors: 0
- Skipped: 0
- JaCoCo instruction coverage: 89.78%
- JaCoCo branch coverage: 73.63%
- Coverage regression baseline: passed
- Docker Compose configuration validation: passed
- Git whitespace validation: passed
- Secret-pattern scan: no findings

## Outbox retry scheduling

- Flyway V11 applied successfully
- `next_attempt_at` column verified
- Partial pending-retry index verified
- Exponential backoff verified
- Bounded jitter verified
- Maximum retry-delay cap verified
- Events cannot be claimed before their scheduled retry time
- Due events can be claimed
- Concurrent `FOR UPDATE SKIP LOCKED` claims remain non-overlapping
- Replay resets an event for immediate processing
- Maximum-attempt exhaustion transitions an event to `FAILED`

## PostgreSQL backup and restore drill

Result: PASS

- PostgreSQL custom-format backup created
- SHA-256 checksum verified
- Backup restored into a disposable database
- Flyway migrations V1 through V11 verified
- Key table data restored
- Outbox retry schema and index verified
- DLT, audit, and idempotency constraints verified
- Disposable database removed after verification

## Incident drills

### Kafka outage

Result: PASS

- Kafka was stopped during active publishing
- At least one event entered scheduled retry
- No synthetic event was lost

### Outbox backlog and recovery

Result: PASS

- Pending events observed during outage: 54
- Events published after Kafka recovery: 55
- Backlog drained after recovery

### Failed-login attack

Result: PASS

- Invalid login attempts generated: 50
- Authentication audit increase: 50
- Application remained healthy

## Final state

- Application health: healthy
- Backup and restore drill: passed
- Kafka outage drill: passed
- Outbox backlog and recovery drill: passed
- Failed-login attack drill: passed
