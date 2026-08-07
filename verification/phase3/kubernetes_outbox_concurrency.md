# Kubernetes Multi-Replica Outbox Concurrency Verification

## Result

PASS

## Deployment

- Kubernetes: Docker Desktop kind cluster
- Spring Boot replicas: 3
- PostgreSQL replicas: 1
- Redis replicas: 1
- Kafka brokers: 1
- Kafka test topic partitions: 1

## Synthetic workload

- Outbox events inserted: 90
- Aggregate type: PHASE3_CONCURRENCY

## Per-replica publisher metrics

| Replica | Claimed | Published | Failures |
|---|---:|---:|---:|
| Replica A | 30 | 30 | 0 |
| Replica B | 30 | 30 | 0 |
| Replica C | 30 | 30 | 0 |
| Total | 90 | 90 | 0 |

## Database result

- PUBLISHED: 90
- Minimum retry count: 0
- Maximum retry count: 0

## Kafka result

- Messages observed: 90
- Valid JSON messages: 90
- Unique sequence IDs: 90
- Duplicate sequence IDs: 0
- Missing sequence IDs: 0
- Unexpected sequence IDs: 0
- Parse errors: 0

## Verified property

Three independent Spring Boot replicas concurrently processed the same
Transactional Outbox workload. PostgreSQL row-level locking with
FOR UPDATE SKIP LOCKED distributed work across all three publishers while
preserving one observable Kafka message per synthetic Outbox event.

This verifies multi-replica Outbox processing under the tested conditions.
It does not claim globally exactly-once delivery under arbitrary crash
boundaries.
