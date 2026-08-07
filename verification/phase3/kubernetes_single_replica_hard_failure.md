# Kubernetes Single-Replica Hard Node Failure Verification

## Result

Automatic recovery: PASS
Zero-downtime HTTP continuity: FAIL

## Scenario

A worker node hosting one of three Spring Boot replicas was stopped
directly with Docker, without cordon or drain.

Steady-state application capacity before failure:

- 3 replicas
- 1 replica on the failed worker
- 2 replicas remained on surviving workers

## Failure timing

Failure start:

- 2026-08-08 02:35:21 CST

Deployment restored to 3/3:

- approximately 2026-08-08 02:41:41 CST

Measured recovery duration:

- approximately 391 seconds

## HTTP availability

The cluster-internal HTTP client observed:

- HTTP 503 during the incident
- connection failure (`000FAIL`) during the incident

Therefore uninterrupted HTTP continuity was not achieved.

## Recovery behavior

The failed worker transitioned to NotReady.

The Deployment remained available at 2/3 replicas while the failed Pod
remained associated with the unavailable node.

After node-loss eviction handling, Kubernetes created a replacement Pod
on a surviving worker.

Final Deployment state:

- READY: 3/3
- AVAILABLE: 3

## Conclusion

Kubernetes recovered the Deployment automatically from a single-replica
hard node failure without manual workload recovery.

However, brief HTTP unavailability was observed despite two surviving
application replicas.
