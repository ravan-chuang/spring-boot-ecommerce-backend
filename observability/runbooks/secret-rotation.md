# Secret rotation runbook

## Scope

Rotate PostgreSQL, JWT signing, Grafana, Caddy/TLS, and Discord webhook credentials without committing values to Git.

## Procedure

1. Create the replacement credential in the owning system or secret manager.
2. Update the deployment secret and restart only consumers of that secret.
3. Validate authentication, observability delivery, and application health.
4. Revoke the previous credential after the new one is confirmed.
5. Run `scripts/secret_rotation_check.sh` and review Git history if exposure is suspected.

For JWT signing-key rotation, active access tokens signed only by the old key become invalid unless a key-ring/`kid` strategy is introduced. Schedule the change accordingly. Never paste a live credential into an issue, log, runbook, test fixture, or command output.
