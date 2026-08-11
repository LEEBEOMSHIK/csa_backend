# Task 10 cutover verification

Date: 2026-08-12

## Implemented scope

- Transaction-aware, fair write admission and freeze acknowledgement in both independent backends.
- Migration error mapping and HTTP 503 contracts while reads remain available.
- Operator-only, non-web `content-migration` runner with fixed audit identity and epoch confirmation for destructive transitions.
- Final delta/reconciliation, atomic cutover/outbox transition, smoke verification, pre-open rollback, and canonical write opening.
- Parent/detail tombstones that archive the canonical graph, retain the legacy link, and prevent public runtime/manifest exposure.
- Task 9 residuals: minimum import pool guard and real PostgreSQL advisory-lock release proof across distinct physical connections.
- Hikari, Flyway, and Hibernate all target schema `front`; raw legacy JdbcTemplate reads resolve through the datasource schema rather than embedded schema literals.
- Control-aware public read routing for all four curated and all four AI read boundaries, preserving legacy DTO, authorization, error, sort, and media URL contracts across cutover and rollback.

No V16 migration was required. V13 already owns all control, reconciliation, link, version, and outbox structures; V15 contains the additive legacy contract metadata. The admin backend remains schema-non-owning.

## RED-to-GREEN evidence

The focused user-backend batch completed with 39 tests, 0 failures/errors/skips (`BUILD SUCCESSFUL`, all five Gradle tasks executed). It covered migration gate/control/runner/scheduler, transaction-aware activity tracking, cutover/smoke behavior, reconciliation/tombstones, user error mapping and HTTP read/write contracts, pool capacity, and the wrapped user mutation entrypoint.

The focused admin-backend batch completed with 21 tests, 0 failures/errors/skips (`BUILD SUCCESSFUL`, all five Gradle tasks executed). It covered both legacy and canonical mutation gates, asset upload and frozen read behavior, binding/authoring/review/publish entrypoints, HTTP response contracts, independent scheduler acknowledgement, and transaction-aware activity tracking.

`ContentCutoverPostgresIntegrationTest` completed 4 tests with 0 failures/errors/skips. It proved:

- a thrown cutover hook rolls back source switches, control state, and outbox together;
- advisory locks are released and reacquirable from two distinct physical PostgreSQL connections;
- soft-deleted parent and hard-deleted detail tombstones archive story/version, retain the link, return runtime 404, and avoid manifest reads.

Additional focused checks:

- `SecurityConfigNonWebTest`: 1/1 GREEN after separating reusable password encoding from servlet-only security configuration.
- `TrackedRuntimeConfigurationTest.localProfileLoadsCompleteRuntimeContractFromTrackedApplicationYaml`: 1/1 GREEN with `spring.datasource.hikari.schema=front`.
- `TrackedDataSourceSchemaPostgresTest`: 1/1 GREEN; `current_schema()` is `front` and unqualified `fairytales` resolves through an application JdbcTemplate connection.
- Initial `ContentMigrationRunnerTest`: 6/6 GREEN, including confirmation mismatch and dry-run no-write interaction assertions.
- `LegacyStoryImportPoolCapacityTest`: 1/1 GREEN; a maximum pool of 1 fails before acquiring the session-lock connection.

## Post-QA closure evidence

- P1-1: final delta import and reconciliation now share a broad recoverable failure boundary. `LegacyImportException`, `DataAccessException`, and other unexpected runtime failures persist a stable `FAILED` report and attempt restoration to `OPEN / LEGACY / LEGACY`. If that restoration transaction also fails, the original failure is propagated and the restoration failure is suppressed; success is never fabricated.
- P1-2: `ContentReadRouter` reads the migration control once per service call and selects exactly one source. The curated home, list, detail, and slides reads and the AI owner list, shared list, owner-private slides, and shared slides reads use the canonical repositories after cutover and legacy repositories after rollback. Canonical branches perform no legacy content-table reads, while public IDs, DTOs, authorization, errors, sorting, and media URLs remain compatible.
- P1-3: bulk import requires an explicit `content.migration.dry-run` value and accepts only the literal values `true` or `false`. Missing, blank, misspelled, or otherwise invalid values fail before any import or cutover interaction. The expanded `ContentMigrationRunnerTest` completed 9/9 GREEN.
- P1-4: `DataInitializer` enters the `LEGACY_CURATED` activity tracker only when category/fairytale/detail seed mutation is actually required. Frozen or canonical-source startup rejects the seed before content mutation, a fully seeded startup remains a no-op, and a missing migration-control singleton fails closed by skipping content seed writes.
- Paged curated import queries now carry `is_theme`, `is_new`, and `is_recommended`, matching bulk import and preserving canonical home placement flags.
- `ContentCutoverSmokeVerifierTest` completed 5/5 GREEN. Direct adapter parity remains in the checksum, and the smoke verifier additionally probes actual `FairytaleService` detail/slides and `AiFairytaleService` owner-private/shared slide boundaries while `read_source=CANONICAL`; legacy routing and public-service failures fail closed.

The final targeted user-backend verification completed exactly 14 classes and 70 tests with 0 failures/errors/skips. The exact PostgreSQL method `LegacyStoryImportPostgresTest.publicServicesReadCanonicalDtosAndMediaAfterCutoverThenLegacyAfterRollback` completed 1/1 GREEN, proving actual public services return canonical DTOs and media after cutover and return to legacy reads after rollback.

## Browser smoke

The authenticated Chrome smoke covered login, curated list, detail, and retry behavior with no browser console error. The existing actual E2E evidence was reused for save/add/delete mutation coverage; those state-changing flows were not repeated during this cutover verification.

## Actual authorized dry-run

Only the documented non-web dry-run command was executed. No request-freeze, stateful bulk import, finalize, smoke, open-writes, or rollback command was executed.

Process result:

```text
profiles=local,content-migration
web-application-type=none
command=bulk-import
epoch=1
dry-run=true
process PID=57708
Gradle exit=0
BUILD SUCCESSFUL in 53s
curatedCount=19
aiCount=0
checksum=4c9d473309eb2d96dfc9120a6946fe97b0a29a575babf96ff8541db58572f760
```

The datasource reported `csa/front`, Flyway validated 15 migrations and reported no migration necessary, and the process shut down cleanly after emitting the checksum.

The read-only snapshots before and after were identical:

```text
control|1|OPEN|LEGACY|LEGACY|0|null|null|null|null|null
counts|0|0|0|0|0
flyway|15|15
```

`counts` is `legacy_story_links | stories | story_content_versions | content_migration_reconciliations | content_outbox_events`. This proves the dry run did not change the control row, links, stories, versions, reconciliation reports, outbox, or Flyway history.

## Operational notes

- Normal startup and admin GET paths do not import or mutate migration state.
- `DataInitializer`, the live freeze acknowledger, and its scheduler are excluded from the CLI profile.
- Reads remain available during `FREEZE_REQUESTED` and `FROZEN`; approved mutations fail with `CONTENT_MIGRATION_FREEZE`.
- The local dry-run initially exposed non-web servlet-security coupling and a public-schema JdbcTemplate connection. Focused RED tests were added before the split security configuration and tracked Hikari `front` schema fixes.
- Full suites were intentionally not repeated; verification used the approved focused batches and PostgreSQL integration slices.

## Final static audit

- `git diff --check` completed without whitespace errors in both backend repositories; targeted checks also found no trailing whitespace in the new Task 10 files.
- `git diff --cached --name-only` was empty in both repositories. Nothing was staged, committed, or pushed.
- No user backend source imports an admin-backend Java type, and no admin backend source imports a user-backend Java type.
- Runner/import reachability is confined to the profiled migration runner and migration services; no controller or initializer exposes it. Normal `DataInitializer` and live acknowledgement schedulers are excluded from the CLI profile.
- The admin backend contains no migration runner, Flyway reference, or schema migration.
- Migration inventory remains V1 through V15; Task 10 did not add or modify a V16 migration.
- Hikari, Hibernate, and Flyway all declare `front` in local, dev, and prod user-backend configuration.
- No Task 10 Gradle or Spring Java process remained after verification.
- Existing Task 9 and unrelated dirty-worktree changes were preserved; no reset, revert, staging, commit, or push was performed.
