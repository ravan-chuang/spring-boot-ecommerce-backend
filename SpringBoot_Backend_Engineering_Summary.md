# Spring Boot Backend Project - System Architecture & Engineering Contributions

## Executive Summary

This project is a production-oriented backend engineering portfolio rather than a CRUD demonstration. It focuses on reliability, distributed-system behavior, security, concurrency correctness, observability, automated verification, and operational clarity.

### Verified status

- 66 automated tests passed in the verified full build.
- GitHub Actions CI runs Maven `clean verify` on pushes and pull requests.
- JaCoCo coverage enforcement is part of the Maven lifecycle.
- CodeQL Java scanning and Dependabot maintenance are configured.
- PostgreSQL, Redis, and Kafka integration tests run through Testcontainers.
- Transactional Outbox, Kafka retry/DLT, consumer idempotency, and operator replay are implemented.
- Concurrent payment and outbox-worker behavior is covered by integration tests.
- Local and production-style Docker Compose configurations are included.
- OCI networking and a VM have been provisioned, but cloud application deployment is not yet verified.

## System Architecture

```text
Client
  -> Caddy reverse proxy
  -> Spring Boot REST API
  -> Spring Security and JWT
  -> Business services
  -> PostgreSQL / Redis
  -> Transactional Outbox
  -> Kafka
  -> Idempotent consumers / retry topics / dead-letter topics

Spring Boot metrics
  -> Prometheus
  -> Grafana
  -> Alertmanager

Repository changes
  -> GitHub Actions verification
  -> JaCoCo quality gate
  -> CodeQL security analysis
  -> Dependabot update pull requests
```

The architecture separates presentation, security, domain services, persistence, messaging, and operations. Business state and outbound events share one PostgreSQL transaction, while publication and consumption are independently recoverable.

## Engineering Contributions

### 1. Security and Session Control

- Stateless JWT access authentication.
- Opaque refresh tokens with stored SHA-256 hashes.
- Refresh-token rotation.
- Multi-device session listing and revocation.
- BCrypt password hashing.
- Role-based authorization and resource-ownership checks.
- Authentication audit logging and failure metrics.

### 2. Durable Event Delivery

- Transactional Outbox eliminates the unsafe database/Kafka dual-write gap.
- `FOR UPDATE SKIP LOCKED` supports cooperating publisher workers.
- Processing leases recover work left by interrupted publishers.
- Retry limits produce an explicit terminal `FAILED` state.
- Admin APIs support failed-event inspection and replay.
- Kafka retry and dead-letter topics make terminal failures observable.

### 3. Idempotency and At-Least-Once Safety

- Payment requests accept an `Idempotency-Key`.
- Database constraints and locking ensure one logical payment for concurrent duplicate requests.
- Consumers persist processed-event identifiers to suppress duplicate side effects.
- Retry and replay paths remain safe under repeated delivery.

### 4. Concurrency Correctness

Verified integration behavior includes:

- concurrent payment requests sharing one key create one payment;
- concurrent outbox workers coordinate without publishing the same row twice;
- duplicate Kafka delivery does not duplicate business effects;
- optimistic locking detects conflicting stock updates.

These are executable tests, not only design claims.

### 5. Observability and Incident Handling

- Actuator and Micrometer instrumentation.
- Prometheus scraping and alert rules.
- Provisioned Grafana dashboards.
- Alertmanager routing with optional Discord notifications.
- Authentication and outbox lifecycle metrics.
- Reproducible Kafka outage/recovery and suspicious-login workflows.
- k6 load, stress, soak, arrival-rate, and payment-idempotency scenarios.

### 6. Quality and Repository Governance

- Maven Wrapper for reproducible builds.
- GitHub Actions CI on pushes and pull requests.
- JaCoCo coverage reporting, Coverage Baseline regression tracking, and gate enforcement.
- CodeQL Java scanning.
- Dependabot for Maven and GitHub Actions dependencies.
- A policy that defers breaking Testcontainers major upgrades to a dedicated migration.
- Pull-request-oriented changes with green checks before merge.

## Practical System Value

Compared with a typical student backend, this project contributes:

- durable distributed event delivery;
- explicit failure states and replay;
- duplicate-request and duplicate-message safety;
- transaction and concurrency correctness;
- real-infrastructure integration testing;
- observable operational behavior;
- automated quality and security controls;
- reproducible deployment and performance evidence.

The contribution is not a novel database or messaging algorithm. Its originality lies in combining these patterns into one coherent, tested e-commerce workflow and making failure behavior inspectable.

## Performance Evidence

A documented local catalog-read scenario on a MacBook Pro M3 Max and Docker Compose produced:

| Metric | Result |
|---|---:|
| Requests | 9,544 |
| Average throughput | 79.44 req/s |
| Average latency | 9.92 ms |
| P95 latency | 17.93 ms |
| P99 latency | 20.12 ms |
| Failed requests | 0.00% |

This is a reproducible local baseline, not a production-capacity claim.

## Cloud Deployment Status

OCI infrastructure work completed:

- Tokyo-region VCN;
- regional public subnet;
- internet gateway and default route;
- attached network security group;
- ingress rules for SSH, HTTP, HTTPS, and the temporary application port;
- public IPv4 assignment;
- generated SSH key pair.

Current limitation:

- the created instance is Oracle Linux 9 on `VM.Standard.E2.1.Micro` with 1 GB RAM;
- TCP port 22 is reachable, but SSH times out during banner exchange before authentication;
- the application and supporting stack have not been deployed to OCI;
- the VM is too small for Spring Boot, PostgreSQL, Redis, Kafka, Prometheus, and Grafana together.

The correct next step is to repair or replace the VM, verify SSH access, then deploy a resource-budgeted production stack with external health-check evidence.

## Professional Assessment

This project is suitable for backend internship and junior backend engineering portfolios because it demonstrates more than framework familiarity. It provides evidence of:

- layered system design;
- secure session management;
- distributed messaging patterns;
- database consistency and concurrency control;
- infrastructure-backed integration testing;
- observability and failure recovery;
- CI, security scanning, and dependency governance;
- clear recognition of production boundaries.

It should be presented as a strong production-minded portfolio system, not as a production service already operating at commercial scale.

## Priority Roadmap

1. Establish a stable cloud host and verified SSH access.
2. Deploy the private-network Compose stack behind Caddy.
3. Add domain-based HTTPS and remove direct public access to port `8080`.
4. Add protected CI/CD deployment and rollback.
5. Add OpenTelemetry tracing and correlation IDs.
6. Add backup/restore drills.
7. Add contract and end-to-end tests.
8. Add a payment-provider sandbox adapter.


---

# Verified Payment Idempotency Load Test

## Objective
Validate payment idempotency under 30 concurrent duplicate requests.

## Environment
- Spring Boot
- PostgreSQL 16
- Docker Compose
- k6
- JWT Authentication

## Workload
- Endpoint: POST /api/orders/{orderId}/payments
- VUs: 30
- Iterations: 1/VU
- Same Idempotency-Key

## Results

|Metric|Value|
|---|---:|
|HTTP Success|30/30|
|Failure Rate|0.00%|
|Average Latency|45.50 ms|
|P95|53.73 ms|
|P99|55.76 ms|
|Max|56.25 ms|
|Returned Payment IDs|All identical (ID=8)|

## Database Verification

- Payment rows: 1
- Idempotency rows: 1
- Payment status: PAID
- Payment method: CREDIT_CARD

## Conclusion

Thirty concurrent duplicate requests generated exactly one logical payment and one idempotency record, demonstrating correct idempotent behavior for the tested workload.
