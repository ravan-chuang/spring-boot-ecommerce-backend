# Performance Baseline — Catalog Read API

## Environment

- Host: MacBook Pro M3 Max, 36 GB RAM
- Runtime: Docker Compose
- Stack: Spring Boot + PostgreSQL + Redis + Kafka
- Tool: k6
- Endpoint: `GET /api/products`

## Load Profile

| Stage | Virtual Users | Duration |
|---|---:|---:|
| Warm-up | 10 | 20s |
| Moderate load | 30 | 40s |
| Peak load | 50 | 40s |
| Cool-down | 0 | 20s |

## Results

| Metric | Result |
|---|---:|
| Total requests | 9,544 |
| Average throughput | 79.44 req/s |
| Average latency | 9.92 ms |
| P90 latency | 17.07 ms |
| P95 latency | 17.93 ms |
| P99 latency | 20.12 ms |
| Max latency | 47.17 ms |
| Failed request rate | 0.00% |
| Check pass rate | 100.00% |

## Acceptance Criteria

| Criterion | Target | Result | Status |
|---|---:|---:|---|
| P95 latency | < 500 ms | 17.93 ms | Pass |
| P99 latency | < 1 s | 20.12 ms | Pass |
| Failed request rate | < 1% | 0% | Pass |

## Interpretation

The catalog read endpoint remained stable throughout the test. No failed
requests or latency spikes were observed under a peak of 50 virtual users.

This is a local Docker Compose baseline rather than a production capacity
claim. The workload includes a 300 ms think time per iteration, so measured
throughput is intentionally rate-limited by the test design.