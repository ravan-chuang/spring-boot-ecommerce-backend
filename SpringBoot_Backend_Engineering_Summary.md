# Spring Boot Backend Engineering Summary

## Document Purpose

This document evaluates `ravan-chuang/spring-boot-ecommerce-backend` from the perspective of a professional backend engineer and system-design reviewer. It records the design intent, implementation mechanisms, verified evidence, operational boundaries, risks, and recommended evolution path. The baseline is the local working tree verified on 2026-08-05 at Git `HEAD ffbe94e`.

## Executive Summary

The project is a reliability-oriented Spring Boot e-commerce backend and engineering portfolio. It includes user identity and multi-device sessions, product catalog, cart, order, payment, a Transactional Outbox, Kafka retry and dead-letter topics, consumer idempotency, audit history, retention, metrics, dashboards, alerts, Docker Compose delivery, and repository governance.

Its engineering maturity comes from explicit failure-mode handling rather than feature count:

- Order or payment state and an outbox event commit atomically, eliminating a direct database/Kafka dual write.
- Duplicate HTTP payment requests are controlled by an order pessimistic lock, a payment uniqueness constraint, and a second idempotency lookup after lock acquisition.
- Duplicate Kafka deliveries are controlled by a processed-event marker persisted in the same transaction as the consumer side effect.
- Multiple outbox workers distribute work through `FOR UPDATE SKIP LOCKED`; a processing lease recovers abandoned claims.
- Retry exhaustion becomes an inspectable `FAILED` state with metrics, alerts, and an admin replay path.
- Short-lived JWTs are paired with hashed opaque refresh tokens, token rotation, and session revocation.
- 104 automated tests, a JaCoCo gate, and a regression baseline convert architectural claims into executable evidence.

Professional assessment: this is a strong and unusually complete junior backend or backend-internship portfolio. It demonstrates production thinking, but it should not be represented as a commercial production service. The main gaps are the public legacy user CRUD surface, generalized idempotency constraints, a DLT operator workflow, a real payment provider, highly available infrastructure, verified cloud delivery, and end-to-end operational evidence.

## 1. Verified Engineering Baseline

### 1.1 Build and Test Result

Verification ran in an isolated snapshot, preserving the original repository's `target/` artifacts and data:

```bash
JAVA_TOOL_OPTIONS="-javaagent:<byte-buddy-agent.jar>" \
  ./mvnw --batch-mode --no-transfer-progress clean verify

python3 scripts/coverage_baseline.py
```

| Verification | Result |
|---|---:|
| Build status | **SUCCESS** |
| Main Java source files | 97 |
| Test source files | 24 |
| Test suites | 23 |
| Tests | 104 |
| Passed / failed / errors / skipped | **104 / 0 / 0 / 0** |
| Total Maven time | 43.776 seconds |
| JaCoCo classes analyzed | 90 |
| Maven coverage gate | **Passed** |
| Coverage baseline comparison | **Passed** |

Testcontainers 1.21.4 started PostgreSQL 16 Alpine, Redis 7 Alpine, and Kafka 4.1.0. Mockito could not self-attach in the restricted JDK 25 environment, so Byte Buddy was loaded explicitly as a Java agent. This did not remove tests, change assertions, or relax quality gates.

### 1.2 Coverage

| Counter | Covered | Missed | Total | Coverage |
|---|---:|---:|---:|---:|
| Instruction | 5,102 | 1,160 | 6,262 | **81.48%** |
| Branch | 112 | 52 | 164 | **68.29%** |
| Line | 1,399 | 331 | 1,730 | **80.87%** |
| Complexity | 466 | 158 | 624 | **74.68%** |
| Method | 426 | 116 | 542 | **78.60%** |
| Class | 84 | 6 | 90 | **93.33%** |

The Maven gate requires instruction coverage of at least 70% and branch coverage of at least 50%. `config/coverage-baseline.json` records 81.4756% instruction and 68.2927% branch coverage and permits at most a 0.5 percentage-point regression. The current values matched the baseline and passed.

## 2. System Context and Architecture

```text
Client
  -> Caddy public edge
  -> Spring Boot REST API
  -> Spring Security / JWT
  -> Controllers and domain services
  -> PostgreSQL / Redis
  -> Transactional Outbox
  -> Scheduled outbox publisher
  -> Kafka + retry topics + DLT
  -> Idempotent consumers / audit tables

Spring Boot / Micrometer
  -> Prometheus
  -> Grafana
  -> Alertmanager -> optional Discord

Git push / pull request
  -> GitHub Actions clean verify
  -> JaCoCo gate
  -> Coverage baseline comparison
  -> test / coverage artifacts
  -> CodeQL and Dependabot governance
```

### 2.1 Component Inventory

| Layer or asset | Count | Responsibility |
|---|---:|---|
| Main Java source | 97 | Product code and configuration |
| Controllers | 7 | HTTP contracts and authorization boundaries |
| Services | 19 | Domain transactions, auth, outbox, Kafka, audit, cleanup, metrics |
| Repositories | 9 | JPA persistence, locks, and native claim queries |
| JPA entities | 9 | Commerce, payment, idempotency, outbox, and refresh tokens |
| Enums | 4 | Order, payment, method, and outbox state |
| DTOs | 24 | Validation, response mapping, and pagination |
| Exception files | 12 | Domain failures and global HTTP mapping |
| Flyway migrations | 8 | Deterministic schema evolution |
| Database tables | 12 | Business state, reliability state, and audit history |
| Route mappings | 30 | Authentication, commerce, and outbox administration |
| k6 scenarios | 6 | Load, stress, soak, arrival-rate, and concurrency testing |
| Grafana dashboards | 2 | Performance and outbox operations |

The project uses a conventional layered architecture, but its important properties are cross-layer invariants. Controllers establish validation and request identity, services own transactions and business rules, repositories express lock and claim semantics, and the outbox plus consumers turn cross-system failure into explicit state machines.

## 3. Domain Design

### 3.1 Product and Cache

Products support CRUD, pagination, and sorting. Individual reads use the Redis `products` cache; update and delete evict the affected key. `Product.version` provides optimistic locking so concurrent stock writes become explicit conflicts instead of lost updates.

Assessment:

- Strength: the read-through cache and mutation eviction model are simple and predictable.
- Boundary: list reads are not cached and no explicit TTL or stampede protection is defined.
- Recommendation: introduce TTL, list caching, warming, or event-based invalidation only after traffic evidence justifies the complexity.

### 3.2 Cart

Adding an existing product increments its quantity. Add and update operations validate current stock. The controller verifies owner-or-admin access, and the service confirms that a cart item belongs to the user in the request path, preventing cross-user item-ID access.

The cart stock check is early feedback, not a reservation. Checkout revalidates stock and uses optimistic locking in the order transaction. That responsibility split is correct for the current design.

### 3.3 Order

The order-creation transaction performs:

1. User and cart lookup.
2. Empty-cart and stock validation.
3. Order-header creation and immutable product name, price, and subtotal snapshots.
4. Product stock decrement.
5. Cart clearing.
6. `ORDER_CREATED` outbox insertion.

Historical order meaning remains stable when catalog names or prices change because order items store purchase-time snapshots. Cancellation is limited to `PENDING`, restores stock, and transitions the order to `CANCELLED`.

`createOrderFromCartSlow` intentionally sleeps for five seconds to reproduce optimistic-lock races. It is an educational endpoint; a production service should keep that delay in a concurrency test harness rather than the public API.

### 3.4 Payment and HTTP Idempotency

The payment transaction applies the following defenses:

- `Idempotency-Key` is mandatory.
- An initial key/path lookup returns a prior payment quickly during a normal retry.
- `OrderRepository.findByIdForUpdate` serializes payment mutations for one order.
- A second lookup after lock acquisition closes the check-then-act window.
- Only a `PENDING` order without a payment is accepted.
- `payments.order_id UNIQUE` prevents more than one payment per order.
- Payment, idempotency record, order `PAID` state, and `PAYMENT_PAID` outbox event commit together.

Concurrent integration coverage and the documented 30-request k6/PostgreSQL check demonstrate one logical payment for the same order and key.

The current `idempotency_records` migration contains a non-unique index, not a database unique key. A generalized contract should add:

- `UNIQUE (idempotency_key, request_path)`;
- request-body fingerprinting to reject the same key with a different payload;
- response status and body storage;
- creation, expiry, and cleanup lifecycle;
- owner or resource scope if keys apply across resource types.

## 4. Transactional Outbox Engineering

### 4.1 Rationale

Committing the database before publishing Kafka can lose an event when publish fails. Publishing Kafka first can expose an event for a database transaction that later rolls back. The Transactional Outbox commits the domain change and durable event together, replacing an impossible database/Kafka atomic write with an atomic database write followed by recoverable publication.

### 4.2 Lifecycle

| State | Meaning | Transition |
|---|---|---|
| `PENDING` | Durable and waiting for claim | Worker claim to `PROCESSING` |
| `PROCESSING` | Owned by one publisher instance | Publish, retry, failure, or lease recovery |
| `PUBLISHED` | Kafka acknowledged | Terminal success |
| `FAILED` | Attempt budget exhausted | Admin replay to `PENDING` |

### 4.3 Multi-Worker Safety

The native claim query is:

```sql
SELECT *
FROM outbox_events
WHERE status = 'PENDING'
ORDER BY created_at ASC
FOR UPDATE SKIP LOCKED
LIMIT :limit;
```

Each publisher instance receives a UUID at startup. The claim transaction records `processing_by` and `processing_at`, and the publisher verifies `(id, PROCESSING, processingBy)` before sending. This prevents a stale worker from publishing work that has been recovered and reassigned.

The processing lease defaults to 60 seconds. After a publisher crash, the scheduler returns expired `PROCESSING` rows to `PENDING`. Integration tests prove single ownership, disjoint multi-worker batches, and expired-lease recovery.

### 4.4 Failure Handling

- Local profile: three attempts for fast failure demonstrations.
- Production profile: ten attempts with more conservative Kafka settings.
- Retryable failure: increment the retry counter, retain `last_error`, and return to `PENDING`.
- Exhausted failure: transition to `FAILED` for operator, metric, and alert visibility.
- Recovery: an admin can inspect failed rows and replay one after the root cause is corrected.

Failure is modeled as durable state rather than only as log text. The main limitation is the absence of a per-event `next_attempt_at` field and exponential backoff. During a long downstream outage, the scheduler can retry failed events at its normal cadence.

## 5. Kafka Delivery and Consumer Idempotency

### 5.1 Delivery Model

- Domain topics: `order-created` and `payment-paid`.
- The producer key is the aggregate ID, preserving ordering for one aggregate within a partition.
- The outbox UUID is carried in the `outbox-event-id` record header.
- Listeners make four total attempts with 1, 2, and 4 second retry-topic delays.
- Exhausted messages reach a `-dlt` topic; the current `@DltHandler` logs terminal failure.

### 5.2 Idempotent Consumer Transaction

`processed_events` has primary key `(event_id, consumer_name)`. A consumer first executes `INSERT ... ON CONFLICT DO NOTHING`:

- a successful insert authorizes the business action;
- an existing row identifies a duplicate and skips the action;
- a business-action exception rolls back the entire transaction and marker, preserving retry safety.

The `ORDER_CREATED` consumer additionally writes `order_event_audit`; a unique event ID provides another duplicate side-effect guard. Records without the outbox header are handled for compatibility, but they cannot receive the same idempotency guarantee.

## 6. Security and Identity Engineering

### 6.1 Authentication Model

- Stateless Spring Security with a custom JWT filter.
- HMAC JWT access token with a 15-minute default TTL.
- BCrypt password hashing.
- 48-byte `SecureRandom` opaque refresh tokens.
- SHA-256 refresh-token hashes persisted instead of reusable raw secrets.
- Thirty-day default refresh lifetime with rotation and predecessor revocation.
- Stable session UUID plus device, IP, created, last-used, expiry, revoked, and replacement metadata.
- Current-session, selected-session, and all-session revocation operations.

This balances stateless request authentication with a stateful refresh-token lifecycle. Revoking a refresh session does not immediately invalidate an already issued access JWT; the default exposure window is limited by the 15-minute access-token TTL.

### 6.2 Authorization Model

- Product reads are public; product writes require `ADMIN`.
- Cart routes require authentication and path-user ownership or `ADMIN`.
- Order and payment routes require order ownership or `ADMIN`.
- Outbox administration and detailed Actuator metrics require `ADMIN`.
- Session routes act on the authenticated user's identity.

### 6.3 Audit and Detection

Authentication success and failure create `auth_audit_logs` rows. Failure audit uses `REQUIRES_NEW`, so the row survives rejection of the outer login transaction. `auth.events{action,outcome}` supports Prometheus alerting for repeated failures.

### 6.4 Critical Boundary

The legacy `/api/users/**` CRUD routes currently fall through to `anyRequest().permitAll()`. Unauthenticated clients can therefore read, modify, and delete user rows. This is a P0 production-readiness issue.

Required remediation:

1. Remove the legacy CRUD API or define explicit admin and self-service methods.
2. Prevent users from changing roles or credentials through generic update DTOs.
3. Eliminate behavior overlap with `/api/auth/register`.
4. Add anonymous, cross-user, role-escalation, and admin authorization tests.

Additional identity boundaries include no rate limiting, account lockout, password-reset flow, email verification, MFA, or immediate JWT denylist. A proxy deployment also needs a trusted forwarded-header policy before client IP can be treated as reliable audit evidence.

## 7. Data and Schema Engineering

### 7.1 Schema Ownership

Flyway owns the database schema. Hibernate runs with `ddl-auto=validate` and `open-in-view=false`, causing startup to fail when entity mappings and migrations disagree and keeping lazy persistence outside the HTTP rendering layer.

### 7.2 Tables

| Table | Responsibility | Key guarantee |
|---|---|---|
| `users` | Identity and role | Unique email |
| `products` | Catalog and stock | Optimistic-lock version |
| `cart_items` | Shopping cart | User and product indexes |
| `orders` | Order lifecycle | User index |
| `order_items` | Purchase-time snapshot | Order and product indexes |
| `payments` | Payment record | Unique order ID |
| `idempotency_records` | HTTP retry pointer | Non-unique key/path index |
| `outbox_events` | Outbound event lifecycle | Status and processing indexes |
| `processed_events` | Consumer deduplication | Composite primary key |
| `order_event_audit` | Consumer side effect | Unique event ID |
| `refresh_tokens` | Session and rotation chain | Unique token hash |
| `auth_audit_logs` | Security audit history | User/time and event/outcome indexes |

### 7.3 Migrations

1. V1: core commerce schema.
2. V2: outbox events.
3. V3: processing ownership and lease.
4. V4: processed-event idempotency.
5. V5: order-event audit.
6. V6: hashed refresh tokens.
7. V7: multi-device session metadata.
8. V8: authentication audit trail.

### 7.4 Retention

Scheduled cleanup removes processed-event markers after 30 days and order-event audit rows after 90 days by default. The code uses batch deletion and configuration-driven retention values. A full production policy must also cover authentication audit, user erasure, backups, legal retention, and capacity planning.

## 8. API and Error Contract

The project has 30 authentication, commerce, and outbox-admin route mappings, excluding Actuator and OpenAPI endpoints:

- Authentication and sessions: 7.
- Legacy user CRUD: 5.
- Product catalog: 5.
- Cart: 4.
- Orders: 5.
- Payments: 2.
- Outbox administration: 2.

Successful responses use `ApiResponse<T>`. Validation failures include a field-error map. Domain exceptions map to 400, 404, or 409, including illegal state, missing resources, idempotency conflict, stock conflict, and optimistic-lock conflict.

The API applies Bean Validation at request boundaries and keeps entity-to-response mapping in the application layer. The main contract issue is the public legacy user CRUD surface; the slow-order endpoint is a secondary lab-only concern.

## 9. Observability and Operations

### 9.1 Metrics and Dashboards

- HTTP request histograms plus P95 and P99.
- Outbox publish success and failure counters.
- Claimed-event and recovered-processing counters.
- Pending, processing, and failed outbox gauges.
- Authentication-event counters by action and outcome.
- Performance dashboard for JVM, HTTP latency, and throughput.
- Outbox dashboard for queue state, claims, recovery, publication, and failure.

### 9.2 Alerts

| Alert | Condition | Severity |
|---|---|---|
| Failed outbox event | Persists for 30 seconds | Critical |
| Publish failure | Any failure in five minutes | Warning |
| Pending backlog | Persists for five minutes | Warning |
| Application down | Scrape target down for one minute | Critical |
| Login failures | At least five in five minutes, sustained one minute | Warning |

Alertmanager groups by alert name, service, and severity and can send an optional Discord notification using a mounted webhook secret.

### 9.3 Incident Readiness

The project has good detection coverage for outbox failure and application availability, but incomplete response tooling. Missing pieces include a DLT inspection and replay workflow, explicit runbooks, trace correlation, backup/restore drills, escalation ownership, and alert-noise validation.

## 10. Automated Test Evidence

### 10.1 Coverage by Engineering Concern

The integration suite covers:

- Spring context, Flyway, and container wiring.
- Registration, login, token rotation, logout, per-session and all-session revocation.
- Auth success and failure audit plus security metrics.
- Public product reads, role-protected writes, pagination, and sorting.
- Complete cart-to-order-to-payment flows.
- Order and payment outbox creation.
- Concurrent payment requests with the same idempotency key.
- Outbox claim ownership, multi-worker distribution, lease recovery, publication, retry, and terminal failure.
- Kafka duplicate delivery, retry topics, DLT, and transaction rollback of processed-event markers.
- Admin replay and metrics authorization.
- Retention cleanup boundaries.

The unit and component suite covers service behavior, edge cases, exception mapping, ownership, stock validation, mapping, metrics, and stale outbox claims.

### 10.2 Full Suite Count

| Suite | Tests |
|---|---:|
| `SpringBootLabApplicationTests` | 1 |
| `AuthControllerIntegrationTest` | 8 |
| `OrderFlowIntegrationTest` | 2 |
| `OutboxAdminControllerIntegrationTest` | 2 |
| `OutboxMetricsAuthorizationIntegrationTest` | 2 |
| `PaymentControllerIntegrationTest` | 4 |
| `ProductControllerIntegrationTest` | 8 |
| `AuthAuditIntegrationTest` | 4 |
| `EventRetentionCleanupIntegrationTest` | 1 |
| `KafkaConsumerIdempotencyIntegrationTest` | 1 |
| `KafkaRetryDltIntegrationTest` | 1 |
| `OutboxEventClaimServiceIntegrationTest` | 3 |
| `OutboxEventPublisherIntegrationTest` | 1 |
| `OutboxEventPublisherRetryIntegrationTest` | 2 |
| `ProcessedEventServiceIntegrationTest` | 2 |
| `ProductControllerTest` | 6 |
| `GlobalExceptionHandlerTest` | 5 |
| `CartServiceTest` | 13 |
| `OrderServiceTest` | 9 |
| `OutboxEventPublisherTest` | 5 |
| `OutboxMetricsTest` | 3 |
| `PaymentServiceTest` | 13 |
| `ProductServiceTest` | 8 |
| **Total** | **104** |

The verified distribution is 62 unit/component tests and 42 integration tests. All 104 passed with zero failures, errors, or skipped tests.

## 11. Performance Evidence

### 11.1 Saved Catalog Baseline

The saved local Docker Compose result in `reports/performance-baseline.md` was produced on a MacBook Pro M3 Max with up to 50 VUs:

| Metric | Result |
|---|---:|
| Requests | 9,544 |
| Average throughput | 79.44 req/s |
| Average latency | 9.92 ms |
| P90 / P95 / P99 | 17.07 / 17.93 / 20.12 ms |
| Maximum latency | 47.17 ms |
| Failed requests | 0.00% |
| Check pass rate | 100.00% |

The script adds 300 ms of think time to every iteration. The result is a reproducible local regression reference, not a production capacity result.

### 11.2 Documented Payment Concurrency Verification

Existing project records, separate from the current Maven run, report:

| Metric | Result |
|---|---:|
| Concurrent requests | 30 |
| Success rate / HTTP failures | 100% / 0% |
| Average / P95 latency | 45.50 / 53.73 ms |
| Payment rows | 1 |
| Idempotency rows | 1 |
| Duplicate payments | 0 |

This supports the narrow claim that simultaneous requests for the same order and key converge on one payment. It does not prove a generalized idempotency contract for arbitrary payloads.

### 11.3 Load Assets Without Saved Results

The repository also defines catalog stress, 500-to-4,000 req/s arrival-rate, 3,000 req/s fixed-rate, and 2,500 req/s soak scenarios. Their thresholds are objectives. Because saved results are absent, those numbers must not be presented as achieved capacity.

## 12. Delivery and Repository Governance

### 12.1 Container Model

The multi-stage Dockerfile builds with a Java 25 JDK and runs on a Java 25 JRE. The image build skips tests because CI is the merge-time test authority.

### 12.2 Compose Topology

The local stack includes PostgreSQL 16, Redis 7, Kafka 4.1.2, the application, Prometheus, Grafana, Alertmanager, and Caddy. The production overlay removes direct host publication for internal services and exposes only Caddy. Caddy blocks public proxy access to Prometheus and detailed metric paths.

The topology is single-host and single-broker with replication factor 1. It is reproducible but not highly available. `depends_on` is startup ordering, not readiness. Production needs health checks, resource limits, backup/restore, managed secrets, and external or clustered stateful services.

### 12.3 GitHub Governance

- CI on main pushes and pull requests with Temurin JDK 25.
- `clean verify`, coverage gate, coverage regression check, and test summary.
- Test and coverage artifacts retained for 14 days.
- CodeQL Java/Kotlin `security-extended` analysis on push, PR, and schedule.
- Dependabot for Maven and GitHub Actions with dependency grouping.
- Repository permissions are read-only within the main CI job.

## 13. Cloud Deployment Status

Existing notes report a Tokyo OCI network, subnet, gateway, route, security group, public IPv4 address, VM, and SSH key. The application is not yet deployed and externally verified. The selected Oracle Linux 9 micro instance has one GB RAM, experiences an SSH banner-exchange timeout, and is undersized for the entire local Compose topology.

The repository does not contain infrastructure as code that independently proves or reconstructs the OCI state. The accurate status is infrastructure exploration, not completed deployment.

The next verifiable milestone should include:

1. A reachable and correctly sized host.
2. A minimal production topology with stateful systems externalized where practical.
3. Managed secrets, firewall rules, domain DNS, and HTTPS.
4. CI/CD deployment with health verification and rollback.
5. External evidence: health endpoint, logs, metrics, and a documented recovery exercise.

## 14. Engineering Risk Assessment

| Priority | Risk | Why it matters | Required direction |
|---|---|---|---|
| P0 | Public legacy user CRUD | Unauthenticated account reads and mutations | Remove or enforce explicit admin/self-service authorization |
| P0 | Environment-file secret dependence | Leaked secrets collapse trust boundaries | Managed secrets, rotation, and scanning |
| P1 | Non-unique idempotency key/path | General retries can remain ambiguous | Unique constraint, fingerprint, stored response, expiry |
| P1 | Single Kafka broker and RF=1 | No broker fault tolerance | Managed or clustered Kafka |
| P1 | DLT is log-only | Terminal messages lack recovery tooling | Inspection, replay, quarantine, and audit workflow |
| P1 | Simulated payment | No provider or webhook correctness evidence | Sandbox adapter and signed webhook verification |
| P1 | No immediate access-token invalidation | Revoked sessions retain issued JWTs until expiry | Short TTL, token version, or denylist |
| P2 | Incomplete readiness and resource policy | Startup races and noisy-neighbor risk | Health checks, limits, and graceful-shutdown testing |
| P2 | No distributed tracing | Cross-boundary diagnosis is slow | OpenTelemetry and context propagation |
| P2 | No abuse controls | Public and login endpoints can be attacked cheaply | Rate limiting, account policy, WAF |
| P2 | Incomplete data-governance policy | Privacy and capacity exposure | Classification, retention, erasure, and backup policy |

## 15. Professional Contribution Assessment

The project demonstrates more than framework familiarity:

- Transaction design: atomic business and event state, price snapshots, and state invariants.
- Concurrency: optimistic stock control, pessimistic payment locking, and multi-worker claims.
- Messaging: at-least-once delivery, retry topics, DLT, producer recovery, and consumer deduplication.
- Security: token lifecycle, hashing, rotation, session ownership, and failure audit.
- Operability: durable failure states, metrics, dashboards, alerting, cleanup, and replay.
- Quality engineering: 104 tests, containerized infrastructure, coverage gates, regression controls, and static analysis.
- Engineering judgment: documentation distinguishes verified results, configured thresholds, and unverified infrastructure claims.

This evidence is credible for a junior backend or backend-internship portfolio and provides strong interview material for transaction boundaries, message reliability, concurrency, and operational tradeoffs.

## 16. Prioritized Roadmap

### Phase 1 — Security and Correctness Closure

1. Secure or remove `/api/users/**` and add privilege-escalation tests.
2. Generalize idempotency with a unique key, request fingerprint, stored response, expiry, and cleanup.
3. Remove the slow-order endpoint from the normal API surface.
4. Define trusted proxy and forwarded-header behavior.

### Phase 2 — Operational Completeness

1. Build a DLT inspection, quarantine, replay, and operator-audit workflow.
2. Add exponential outbox backoff, jitter, and `next_attempt_at`.
3. Add OpenTelemetry, correlation IDs, structured logs, and alert runbooks.
4. Add health checks, resource limits, graceful shutdown, backup/restore drills, and SLOs.

### Phase 3 — Delivery and External Integration

1. Add infrastructure as code, domain TLS, protected CI/CD, external health checks, and rollback.
2. Externalize or cluster PostgreSQL, Redis, Kafka, and observability state.
3. Integrate a payment-provider sandbox through a provider adapter.
4. Verify signed webhooks, provider idempotency, reconciliation, and failure recovery.
5. Add image scanning, an SBOM, API contract tests, and a full end-to-end deployment test.

## Final Positioning Statement

`spring-boot-ecommerce-backend` is best described as a **backend engineering portfolio with production thinking, explicit reliability patterns, and comprehensive automated evidence**. Its strongest work is the coherent connection between database transactions, concurrency control, message delivery, idempotency, operational state, and test evidence. Its remaining limitations are known and documented, which makes the project more credible than an inflated production-readiness claim.
