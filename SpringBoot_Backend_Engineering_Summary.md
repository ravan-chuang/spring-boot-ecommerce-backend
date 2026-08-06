# Spring Boot E-Commerce Backend - System Architecture and Engineering Summary

## Executive Summary

This repository is a production-minded backend engineering portfolio, not a CRUD-only demonstration. It combines secure identity and session control, e-commerce transaction workflows, database concurrency, durable event delivery, idempotent processing, governed dead-letter recovery, observability, service-level objectives, operational runbooks, and infrastructure-backed automated verification.

The engineering emphasis is on behavior under failure: a committed order must not lose its event, duplicate requests and messages must not duplicate business effects, concurrent workers must coordinate safely, terminal Kafka messages must be inspectable and recoverable, and operators must have metrics, traces, logs, audit history, alerts, and written procedures.

## Verified Project Status

Verified locally on August 6, 2026:

| Evidence | Verified result |
|---|---:|
| Main Java source files | 118 |
| Test Java source files | 32 |
| Controller mappings | 35 |
| Flyway migrations | 10 |
| Automated tests | 135 passed |
| Failures / errors / skipped | 0 / 0 / 0 |
| Instruction coverage | 89.62% (7,517 / 8,388) |
| Branch coverage | 73.19% (202 / 276) |
| Line coverage | 89.27% (2,081 / 2,331) |
| JaCoCo gate | Instruction >= 70%; branch >= 50% |
| Coverage regression check | Passed |
| Flyway integration | V1-V10 applied on PostgreSQL 16 |
| Prometheus validation | 11 alert rules and 7 recording rules valid |
| OTel / Tempo / Loki / Alloy | Configurations valid against pinned binaries |
| Docker Compose | Merged configuration valid |
| Targeted tracked-secret scan | Passed |

The Maven `clean verify` run used PostgreSQL, Redis, and Kafka Testcontainers. The committed regression baseline is 81.48% instruction coverage and 68.29% branch coverage with a maximum permitted drop of 0.50 percentage points; the current result is above both baselines.

GitHub Actions is configured to execute Maven verification on pushes and pull requests, publish Surefire and JaCoCo evidence, and enforce the coverage gate. CodeQL Java scanning and Dependabot maintenance are also configured. This document distinguishes verified local evidence from remote deployment claims.

## System Architecture

```text
Client
  -> Caddy reverse proxy
  -> Spring Boot REST API
  -> Spring Security / JWT / ownership authorization
  -> Domain services
  -> PostgreSQL and Redis

Business transaction
  -> business rows + PENDING outbox event in one transaction
  -> SKIP LOCKED publisher claim + processing lease
  -> Kafka
  -> retry topics
  -> idempotent consumer
  -> processed-event marker + audited side effect

Terminal Kafka failure
  -> dead-letter topic
  -> persisted dead_letter_events record
  -> ADMIN inspect / quarantine / replay
  -> dead_letter_audit_logs

Metrics
  -> Prometheus recording and alert rules
  -> Grafana dashboards / Alertmanager / Discord

Traces
  -> OpenTelemetry Collector
  -> Tempo
  -> Grafana

Structured container logs
  -> Grafana Alloy
  -> Loki
  -> Grafana
```

The architecture separates presentation, security, domain services, persistence, messaging, and operations. PostgreSQL is the source of truth for business state, outbox delivery state, consumer deduplication, security audit data, and DLT operator state.

## Engineering Contributions

### 1. Authentication, Authorization, and Session Security

- Stateless JWT access authentication with a 15-minute default lifetime.
- Opaque 30-day refresh tokens; only SHA-256 token hashes are stored.
- Refresh-token rotation and revocation.
- Multi-device session listing, single-session revocation, and logout-all behavior.
- BCrypt password hashing.
- USER / ADMIN role enforcement.
- Resource-ownership checks for carts, orders, payments, and user records.
- ADMIN-only product mutation, outbox operations, DLT operations, and detailed metrics.
- Authentication audit records and action/outcome metrics.
- Failed authentication audits persist in an independent transaction.

### 2. E-Commerce Transaction Correctness

- Product catalog CRUD with Redis-backed reads.
- Cart item creation, listing, update, and removal.
- Order creation, listing, cancellation, and ownership enforcement.
- Payment creation and retrieval.
- Product stock uses optimistic locking to detect conflicting writes.
- Payment requires an `Idempotency-Key` and stores a request fingerprint.
- Reusing a key with a different request is rejected as an idempotency conflict.
- Concurrent duplicate payment requests resolve to one logical payment.

### 3. Transactional Outbox and Multi-Worker Coordination

- Business rows and outbound events commit in one PostgreSQL transaction.
- Outbox lifecycle: `PENDING -> PROCESSING -> PUBLISHED`, with terminal `FAILED`.
- PostgreSQL `FOR UPDATE SKIP LOCKED` allows cooperating publisher instances to claim separate work.
- A processing lease recovers work abandoned by a stopped publisher.
- Retry limits prevent infinite hot-loop delivery.
- ADMIN endpoints inspect FAILED records and return them to PENDING for replay.
- Outbox records now persist `correlation_id`, which is exposed through the ADMIN response and propagated to Kafka.

### 4. At-Least-Once Consumer Safety

- Kafka retry topics use delayed non-blocking retries before DLT routing.
- The publisher attaches an `outbox-event-id` header.
- Consumers persist `(event_id, consumer_name)` in `processed_events`.
- The processed marker and business side effect share one database transaction.
- A failed side effect rolls back its marker so a later retry remains valid.
- Duplicate delivery produces one processed marker and one order audit side effect.

### 5. Governed Dead-Letter Operations

The DLT handler persists operational evidence instead of relying only on broker retention. `V10__create_dead_letter_operations.sql` adds the outbox correlation column and creates `dead_letter_events` plus `dead_letter_audit_logs`.

Captured data includes:

- DLT and original topic, partition, and offset;
- message key and payload;
- bounded Base64-encoded Kafka headers;
- outbox event ID and correlation ID;
- exception class and message;
- state, optimistic version, timestamps, operator IDs, and replay attempts.

State transitions are explicit:

```text
RECEIVED -> QUARANTINED -> REPLAYING -> REPLAYED
                         -> QUARANTINED on send failure
REPLAYING -> QUARANTINED when the replay lease expires
```

Controls include:

- unique DLT topic/partition/offset intake deduplication;
- ADMIN-only list, detail, audit, quarantine, and replay endpoints;
- required operator reasons;
- pessimistic transition locks and JPA optimistic versioning;
- database reservation before Kafka replay;
- replay to the original topic and partition with original identity/correlation headers;
- failure recovery to quarantine;
- scheduled recovery of expired 60-second replay leases;
- Micrometer state gauges and lifecycle counters;
- append-only operator audit history.

### 6. Correlation, Tracing, and Structured Logging

- `X-Correlation-ID` is normalized on every HTTP request and returned to the caller.
- Unsafe or missing values are replaced with UUIDs.
- The correlation ID is stored in MDC, persisted with the outbox event, sent as a Kafka header, restored in consumer MDC, and retained with a DLT record.
- MDC context is restored or removed in `finally` blocks to prevent thread-pool leakage.
- Spring Boot's OpenTelemetry starter exports sampled OTLP/HTTP traces through the Collector to Tempo in the production profile.
- Production sampling defaults to 10% and is environment-configurable.
- Production console output uses Logstash-compatible structured JSON.
- `traceId`, `spanId`, and `correlationId` support log/trace navigation in Grafana.
- Normal consumer and DLT logs avoid payload output.

### 7. Metrics, SLOs, Alerts, and Runbooks

Prometheus receives Actuator/Micrometer metrics for HTTP, JVM, database-pool, authentication, outbox, and DLT behavior. Grafana provisions Prometheus, Loki, and Tempo data sources plus reliability/security and performance/capacity dashboards.

Current service objectives:

| Indicator | Objective |
|---|---:|
| HTTP availability | 99.5% over 30 days |
| HTTP P95 latency | <= 750 ms over 5 minutes |
| DLT review | No RECEIVED record older than 15 minutes |
| Replay lease | No REPLAYING record beyond two 60-second leases |

Seven Prometheus recording rules compute request rate, 5xx ratios across multiple windows, availability, and P95 latency. Eleven alert rules cover application availability, authentication failure activity, outbox failure/backlog, DLT review/replay, and fast/slow availability error-budget burn. Critical operational alerts link directly to repository runbooks.

Runbooks cover:

- application down;
- outbox failure;
- DLT inspection, quarantine, and replay;
- high error rate and latency;
- PostgreSQL backup and restore verification;
- secret rotation;
- privacy and retention.

### 8. Operational Safety and Deployment Preparation

- Docker Compose includes PostgreSQL, Redis, Kafka, the application, Prometheus, Alertmanager, Grafana, OpenTelemetry Collector, Tempo, Loki, and Alloy.
- PostgreSQL, Redis, Kafka, and the application have health checks.
- The application waits on healthy stateful dependencies.
- Graceful shutdown uses a 30-second phase timeout.
- The production overlay removes direct host ports from internal services and exposes only Caddy.
- Environment templates contain names/placeholders, while `.env` and `observability/secrets/` are ignored.
- PostgreSQL backup output includes a SHA-256 checksum.
- Restore verification refuses any database name that does not end in `_restore_verify`, requires explicit opt-in, validates Flyway/core table evidence, and removes the scratch database.
- The secret-check script searches tracked files without printing credential values.
- Privacy/retention checks are read-only.

The operational scripts passed shell syntax validation and the tracked-secret scan. A live production backup/restore drill has not been claimed; it must be executed and recorded against an approved disposable environment.

### 9. Automated Quality and Repository Governance

- Maven Wrapper and Java 25 build.
- Spring Boot 4.1.0 application lifecycle.
- JUnit, MockMvc, Mockito, and Testcontainers.
- Real PostgreSQL 16, Redis 7, and Kafka integration behavior.
- JaCoCo reports, hard quality gates, and a committed regression baseline.
- Docker Compose configuration validation.
- Prometheus `promtool` validation.
- Pinned-binary validation for OpenTelemetry Collector, Tempo, Loki, and Alloy configurations.
- GitHub Actions CI, CodeQL, Dependabot, and pull-request-oriented changes.

## API and Persistence Surface

The repository contains 35 explicit controller mappings across authentication, user, product, cart, order, payment, outbox administration, and DLT administration.

Flyway migrations:

```text
V1  Initial commerce schema
V2  Transactional outbox
V3  Outbox processing lease
V4  Processed-event deduplication
V5  Order event audit
V6  Refresh tokens
V7  Refresh-token sessions
V8  Authentication audit logs
V9  Payment idempotency hardening
V10 Persisted dead-letter operations and correlation IDs
```

Hibernate uses schema validation rather than automatic schema mutation.

## Test Evidence

The 135-test suite covers:

- authentication, refresh rotation, logout, session control, and auth auditing;
- USER / ADMIN authorization and resource ownership;
- product, cart, order, and payment service/controller behavior;
- payment idempotency, concurrent duplicates, and conflict detection;
- optimistic stock locking;
- transactional outbox creation, claiming, publication, retry limits, FAILED state, lease recovery, and ADMIN replay;
- Kafka retry-to-DLT behavior;
- DLT persistence, header/coordinate parsing, duplicate capture, ADMIN authorization, state transitions, audit history, replay success/failure, metrics, and lease recovery;
- consumer idempotency and audited side effects;
- event retention cleanup;
- correlation-ID validation, MDC cleanup, outbox persistence, Kafka header propagation, and ADMIN visibility;
- Prometheus metric authorization and application context startup.

No test was skipped. The final `clean verify` completed successfully.

## Performance Evidence

### Catalog read baseline

MacBook Pro M3 Max, Docker Compose, `GET /api/products`:

| Metric | Result |
|---|---:|
| Requests | 9,544 |
| Average throughput | 79.44 req/s |
| Average latency | 9.92 ms |
| P95 latency | 17.93 ms |
| P99 latency | 20.12 ms |
| Failed requests | 0.00% |

### High-rate local soak profile

| Metric | Result |
|---|---:|
| Target duration/rate | 5 minutes at 2,500 req/s |
| Requests | 750,000 |
| Achieved throughput | 2,499.91 req/s |
| P95 latency | 0.84 ms |
| P99 latency | 1.11 ms |
| Client-side failure rate | 0.07% |
| Dropped iterations | 0 |
| Observed application-side 5xx | None |

### Concurrent payment idempotency profile

| Metric | Result |
|---|---:|
| Concurrent requests sharing one key | 30 |
| HTTP success | 30 / 30 |
| Average latency | 45.50 ms |
| P95 / P99 / max | 53.73 / 55.76 / 56.25 ms |
| Returned payment IDs | All identical |
| Payment rows / idempotency rows | 1 / 1 |

These are reproducible local engineering baselines, not production capacity, SLA, or commercial-scale claims.

## Cloud Deployment Status and Boundaries

OCI networking and an Oracle Linux VM were provisioned in Tokyo with a VCN, public subnet, internet gateway, route, security group, public IPv4 address, and SSH keys. However, application deployment is not verified: SSH reached TCP port 22 but timed out during banner exchange, and the 1 GB `VM.Standard.E2.1.Micro` instance is undersized for the complete application/data/messaging/observability stack.

The system should therefore be presented as production-minded and deployment-ready at the repository level, not as a production service already operating in the cloud. The next cloud step is a stable, appropriately sized host or managed services, verified SSH/management access, a resource budget, external health evidence, protected deployment/rollback, off-host encrypted backups, and an external secret manager.

## Professional Assessment

The project demonstrates junior-to-early-career backend engineering skills beyond framework familiarity:

- layered system design and explicit authorization boundaries;
- transaction, concurrency, and idempotency correctness;
- durable event delivery and at-least-once safety;
- governed operator recovery with auditability;
- real-infrastructure integration testing;
- logs, metrics, traces, SLOs, alerts, and runbooks;
- CI, security scanning, dependency governance, and honest operational boundaries.

Its contribution is not a novel distributed-systems algorithm. Its engineering value is the coherent, tested integration of established patterns into one e-commerce workflow with observable and recoverable failure behavior.

## Recommended Next Phase

1. Execute and record the disposable PostgreSQL backup/restore drill.
2. Rotate any exposed development credentials and move deployment values to an external secret manager.
3. Establish a stable, adequately sized cloud environment and verify private service networking behind Caddy/HTTPS.
4. Add protected CI/CD deployment, rollback, and external health checks.
5. Define and automate DLT/auth audit retention after privacy and legal review.
6. Add API contract tests, broader end-to-end tests, and a payment-provider sandbox adapter.
7. Add production trace tail sampling, retention/cost controls, and alert tuning from real traffic.
8. Add rate limiting/account lockout and chaos/recovery exercises.
