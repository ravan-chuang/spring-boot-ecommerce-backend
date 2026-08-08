# Phase 5 — Kafka HA and Broker Failure Validation

## Scope

Phase 5 replaces the previous single-broker Kafka deployment with a
three-node Kafka KRaft cluster and validates behavior during a hard
broker/node failure.

The experiment focuses on:

- Kafka KRaft quorum availability
- topic replication
- minimum in-sync replicas
- leader failover
- producer behavior during broker loss
- broker rejoin
- ISR convergence
- message-level durability reconciliation

## HA configuration

The tested Kafka topology contains three Kafka nodes distributed across
three Kubernetes worker nodes.

The Phase 5 validation topic uses:

- 6 partitions
- replication factor: 3
- `min.insync.replicas=2`

The producer uses acknowledgement and idempotence settings intended to
prevent successful acknowledgement before the configured durability
requirements have been met.

## Failure injection

A Kubernetes worker hosting one Kafka broker was stopped while the
producer was continuously writing messages.

This is stronger than deleting only the Kafka pod because the tested
failure removes the broker together with its Kubernetes node.

## Observed failure behavior

During the failure:

- one Kafka broker became unavailable
- KRaft retained quorum
- controller leadership changed
- topic partition leadership was redistributed
- affected partitions operated with two in-sync replicas
- producer-side transient `NOT_LEADER_OR_FOLLOWER` responses were observed
- the Kafka client refreshed metadata and retried automatically
- no terminal `producer_send_error` event was observed in the captured
  acknowledgement evidence

The transient metadata errors are expected evidence that the client path
actually encountered leader movement. They are not classified as data loss.

## Recovery

The failed worker node was restarted.

After recovery:

- the Kafka broker became Ready again
- all three brokers were operational
- all six validation partitions returned to three-member ISR
- KRaft follower lag returned to zero in the final observation
- the Kafka cluster remained usable

## Message durability reconciliation

A complete read of the validation topic was compared against the
successfully acknowledged producer records captured during the experiment.

The reconciliation criterion is:

> Every message for which a producer acknowledgement was captured must be
> present in Kafka after recovery.

The observed reconciliation result is:

**OBSERVED RPO = 0 ACKNOWLEDGED MESSAGES LOST**

Kafka contained additional records beyond the captured acknowledgement
window. Those records were produced after the point-in-time producer log
capture and therefore are not treated as duplicates or inconsistencies.

The durability claim is intentionally restricted to the set of producer
acknowledgements for which evidence was captured.

## Interpretation

The experiment demonstrates, for this environment and failure scenario,
that the Kafka deployment can tolerate loss of one broker/node while
maintaining KRaft quorum and the configured minimum ISR.

It also demonstrates successful broker rejoin and replication convergence
after node recovery.

The experiment goes beyond Kubernetes readiness checks by reconciling
client acknowledgements against records physically readable from Kafka
after recovery.

## Limitations

This is a local engineering validation using kind and Docker Desktop.

It does not by itself establish:

- cloud-region failure tolerance
- correlated two-node failure tolerance
- production storage durability
- rack / availability-zone awareness
- cross-region Kafka disaster recovery
- sustained high-throughput failover behavior
- long-duration soak reliability
- a formal production availability SLO

The result should therefore be interpreted as evidence for the explicitly
tested single broker/node hard-failure scenario rather than a universal
zero-loss guarantee.

## Recorded experiment summary

```text
=== Phase 5 Kafka Failure / Recovery Summary ===

During broker/node failure:
  KRaft leader ID       : N/A
  KRaft leader epoch    : N/A
  KRaft follower lag    : N/A
  partitions at ISR=2   : 0/6
  NOT_LEADER retries    : 2

After recovery:
  KRaft leader ID       : 1
  KRaft leader epoch    : 2
  KRaft follower lag    : 0
  partitions at ISR=3   : 6/6

Message durability:
  Producer ACKed records    : 3733
  Unique ACKed values       : 3733
  Consumed records          : 6337
  ACKed but missing count   : 0
  RESULT: OBSERVED RPO = 0 ACKNOWLEDGED MESSAGES LOST
```
