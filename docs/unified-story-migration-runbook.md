# Unified story content cutover runbook

This runbook applies to the Phase 1 legacy-to-canonical story cutover. The migration CLI exists only in `csa_backend`. `csa_adm_backend` shares the control table and acknowledges freezes, but it does not own Flyway or expose a migration runner.

## Safety boundary

- Treat every command below except `bulk-import --dry-run=true` and the read-only SQL as state-changing and require explicit operator approval.
- The state-changing examples in this document were not executed during Task 10 verification.
- Run the CLI only with profiles `local,content-migration` (or the target environment plus `content-migration`) and `spring.main.web-application-type=none`.
- The CLI audit identity is fixed to `csa_backend:content-migration-cli`; it is not an operator-supplied argument.
- Never start `open-writes` until the same epoch has a successful final reconciliation and passing smoke result.
- CLI rollback is available only while the state is `CUTOVER_PENDING`. Once canonical writes are open, rollback fails with `CANONICAL_WRITES_ALREADY_OPEN`.

## Preconditions

1. Back up the shared PostgreSQL database and record the restore point.
2. Deploy the same Task 10 build to both backends and keep both normal web processes healthy.
3. Confirm Flyway is current and `front.content_migration_control` has exactly one row.
4. Confirm the datasource schema is `front` for Flyway, Hibernate, and Hikari.
5. Confirm the user-backend Hikari maximum pool size is at least 2. One physical connection owns the session advisory lock while another performs transactional work. Local/dev Hikari defaults and prod's explicit maximum of 10 satisfy this; a smaller pool is rejected with `LEGACY_IMPORT_POOL_SIZE_TOO_SMALL` before import starts.
6. Confirm current state is `OPEN / LEGACY / LEGACY`, and choose the next positive barrier epoch.

Use an approved read-only SQL console for status checks:

```sql
select singleton_id, state, read_source, write_source, barrier_epoch,
       backend_ack_epoch, admin_backend_ack_epoch,
       trim(reconciliation_hash) as reconciliation_hash,
       trim(smoke_hash) as smoke_hash, smoke_passed_at, updated_at
from front.content_migration_control
where singleton_id = 1;
```

## Read-only dry run

Set `DB_PASSWORD`, `CSA_USER_JWT_SECRET`, and the target profile through the approved secret mechanism. In PowerShell:

```powershell
$migrationEpoch = 1
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=bulk-import --content.migration.epoch=$migrationEpoch --content.migration.dry-run=true" --no-daemon --console=plain
```

Expected output contains `Content migration dry run`, CURATED/AI source counts, and a deterministic checksum. This path reads source rows and media bytes only. It does not import, reconcile, archive, update control state, or enqueue outbox events.

## Approved cutover sequence

The commands in this section are examples only. They are state-changing and were not run during Task 10.

### 1. Bulk import while legacy writes are open

```powershell
$migrationEpoch = 1
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=bulk-import --content.migration.epoch=$migrationEpoch --content.migration.dry-run=false" --no-daemon --console=plain
```

Repeat only after reviewing import/quarantine results. Do not reduce the pool below 2.

### 2. Request the freeze

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=request-freeze --content.migration.epoch=$migrationEpoch --content.migration.confirm-epoch=$migrationEpoch" --no-daemon --console=plain
```

The transition is `OPEN/LEGACY/LEGACY -> FREEZE_REQUESTED/LEGACY/LEGACY`. Both backends continue serving reads. Approved legacy and canonical mutation routes return HTTP 503 with code `CONTENT_MIGRATION_FREEZE` and the barrier epoch.

### 3. Wait for both live acknowledgements

Do not continue until the status query shows all of the following for the same epoch:

- `state = 'FROZEN'`
- `backend_ack_epoch = barrier_epoch`
- `admin_backend_ack_epoch = barrier_epoch`
- both normal web processes are still healthy and reads remain available

Each backend uses its own fair activity tracker. It acknowledges only after its admitted writes have completed, including transaction completion; the two processes do not share in-memory counters.

### 4. Finalize reconciliation and prepare canonical cutover

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=finalize --content.migration.epoch=$migrationEpoch --content.migration.confirm-epoch=$migrationEpoch" --no-daemon --console=plain
```

Finalize runs the final delta import and reconciliation outside the short control transaction, then atomically changes both sources to `CANONICAL`, sets `CUTOVER_PENDING`, records the same-epoch reconciliation checksum, and enqueues the audit outbox event. A failed reconciliation returns the control row to `OPEN/LEGACY/LEGACY`.

Absent active legacy parents or details are tombstones: the retained legacy link remains, while the linked canonical story and its versions are archived. Public runtime returns 404 and does not read a stored manifest for those stories.

### 5. Run cutover smoke

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=smoke --content.migration.epoch=$migrationEpoch" --no-daemon --console=plain
```

Smoke checks normalized curated public, AI private, AI shared, and public runtime behavior. Passing smoke records a checksum/time. Failed smoke automatically returns to `OPEN/LEGACY/LEGACY` and writes a failure outbox event.

### 6. Open canonical writes

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=open-writes --content.migration.epoch=$migrationEpoch --content.migration.confirm-epoch=$migrationEpoch" --no-daemon --console=plain
```

Expected final state is `OPEN/CANONICAL/CANONICAL`. Confirm both backends accept their approved mutation routes and public reads remain healthy.

## Rollback before opening writes

Use only from `CUTOVER_PENDING` and only with explicit approval:

```powershell
$rollbackReason = "operator-approved-reason"
.\gradlew.bat bootRun --args="--spring.profiles.active=local,content-migration --spring.main.web-application-type=none --content.migration.command=rollback --content.migration.epoch=$migrationEpoch --content.migration.confirm-epoch=$migrationEpoch --content.migration.rollback-reason=$rollbackReason" --no-daemon --console=plain
```

Expected state is `OPEN/LEGACY/LEGACY`, with an audited `CUTOVER_ROLLED_BACK` outbox event. Do not use this command after canonical writes have opened.

## Stop conditions and common codes

- `CSA_BACKEND_ACK_REQUIRED` / `CSA_ADM_BACKEND_ACK_REQUIRED`: live process has not drained and acknowledged the epoch.
- `CONTENT_MIGRATION_FROZEN_REQUIRED` / `CUTOVER_PENDING_REQUIRED`: state transition order is invalid.
- `CONTENT_MIGRATION_EPOCH_MISMATCH` / `CONTENT_MIGRATION_CONFIRM_EPOCH_MISMATCH`: stop and re-read the control row; do not guess the epoch.
- `RECONCILIATION_REQUIRED` / `CUTOVER_SMOKE_REQUIRED`: do not open writes.
- `FINAL_RECONCILIATION_FAILED`: inspect the stored reconciliation report and restore legacy/open state before retrying.
- `LEGACY_IMPORT_POOL_SIZE_TOO_SMALL`: raise the Hikari maximum to at least 2 before importing.
- `LEGACY_MEDIA_PREFLIGHT_FAILED`: repair or quarantine unreadable legacy media before retrying.
- `CANONICAL_WRITES_ALREADY_OPEN`: CLI rollback is no longer permitted; follow the separately approved recovery plan.
