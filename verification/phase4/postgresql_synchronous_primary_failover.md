# Phase 4.3 — PostgreSQL Synchronous Primary Hard-Failure Verification

## Scope

This experiment validates client-visible write availability and
acknowledged-transaction durability when the worker hosting the
CloudNativePG primary instance is abruptly stopped.

The experiment was executed in the local multi-worker kind environment.
It is not a claim of production multi-zone availability.

## Baseline

- CloudNativePG cluster: `postgres-ha`
- PostgreSQL instances: 3
- Ready instances: 3
- Original primary: `postgres-ha-1`
- Original primary worker: `spring-boot-ha-worker`
- Synchronous commit: `on`
- Synchronous standby policy: `ANY 1 (postgres-ha-2,postgres-ha-3,postgres-ha-1)`
- Probe worker: `spring-boot-ha-worker2`
- Run ID: `phase43-sync-primary-1786203734`

Prior to failure injection, both replicas were streaming and the cluster
was healthy.

## Failure Injection

Failure timestamp:

`2026-08-08T15:42:53Z`

The Docker container representing worker:

`spring-boot-ha-worker`

was stopped directly, without drain or graceful database switchover.

This represents an abrupt primary-node loss in the tested kind
environment.

## Primary Recovery

Old primary:

`postgres-ha-1`

Promoted primary:

`postgres-ha-2`

The client remained connected through the CloudNativePG read/write
service `postgres-ha-rw`.

## Client-Visible Availability

> === Phase 4.3-C Client Availability ===
> Failure injection: 2026-08-08T15:42:53+00:00
> Parsed records: 89
> Post-failure attempts: 63
> Post-failure failures: 17
> Post-failure commits: 46
>
> First failure: 2026-08-08T15:42:53.925000+00:00 seq= 30
> Last failure: 2026-08-08T15:43:44.187000+00:00 seq= 46
> First recovered commit: 2026-08-08T15:43:45.468000+00:00 seq= 47
> Client-visible RTO: 51.543 seconds
> Failure-injection-to-recovery: 52.468 seconds

Client RTO is defined here as the interval from the first observed failed
write attempt to the first subsequently acknowledged write.

It is intentionally distinct from Kubernetes node detection,
CloudNativePG promotion, endpoint convergence, and full 3-instance
capacity restoration.

## RPO Reconciliation

> === Phase 4.3-C RPO Reconciliation ===
> Client acknowledged : 72
> Client failed       : 17
> DB rows             : 72
> Last acknowledged seq: 89
> Highest DB seq       : 89
>
> Acknowledged but missing from DB: []
> Present in DB without COMMITTED acknowledgement: []
>
> RESULT: OBSERVED RPO = 0 ACKNOWLEDGED TRANSACTIONS LOST

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

A separate standby-loss experiment verified that the `ANY 1`
synchronous quorum policy continued accepting writes after one
synchronous standby became unavailable.
