# Phase 5 Kafka Message Reconciliation

## Result

Observed RPO:

**0 acknowledged messages lost**

## Evidence

Producer acknowledgement capture:

- ACKed records: 3733
- Unique ACKed values: 3733
- Value range: 0-3732
- Producer send errors recorded in captured log: 0
- Duplicate ACK records: 0

Kafka recovery read:

- Consumed records: 6337
- Unique consumed values: 6337
- Duplicate consumed records: 0

Set reconciliation:

- ACKed but missing from Kafka: none
- All 3733 messages for which an acknowledgement was captured were
  successfully recovered from Kafka after the broker/node failure.

## Additional Kafka records

Kafka contained 2604 additional records beyond the final acknowledgement
captured in `producer_after_recovery.log`.

These records are not interpreted as an error or as evidence of duplicate
delivery. The producer log used for acknowledgement reconciliation is a
point-in-time capture and does not cover the producer's entire lifetime.

The RPO claim is therefore deliberately scoped to messages for which a
successful producer acknowledgement was captured.

## Interpretation

The experiment establishes, for the tested failure:

1. Kafka continued operating after loss of one broker/node.
2. Partition leadership moved to surviving brokers.
3. Replication remained at the configured minimum ISR of 2.
4. The producer encountered transient metadata/leadership changes and
   recovered automatically.
5. The failed broker rejoined successfully.
6. All six partitions returned to three-member ISR.
7. KRaft follower lag returned to zero.
8. Every captured acknowledged message was present after recovery.

Therefore:

**OBSERVED RPO = 0 ACKNOWLEDGED MESSAGES LOST**

This is an empirical result for the tested local kind environment. It does
not prove zero loss for arbitrary failures, correlated multi-node failures,
storage corruption, cross-region disaster, or production infrastructure.
