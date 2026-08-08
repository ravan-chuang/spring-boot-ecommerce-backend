# Phase 6 Redis Sentinel HA and Failover Verification

## Objective

Phase 6 replaces the previous single-instance Redis deployment with a
replicated Redis topology coordinated by Redis Sentinel.

The validation focuses on:

- Redis replication
- Sentinel quorum
- automatic master failover
- application Sentinel discovery
- data survival across master failure
- write availability after promotion
- replica reintegration after node recovery
- regression safety

## Architecture

The tested topology contains:

- 3 Redis instances
- 1 writable master
- 2 replicas
- 3 Redis Sentinel instances
- Sentinel quorum: 2
- persistent storage for Redis instances
- Spring Boot Redis connectivity through Sentinel discovery

The application no longer relies on a fixed Redis host.

## Baseline validation

Before failure injection:

- all 3 Redis pods were Ready
- all 3 Sentinel pods were Ready
- Redis topology contained 1 master and 2 replicas
- both replicas had an active replication link to the master
- all 3 Sentinels discovered the same master
- Sentinel reported 3 usable Sentinels
- Sentinel quorum and failover authorization were reachable

## Failure injection

The Kubernetes node hosting the active Redis master was stopped.

The failure removed both:

- the active Redis master
- one Sentinel instance

This left:

- 2 Redis instances
- 2 Sentinel instances

The remaining Sentinel quorum was sufficient to authorize failover.

## Automatic promotion

Redis Sentinel elected a surviving replica as the new master.

Observed promoted instance:

- Redis pod: `redis-1`
- role after promotion: `master`

The remaining surviving Redis instance became its replica.

Sentinel quorum during the failure remained available:

`OK 2 usable Sentinels. Quorum and failover authorization can be reached`

## Data validation

A probe value written before the master failure was replicated to both
replicas before fault injection.

After failover, the same value was successfully read from the promoted
master.

This demonstrates that the replicated pre-failure probe survived the
tested master-node failure.

A new value was then written successfully to the promoted master,
demonstrating write availability after promotion.

## Node recovery

The failed Kubernetes node was restarted.

After recovery:

- the former master returned
- Sentinel reconfigured it as a replica
- Redis converged back to exactly one master
- the new master had 2 connected replicas
- both replicas reported healthy replication links

The value written after failover was subsequently readable from all
three Redis instances.

## Application validation

Spring Boot was migrated from fixed-host Redis configuration to Sentinel
discovery using:

- Sentinel master name: `mymaster`
- three Sentinel endpoints
- explicit Redis connect timeout
- explicit Redis command timeout

All application replicas were Ready after the failover experiment.

A targeted application log scan found no:

- `RedisConnectionFailureException`
- `RedisCommandTimeoutException`
- `RedisSystemException`
- Redis connection-refused errors
- Lettuce ERROR/WARN failover failures

## Regression validation

A clean Maven test execution completed successfully:

- Tests run: 146
- Failures: 0
- Errors: 0
- Skipped: 0
- Maven exit code: 0

## Result

PASS.

The tested local Redis deployment tolerates loss of the active Redis
master node and one Sentinel while retaining sufficient quorum to
promote a replica.

The experiment demonstrated:

- automatic Redis master failover
- Sentinel quorum survival
- replicated data survival
- post-promotion writes
- automatic topology convergence after recovery
- application Sentinel discovery
- successful application regression tests

## Limitations

This is a local kind / Docker Desktop validation.

It does not establish:

- cloud availability-zone durability
- managed Redis service behavior
- synchronous zero-loss replication guarantees
- tolerance of simultaneous loss of two Redis data nodes
- tolerance of loss of Sentinel quorum
- large-scale cache workload behavior during failover
- production RTO/RPO or availability SLOs

Redis replication is asynchronous. Therefore this experiment proves
survival of the explicitly validated replicated data, not a general
zero-RPO guarantee for arbitrary writes immediately preceding failure.
