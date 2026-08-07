# Kubernetes Hard Node Failure Verification

## Result

PARTIAL PASS

Automatic recovery: PASS
Zero-downtime continuity: FAIL

## Failure scenario

A kind worker node was stopped directly with Docker without performing
kubectl drain or cordon.

The failed worker hosted two of the three Spring Boot replicas.

Failure timestamp:

- 2026-08-08 02:18:09 CST

Observed immediate application capacity:

- Before failure: 3/3
- After node failure: 1/3
- Application replicas lost: 2/3

## Node failure detection

The failed node transitioned to NotReady approximately 65 seconds after
the underlying container was stopped.

## HTTP availability

The cluster-internal HTTP client observed failures during the incident:

- HTTP 503 observed at the failure boundary
- connection failure (`000FAIL`) observed during recovery

Therefore uninterrupted HTTP availability was not achieved for this
two-replica simultaneous-loss scenario.

## Kubernetes recovery behavior

Pods on the unavailable node retained the default tolerations:

- node.kubernetes.io/not-ready:NoExecute for 300 seconds
- node.kubernetes.io/unreachable:NoExecute for 300 seconds

After the node-loss eviction interval, replacement Pods were scheduled
onto surviving worker nodes.

Final Deployment state:

- READY: 3/3
- AVAILABLE: 3
- Failed worker remained NotReady

## Recovery time

Failure start:

- 02:18:09 CST

Deployment restored to 3/3:

- approximately 02:24:20 CST

Observed application capacity recovery time:

- approximately 371 seconds
- approximately 6 minutes 11 seconds

## Conclusion

Kubernetes successfully recovered the Spring Boot Deployment from an
uncontrolled worker failure without manual workload recovery.

However, the failed worker hosted two of three application replicas,
and brief HTTP unavailability was observed.

The next scheduling hardening step is to improve steady-state
fault-domain distribution while retaining soft scheduling fallback
during degraded-node conditions.
