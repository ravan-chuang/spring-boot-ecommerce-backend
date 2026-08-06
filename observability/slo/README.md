# Service-level objectives

## Objectives

| Service indicator | Objective | Window | Measurement |
|---|---:|---:|---|
| HTTP availability | 99.5% | Rolling 30 days | Non-5xx requests / all requests |
| HTTP latency | P95 at or below 750 ms | Rolling 5 minutes | Actuator HTTP server histogram |
| DLT review | No `RECEIVED` event older than 15 minutes | Continuous | `dlt_events{status="RECEIVED"}` |
| Replay lease | No replay held longer than two 60-second leases | Continuous | `dlt_events{status="REPLAYING"}` |

The 99.5% availability target provides a monthly error budget of approximately 3 hours 39 minutes in a 30-day month. Multi-window alerts detect a fast 14.4x burn and a slower 6x burn. The objective excludes intentional 4xx responses because they represent rejected client requests rather than server unavailability.

## Operating policy

- Treat a fast-burn alert as an incident and pause non-essential releases.
- Treat a slow-burn alert as a release-risk signal; assign an owner and correct the source before the budget is exhausted.
- Review SLO thresholds after each load-test baseline and at least quarterly.
- Keep `uri` out of alert grouping to avoid high-cardinality incident streams.
- Validate rule syntax with `promtool check rules` before deployment.
