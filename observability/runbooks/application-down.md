# Application down runbook

## Trigger

`SpringBootApplicationDown` fires when Prometheus cannot scrape the application for more than one minute.

## Triage

1. Confirm whether the target is down in Prometheus and whether the failure is isolated to the application.
2. Check `docker compose ps`, application logs, and `/actuator/health/liveness`.
3. Verify PostgreSQL, Redis, and Kafka health before restarting the application.
4. Look for failed Flyway validation, missing environment variables, memory pressure, and crash-looping containers.

## Recovery

Restore the failed dependency or configuration, then restart only the affected service. Confirm readiness, one successful business request, trace export, and Prometheus scraping before resolving the incident.

## Escalation

Escalate immediately if recovery would require skipping a migration, deleting data, disabling authentication, or exposing a dependency port publicly.
