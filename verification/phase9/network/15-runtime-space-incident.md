# Phase 9.1 Runtime Space Incident

## Observed incident

During network-hardening verification, the OCI guest experienced a
userspace degradation.

Observed symptoms included:

- TCP/22 remained reachable.
- SSH stalled during banner exchange.
- OCI Run Command executions remained ACCEPTED/VISIBLE for an extended period.
- The serial console repeatedly reported:

  `systemd-journald: Failed to open runtime journal: No space left on device`

A subsequent power cycle restored SSH protocol responsiveness and OCI Run
Command execution.

## Post-reboot evidence

- `/run`: 190 MiB total, 4.1 MiB used (3%)
- `/run` inode usage: 1%
- root filesystem: 30 GiB total, 19% used
- RAM: 946 MiB total, 453 MiB used
- swap: 497 MiB total, 0 used
- Run Command: SUCCEEDED / ACKED
- Terraform reconciliation: No changes

## Evidence boundary

The system does not retain a persistent systemd journal across boots.

Therefore the pre-reboot filesystem and inode utilization cannot be recovered,
and the exact exhausted resource cannot be proven retrospectively.

The incident is most consistent with transient exhaustion of the volatile
runtime-journal filesystem or related `/run` resource, but this remains a
probable root cause rather than a confirmed one.

No evidence indicates that the Phase 9.1 OCI NSG/Security List changes caused
the guest-side degradation.
