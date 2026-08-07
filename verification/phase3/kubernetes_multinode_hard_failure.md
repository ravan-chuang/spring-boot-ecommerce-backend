# Phase 3.3 Multi-Node Hard Failure Verification

## Environment

- Kubernetes: kind v1.36.1
- Topology:
  - 1 control-plane
  - 3 worker nodes
- Spring Boot replicas: 3
- PodDisruptionBudget:
  - minAvailable: 2
- Rolling update:
  - maxUnavailable: 0
  - maxSurge: 1
- Application placement:
  - one replica per worker during baseline
- Node failure tolerations:
  - node.kubernetes.io/not-ready: 30s
  - node.kubernetes.io/unreachable: 30s

## Failure injection

Worker node:

spring-boot-ha-worker

was abruptly stopped using Docker at:

2026-08-07T19:22:59Z

The HTTP probe remained on spring-boot-ha-worker3.

## Client trace

Observed requests:

- Total trace requests: 458
- HTTP 200: 424
- HTTP 000: 34
- HTTP 5xx: 0

Post-failure observation:

- Parsed requests: 78
- Successful: 44
- Transport failures: 34
- First failure: 2026-08-07T19:22:59Z
- Last observed transport failure: 2026-08-07T19:23:39Z
- First success after the last observed failure:
  2026-08-07T19:23:40Z

Observed client disruption window:

approximately 40 seconds

Transport failures consisted of connection refusal and connect timeout.
No application-generated HTTP 5xx responses were observed.

## Kubernetes recovery timeline

- T+0s:
  Worker hard-stopped.

- T+48s:
  Node Ready condition transitioned to Unknown
  at 2026-08-07T19:23:47Z.

- T+78s:
  Replacement pod created
  at 2026-08-07T19:24:17Z.

- T+94s:
  Replacement pod became Ready
  at 2026-08-07T19:24:33Z.

- T+94s:
  EndpointSlice last-change timestamp:
  2026-08-07T19:24:33Z.

Final deployment state:

- replicas: 3
- ready: 3
- available: 3
- updated: 3

## Verified properties

The surviving Spring Boot replicas remained operational during abrupt
worker-node loss and continued successfully serving traffic.

The hard node failure caused transient transport-level failures during
the Kubernetes node-failure detection and Service dataplane convergence
window.

The experiment did not demonstrate zero-downtime behavior under abrupt
node failure.

Full three-replica capacity was restored approximately 94 seconds after
failure injection.

The replacement Spring Boot pod required approximately 16 seconds from
creation to Ready state.

## Interpretation

Application availability, Kubernetes node health, Deployment replica
recovery, EndpointSlice convergence, and client-visible availability are
distinct recovery dimensions and must not be represented as one MTTR
metric.

This experiment verifies multi-node failure recovery behavior under the
tested local kind environment. It does not establish production-grade
multi-zone high availability.
