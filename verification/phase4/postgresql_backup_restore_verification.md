# PostgreSQL Backup and Restore Verification

## Result

PASS

## Backup configuration

CloudNativePG was configured with the Barman Cloud plugin and an
S3-compatible object store.

Continuous WAL archiving was verified operational.

A plugin-based online base backup completed successfully and produced
physical backup artifacts in object storage.

## Backup evidence

The successful backup reported:

- Method: plugin
- Plugin: barman-cloud.cloudnative-pg.io
- PostgreSQL major version: 17
- Backup phase: completed
- Online backup: true
- Begin WAL: 00000005000000000000001B
- End WAL: 00000005000000000000001B

Object storage contained:

- backup.info
- data.tar
- archived WAL segments

CloudNativePG subsequently reported a valid First Point of
Recoverability.

## Restore validation

An independent CloudNativePG cluster named:

postgres-ha-restore

was bootstrapped from the previously generated Barman backup.

The recovery source used the existing ObjectStore and explicitly
selected the source backup server:

postgres-ha

The physical full-recovery job completed successfully.

The restored PostgreSQL instance then started independently and reached:

- Instances: 1
- Ready instances: 1
- Status: Cluster in healthy state

Observed time from restore-cluster creation to healthy state was
approximately 49 seconds in the local kind environment.

## Initial recovery configuration failure

The first recovery attempt failed with:

no target backup found

The recovery plugin initially resolved the backup server as:

postgres-ha-restore

while the physical backup catalog was stored under:

postgres-ha

The recovery source was corrected by explicitly setting the plugin
serverName to postgres-ha.

After recreating the restore cluster and its storage, recovery
completed successfully.

This failure is retained as diagnostic evidence because successful
backup creation alone does not establish recoverability.

## Interpretation

The experiment demonstrates that:

- WAL archiving is functional.
- A physical base backup can be created.
- Backup artifacts are present in object storage.
- The backup catalog can be consumed by an independent recovery cluster.
- PostgreSQL can be physically restored and started from the backup.

Therefore the tested backup is recoverable under the tested local
CloudNativePG/kind environment.

This does not establish production-grade disaster recovery by itself.
The current experiment uses local kind nodes and a local MinIO object
store and does not validate remote-region durability, cloud object
storage failure domains, encryption/KMS behavior, or large-dataset
restore performance.

## Restored data validation

The restored PostgreSQL instance was queried directly after the recovery
cluster reached healthy state.

Observed database state:

- Database: `spring_boot_lab`
- `pg_is_in_recovery()`: `false`
- `phase4_primary_failover_probe` rows: `3864`

This confirms that the validation did not stop at Kubernetes readiness
or PostgreSQL process startup. Application-level test data contained in
the physical backup was successfully recovered and was queryable from
the independently restored cluster.
