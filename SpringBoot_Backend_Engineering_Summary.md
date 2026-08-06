# Spring Boot E-Commerce Backend - System Architecture and Engineering Summary

## Executive Summary

This repository is a production-minded, small-scale distributed event-driven backend built with Java 25 and Spring Boot 4.1.0. It combines secure authentication, commerce transactions, PostgreSQL consistency controls, Redis caching, Kafka delivery, a Transactional Outbox, idempotent consumers, governed dead-letter operations, end-to-end observability, and executable reliability drills.

The current release is `v1.3.0-phase21-reliability`, produced after PR #27 merged the Outbox retry scheduler and reliability-verification work into `main`. The correct professional positioning is **advanced student-level / strong junior backend and platform engineering evidence**. It demonstrates production reasoning and verified recovery behavior, but it is not yet a highly available or production-proven multi-node service.

## Verified Project Status

Verified on 2026-08-07:

| Evidence | Result |
|---|---:|
| Maven lifecycle | `clean verify` passed |
| Automated tests | 146 passed |
| Failures / errors / skipped | 0 / 0 / 0 |
| Instruction coverage | 89.78% |
| Branch coverage | 73.63% |
| Coverage regression baseline | Passed against 81.48% instruction / 68.29% branch, maximum 0.50-point drop |
| Flyway | V1-V11 applied and validated on PostgreSQL 16 |
| PostgreSQL backup and restore | Executed successfully against a disposable restore database |
| Kafka outage drill | Passed; scheduled retry observed |
| Outbox backlog and recovery | Passed; 54 pending during outage, 55 published after recovery |
| Failed-login drill | Passed; authentication audit increased by 50 |
| Final application health | Healthy |
| Phase 2.1 CI and CodeQL | Passed before merge |
| Release | `v1.3.0-phase21-reliability` |

The verification uses real PostgreSQL, Redis, and Kafka infrastructure through Testcontainers for automated integration testing and Docker Compose for operational drills. Coverage is treated as a regression indicator rather than a substitute for behavioral evidence.

## System Classification

The project qualifies as a **distributed event-driven backend system** because independent application, database, cache, broker, and telemetry processes communicate across network boundaries and must tolerate partial failure, duplicate delivery, delayed recovery, and inconsistent availability.

Its present topology remains intentionally limited:

- one Spring Boot application container;
- one Kafka broker;
- one PostgreSQL node;
- one Redis node;
- one Docker host;
- no orchestrator-level high availability.

It is therefore distribution-aware and production-minded, not a production-proven large-scale distributed platform.

## System Architecture

```text
Client
  -> Caddy edge
  -> Spring Security + JWT
  -> REST controllers and ownership authorization
  -> domain services
  -> PostgreSQL and Redis

Business transaction
  -> business rows + PENDING Outbox event in one PostgreSQL transaction
  -> due-time claim with FOR UPDATE SKIP LOCKED
  -> processing lease
  -> synchronous Kafka acknowledgement
  -> PUBLISHED, scheduled retry, or terminal FAILED

Consumer delivery
  -> retry topics
  -> transactionally persisted processed-event marker
  -> audited side effect
  -> DLT after retry exhaustion

Terminal Kafka failure
  -> persisted dead_letter_events evidence
  -> ADMIN inspection and quarantine
  -> database replay reservation
  -> replay to original topic and partition
  -> append-only dead_letter_audit_logs

Operations
  -> Prometheus + Alertmanager
  -> Grafana dashboards
  -> OpenTelemetry Collector + Tempo
  -> structured JSON logs + Alloy + Loki
  -> SLO recording rules and runbooks
```

PostgreSQL is the system of record for commerce state, token sessions, payment idempotency, Outbox delivery state, consumer deduplication, authentication audit, DLT state, and operator audit history.

## Engineering Contributions

### 1. Authentication, Authorization, and Session Security

- HMAC JWT access tokens with a 15-minute default lifetime.
- Opaque 30-day refresh tokens generated from secure randomness; only SHA-256 hashes are persisted.
- Refresh-token rotation, predecessor revocation, multi-device session listing, single-session revocation, and logout-all.
- BCrypt password hashing.
- USER / ADMIN role enforcement and resource-ownership checks.
- ADMIN-only collection-level legacy user management; self/admin item access; deny-by-default unmatched routes.
- Protected cart, order, payment, Outbox, DLT, and detailed Actuator operations.
- Authentication audit records and Micrometer action/outcome metrics.
- Failed authentication audit persistence independent of the rejected login transaction.

Remaining identity boundaries include rate limiting, temporary lockout, password reset, email verification, MFA, trusted proxy policy, and immediate access-token revocation.

### 2. Commerce Transaction Correctness

- Product catalog CRUD with Redis-backed detail reads and cache eviction.
- Optimistic locking on product stock.
- Cart ownership validation and stock revalidation during checkout.
- Order-item product name, unit price, and subtotal snapshots.
- Order creation, stock deduction, cart deletion, and `ORDER_CREATED` Outbox insertion in one transaction.
- Cancellation allowed only from `PENDING`; it restores stock and uses the same pessimistic order lock as payment.
- Payment allowed only from `PENDING`; one payment per order is protected by a database unique constraint.

### 3. Payment Idempotency

- Required `Idempotency-Key`.
- Unique `(idempotency_key, request_path)` invariant.
- SHA-256 request fingerprint rejects key reuse for a different request.
- Persisted response status, payment reference, response snapshot metadata, and 24-hour expiry.
- Double-checked replay lookup around the order lock.
- Concurrent duplicate requests resolve to one logical payment.

A dedicated repeated cancel-versus-pay contention drill remains a recommended hardening step before claiming production-proven terminal-transition behavior at scale.

### 4. Transactional Outbox and Multi-Worker Coordination

- Domain rows and their outbound event share one PostgreSQL commit.
- Lifecycle: `PENDING -> PROCESSING -> PUBLISHED`, with terminal `FAILED`.
- `FOR UPDATE SKIP LOCKED` supports cooperating publishers without overlapping claims.
- `processing_by` and `processing_at` establish claim ownership and a recoverable lease.
- Expired processing leases return abandoned records to `PENDING`.
- `EventProducer` waits for Kafka acknowledgement before publication is marked successful.
- ADMIN failed-event inspection and replay are available.

### 5. Scheduled Retry Policy

Flyway V11 adds `next_attempt_at` and the partial index `idx_outbox_events_pending_next_attempt`.

The retry policy provides:

- immediate first-attempt eligibility;
- exponential growth from a configurable base delay;
- a configurable maximum-delay cap;
- bounded jitter to reduce synchronized retry bursts;
- persistent scheduling in `next_attempt_at`;
- due-time filtering in the claim query;
- replay reset to immediate eligibility;
- transition to `FAILED` after the maximum attempt count.

Dedicated unit and integration tests cover delay growth, jitter bounds, maximum capping, scheduling state, due-time claims, concurrent non-overlap, publication failure, and terminal exhaustion.

### 6. At-Least-Once Consumer Safety

- Kafka non-blocking retry topics delay subsequent attempts before DLT routing.
- The producer attaches `outbox-event-id` and correlation headers.
- Consumers persist `(event_id, consumer_name)` in `processed_events`.
- The processed marker and business side effect share one transaction.
- A failed side effect rolls back the marker so retry remains valid.
- Duplicate delivery produces one marker and one audited business effect.

### 7. Governed Dead-Letter Operations

`V10__create_dead_letter_operations.sql` persists terminal broker evidence and operator history.

Captured evidence includes:

- DLT and original topic, partition, and offset;
- message key and payload;
- bounded Base64 Kafka headers;
- Outbox event ID and correlation ID;
- exception class and message;
- lifecycle state, optimistic version, actors, timestamps, and replay attempts.

State transitions:

```text
RECEIVED -> QUARANTINED -> REPLAYING -> REPLAYED
                         -> QUARANTINED on send failure
REPLAYING -> QUARANTINED on expired replay lease
```

Controls include coordinate deduplication, ADMIN-only APIs, required reasons, transition locking, database reservation before send, original destination preservation, failure recovery, scheduled lease recovery, lifecycle metrics, and append-only operator audit history.

### 8. Correlation, Tracing, and Structured Logging

- `X-Correlation-ID` is validated, normalized, returned to the caller, and stored in MDC.
- Correlation flows through Outbox state, Kafka headers, consumers, and DLT evidence.
- MDC state is restored or removed in `finally` blocks.
- OpenTelemetry exports sampled OTLP/HTTP traces through the Collector to Tempo.
- Production console output uses structured Logstash-compatible JSON.
- `traceId`, `spanId`, and `correlationId` link traces and logs in Grafana.
- Normal consumer and DLT logs avoid payload output.

### 9. Metrics, SLOs, Alerts, and Runbooks

The system exposes HTTP, JVM, HikariCP, authentication, Outbox, and DLT metrics. Grafana provisions Prometheus, Loki, and Tempo data sources and reliability/security dashboards.

| Indicator | Objective |
|---|---:|
| HTTP availability | 99.5% over 30 days |
| HTTP P95 latency | <= 750 ms over 5 minutes |
| DLT review | No `RECEIVED` record older than 15 minutes |
| Replay lease | No `REPLAYING` record beyond two 60-second leases |

Seven recording rules compute request rate, multi-window 5xx ratios, availability, and P95 latency. Eleven alert rules cover application availability, authentication failures, Outbox failure/backlog, DLT review/replay, latency, and fast/slow error-budget burn.

Runbooks cover application down, Outbox failure, DLT operations, high error rate, PostgreSQL backup/restore, secret rotation, and privacy/retention.

### 10. Executed Reliability and Recovery Drills

#### Kafka outage

- Kafka was stopped.
- Five synthetic events were inserted.
- Events entered `PROCESSING` while the synchronous send waited for broker failure.
- At least one event returned to `PENDING` with an increased retry count and scheduled `next_attempt_at`.
- Result: PASS.

#### Outbox backlog and recovery

- 50 additional synthetic events were inserted while Kafka remained unavailable.
- 54 pending events were observed during the outage.
- Kafka was restarted.
- 55 events were published after recovery and the backlog drained.
- Result: PASS.

#### Failed-login activity

- 50 invalid login attempts were generated.
- `auth_audit_logs` increased by 50.
- Final application health remained healthy.
- Result: PASS.

#### PostgreSQL backup and restore

- A custom-format backup was created.
- SHA-256 checksum verification passed.
- The backup was restored into a disposable `_restore_verify` database.
- Flyway V1-V11, core row counts, the retry column/index, and DLT/idempotency constraints were verified.
- The scratch database was removed by the cleanup trap.
- Result: PASS.

These drills validate local recovery mechanics. They do not establish a commercial production RPO, RTO, or multi-node availability guarantee.

## API and Persistence Surface

The repository exposes authentication, user, product, cart, order, payment, Outbox administration, DLT administration, and Actuator mappings.

Flyway migrations:

```text
V1  Initial commerce schema
V2  Transactional Outbox
V3  Outbox processing lease
V4  Processed-event deduplication
V5  Order event audit
V6  Refresh tokens
V7  Refresh-token sessions
V8  Authentication audit logs
V9  Payment idempotency hardening
V10 Persisted DLT operations and correlation IDs
V11 Outbox retry scheduling and due-time index
```

Hibernate validates the schema rather than mutating it automatically.

## Test Evidence

The 146-test suite covers:

- authentication, refresh rotation, logout, session control, and audit;
- anonymous, self-service, cross-user, USER, and ADMIN authorization boundaries;
- product, cart, order, and payment service/controller behavior;
- payment replay, fingerprint conflict, database uniqueness, and concurrency;
- stock optimistic locking;
- Outbox creation, claiming, due-time filtering, publication, scheduling, terminal failure, lease recovery, and ADMIN replay;
- retry-policy backoff, jitter, and maximum capping;
- Kafka retry-to-DLT behavior;
- DLT capture, header bounds, coordinate deduplication, state transitions, operator audit, replay success/failure, metrics, and lease recovery;
- consumer idempotency and rollback semantics;
- event retention cleanup;
- correlation-ID validation, propagation, MDC cleanup, and operator visibility;
- health probes, metrics authorization, Flyway startup, and application context.

No test was skipped. The final local `clean verify` completed successfully.

## Performance Evidence

### Catalog read baseline

- 9,544 requests.
- 79.44 requests/second average throughput.
- 9.92 ms average latency.
- P95 17.93 ms; P99 20.12 ms.
- No failed requests.

### High-rate local soak

- Five minutes at 2,500 requests/second.
- 750,000 requests.
- 2,499.91 requests/second achieved.
- P95 0.84 ms; P99 1.11 ms.
- 0.07% client-side failure rate.
- No observed application-side 5xx responses.

### Concurrent payment idempotency

- 30 concurrent requests.
- 100% successful HTTP responses.
- One payment row and one idempotency row.
- No duplicate payments.

These are saved local profiles, not a production capacity claim. The reliability changes should be included in any future write-path and recovery-throughput benchmark.

## Delivery and Repository Governance

- Maven Wrapper and Java 25 build.
- Multi-stage Dockerfile with a JRE runtime image.
- Docker Compose for PostgreSQL, Redis, Kafka, application, Prometheus, Alertmanager, Grafana, OpenTelemetry Collector, Tempo, Loki, and Alloy.
- Health checks for stateful dependencies and the application.
- Graceful shutdown with a 30-second phase timeout.
- Production-style overlay with Caddy as the public edge.
- GitHub Actions CI, CodeQL, Dependabot, workflow artifacts, and manual dispatch.
- Coverage regression gate and secret-pattern checks.
- Guarded backup/restore and reliability scripts.
- PR #27 squash merge and release tag `v1.3.0-phase21-reliability`.

## Production Boundaries and Risk Register

| Priority | Boundary | Required next step |
|---|---|---|
| P0 | Environment/file-based secrets remain unsuitable for production | External secret manager, workload identity, rotation, and scanning |
| P1 | Single application, Kafka, PostgreSQL, and Redis nodes | Replicated or managed HA topology and failover drills |
| P1 | Cloud deployment is not verified | Infrastructure as code, domain TLS, deployment health gate, rollback, and external checks |
| P1 | Payment provider is simulated | Sandbox adapter, signed webhooks, reconciliation, and provider idempotency |
| P1 | No production RPO/RTO | Scheduled off-host backups, restore cadence, timing evidence, and retention policy |
| P2 | No rate limiting or account lockout | Gateway/application controls and security tests |
| P2 | Partial retention lifecycle | Approved erasure automation for DLT, audit, Outbox, refresh, and idempotency data |
| P2 | Telemetry retention and cost not tuned | Representative traffic, sampling policy, and storage budgets |
| P2 | Dedicated repeated cancel/pay race evidence not archived | High-iteration PostgreSQL contention test and invariant checks |

## Professional Assessment

The project is strongest where implementation, state, tests, and operational evidence meet:

- the Outbox includes claim ownership, due-time scheduling, leases, backoff, jitter, terminal state, replay, metrics, alerts, and outage evidence;
- DLT handling includes persisted evidence, governance states, replay reservation, audit, recovery, and metrics;
- payment idempotency combines locking, unique invariants, fingerprinting, persisted replay data, and concurrency verification;
- at-least-once consumers use transactionally persisted deduplication rather than in-memory checks;
- correlation connects HTTP, database state, Kafka, consumers, DLT, logs, and traces;
- recovery claims are supported by executed drills rather than configuration alone.

Recommended positioning:

> A production-minded distributed event-driven e-commerce backend built with Java 25 and Spring Boot 4, featuring secure sessions, transactional consistency, scheduled Outbox retry, Kafka at-least-once safety, governed DLT recovery, cross-boundary observability, executable incident drills, and automated quality gates.

This is strong evidence for backend, Java, platform, DevOps/SRE, or event-driven systems internships and junior roles. It does not replace experience operating a multi-node commercial service.

## Recommended Next Phase

1. Deploy to Kubernetes with multiple application replicas, health gates, rolling updates, autoscaling, and rollback tests.
2. Add cloud infrastructure as code, IAM, a managed secret store, domain TLS, and external synthetic checks.
3. Use replicated or managed PostgreSQL, Kafka, and Redis and execute node/broker/failover chaos drills.
4. Run write-heavy, million-event Kafka, recovery-throughput, and long-duration soak tests.
5. Define RPO/RTO, MTTR, error-budget review, incident timelines, and postmortem practice.
6. Integrate a real payment sandbox, signed webhooks, and reconciliation.
7. Add SBOM generation, image/dependency scanning, retention automation, privacy erasure, and dedicated cancel/pay race verification.
