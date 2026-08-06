# PostgreSQL backup and restore drill

## Backup

Run `scripts/postgres_backup.sh`. It creates a timestamped custom-format dump and a SHA-256 checksum under the ignored `backups/` directory by default. Store production backups in encrypted object storage with access logging and lifecycle retention.

## Restore verification

Run the restore only against a disposable database whose name ends in `_restore_verify`:

```bash
ALLOW_RESTORE_VERIFY=true \
RESTORE_DATABASE=spring_boot_lab_restore_verify \
scripts/postgres_restore_verify.sh backups/<dump>.dump
```

The script rejects other database names, restores the dump, validates Flyway history and core table counts, and drops the scratch database on exit.

## Drill evidence

Record the dump timestamp/checksum, restore start/end times, migration version, row-count validation, recovery time, owner, and corrective actions. Run quarterly and after material schema changes.
