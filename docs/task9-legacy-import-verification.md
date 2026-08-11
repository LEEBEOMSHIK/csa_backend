# Task 9 legacy import verification

Date: 2026-08-11

## Completion-gate PostgreSQL characterization

The first fresh completion-gate run of `LegacyStoryImportPostgresTest` completed with 23 tests,
6 failures, 0 errors, and 0 skips. All six failures were stale assertions after approved Task 9
contract changes; no production behavior was changed in response:

- `aiReadOnlyServiceReturnsLegacySnapshotAndPersistsShadowMismatch` and
  `missingCanonicalSnapshotReturnsLegacyReadAndRecordsOneOpenMismatch`: shadow reads are now
  explicitly disabled by default. This opt-in PostgreSQL contract class now enables
  `csa.migration.shadow-read-enabled=true` so the two audit-persistence cases exercise the
  enabled path.
- `deltaLoadsMultipleChangedIdsThroughOneBulkSourceSnapshot`: the source SELECT bound is 8,
  accounting for both CURATED and AI discovery plus the bounded CURATED snapshot queries. The
  former assertion counted only the pre-refactor path.
- `identicalCuratedReplayIsACompleteNoOp`: canonical rows, links, versions, assets, imported
  timestamp, and files remain unchanged, while the per-type migration watermark advances on a
  successful no-op scan as required for progress.
- `failedMediaPreflightLeavesLinkPointerVersionAndWatermarkUnchanged`: the shared guarded media
  source reader reports missing/unreadable local media as `LEGACY_MEDIA_PREFLIGHT_FAILED`; the
  fail-safe graph/pointer/watermark assertions remain unchanged.
- `pageLessCuratedIsQuarantinedWithoutAbortingBatchAndRepairsOnSameStory`: the adapter now emits
  the complete typed contract, so migration fields are under `/migration` and an absent slide
  response is represented by null `/curatedSlides`, rather than the removed flat shadow shape.

Final verification results are recorded below after the corrected fresh rerun.

The first corrected rerun completed 23 tests with one fixture failure. The missing-canonical
case had inserted legacy media URLs without creating their source bytes; the independent adapter
correctly failed closed during media identity normalization before it could write the mismatch.
The test fixture now writes the same legacy bytes as the other real-adapter cases. No production
code changed for this result.

## Final verification

- `LegacyStoryImportPostgresTest`: the original completion gate passed 23 tests, 0 failures,
  0 errors, and 0 skips in 173.4 seconds (JUnit 83.948 seconds). After the independent-QA
  fixes below added concurrency, watermark, and restartability cases, the latest required fresh
  full-class rerun passed 27 tests, 0 failures, 0 errors, and 0 skips in 77 seconds
  (JUnit 66.957 seconds).
- Targeted unit/security/media/adapter/reconciliation batch: 16 classes, 65 tests,
  0 failures, 0 errors, 1 skip. The skipped symbolic-link case requires a Windows privilege
  unavailable to the test process; both Windows junction cases passed.
- `sharedSchemaMigrate`: the intentional RED was expected V14/actual V15 after Flyway applied
  the additive migration. After updating the finite-target assertion, the harness passed 1/1.
  `front.flyway_schema_history` reports `15|add legacy contract metadata|true`.
- Admin shared-schema compatibility: `csa_adm_backend postgresTest` started and validated its
  mappings against V15, then ran 82 tests. Eighty passed; two unrelated fixture tests failed
  because their native `categories` inserts omit the pre-existing NOT NULL `cre_dt` column.
  The exact failures were
  `AdminCuratedFairytaleServiceTest.updateCreatesMissingDetailAndRestoresSoftDeletedDetail`
  (`AdminCuratedFairytaleServiceTest.java:197`) and
  `CuratedFairytaleRepositoryTest.returnsOnlyActiveFairytalesWithCategories`
  (`CuratedFairytaleRepositoryTest.java:75`), both rooted at PostgreSQL
  `PSQLException: null value in column "cre_dt" of relation "categories"`. There was no
  `SchemaManagementException`, V15 column/table failure, or admin source change.

The V15 schema change is additive. `story_content_versions.legacy_contract_metadata` is a
non-null JSON object with an empty-object default and an object-type check;
`legacy_migration_watermarks.snapshot_hash` is nullable and, when present, constrained to a
lowercase 64-character SHA-256 hex value. Task 9 accesses both through typed JDBC records and
does not add entity setters, relationship ownership, or API request JSON surfaces.

## Independent-QA P1 fix round

### Actual legacy contract parity

`LegacyFairytaleAdapterContractTest` calls the real legacy `FairytaleService` and
`AiFairytaleService`, imports the same PostgreSQL rows, and compares those DTO JSON trees with the
concrete canonical adapters. Only fixture URLs whose complete literal values are present in an
independently computed URL-to-content-SHA map are normalized; unknown URL fields still fail and
non-URL strings are not removed or ignored.

The genuine RED run failed both tests:

- AI expected `createdAt: "2024-02-03T04:05:06.123456"`; canonical returned the lossy
  `createdAtEpochMillis: 1706900706123` field.
- CURATED expected the actual API category list `["retired-780"]`; canonical returned `[]`
  because both importer and shadow SQL incorrectly filtered `categories.del_yn`.

Production now stores the exact nullable legacy `LocalDateTime` JSON scalar in typed V15 metadata,
preserves its microseconds when writing `stories.created_at`, and exposes the same `createdAt`
string from the canonical adapter. Previously written V15 JSON using `createdAtEpochMillis` remains
readable through an alias; the canonical story timestamp is the compatibility fallback for its
already-truncated value. Category membership is loaded without a category soft-delete filter,
matching `FairytaleDto.from` without changing the public legacy API. The focused GREEN evidence is:

- `LegacyFairytaleAdapterContractTest`: 2/2 passed in 59 seconds after the final importer change.
- `LegacyContractMetadataTest`: 4/4 passed in 13 seconds, including the old JSON-field alias.
- `LegacyStoryImportPostgresTest.realCuratedAndAiAdaptersMatchTheirLinkedCanonicalGraphs`: 1/1
  passed in 53 seconds.

No entity nullability or relationship ownership changed. The metadata `createdAt` scalar remains
nullable, legacy `cre_dt` and canonical `stories.created_at` remain NOT NULL in PostgreSQL, and no
request DTO or frontend contract changed.

### Concurrent delta serialization and watermark coupling

The deterministic PostgreSQL RED paused delta A after it prepared `stale-A`, allowed delta B to
commit `new-B`, then resumed A. Before the fix, B's version 3/link/pointer/hash were replaced by
stale version 4, and its watermark snapshot/update pair regressed even though `watermark_at`
remained monotonic. A separate RED seeded a 2099 watermark with hash `aaaa...` and proved that an
older 2026 candidate replaced only `snapshot_hash` and `updated_at`.

The first concurrency fix used an outer Spring transaction and a transaction-scoped advisory lock.
Independent QA then identified that this held one database transaction across the full scan, media
I/O, and every row, defeating the approved short per-row transaction and restartable-batch design.
The follow-up genuine RED proved both consequences: when row 793 failed media preflight, the valid
published import for row 792 had been rolled back (`expected 1, actual 0`), and real media prepare
observed active Spring transactions `[true, true]` instead of `[false, false]`. The post-failure
repair call itself completed, confirming the problem was transaction scope rather than basic lock
release.

Importer entry points now acquire PostgreSQL session advisory locks on one dedicated JDBC
connection without opening an outer Spring transaction. CURATED and AI batches lock their type;
mixed delta locks CURATED then AI in a fixed order. `CanonicalStoryWriter` therefore retains its
short per-row transaction, earlier successful rows remain committed when a later row fails, and the
final watermark update remains one atomic SQL statement that is skipped on batch failure. Normal
cleanup explicitly unlocks in reverse order before returning the connection to the pool. Any lock
acquisition, unlock, or connection-close uncertainty physically aborts the dedicated PostgreSQL
session and then closes the proxy, so a pooled connection cannot retain a session lock. Process or
connection loss likewise releases the locks server-side. There is no JVM-only lock or startup
runner.

Both watermark UPSERT paths still update `snapshot_hash` and `updated_at` only when the same
candidate wins the monotonic `watermark_at` comparison, while `last_legacy_id` remains independently
monotonic with `greatest`. The final focused session-lock slice passed 3/3 in 60 seconds: stale B
remained final, row 792 stayed published while row 793 failed without advancing the watermark, and
row 794 retried after failure with media prepare outside a Spring transaction. The final 27-test
class result above includes these cases.

### Synthetic paging lock fixture

Independent QA reran `LegacyStoryImportServicePagingTest` after the session-lock change and captured
the expected infrastructure RED: the synthetic `JdbcTemplate` had no `DataSource`, so the test
failed with `LEGACY_IMPORT_LOCK_DATASOURCE_REQUIRED` before reaching its 1001-ID keyset assertions.
The test fixture now supplies a fake DataSource, connection, advisory-lock/unlock statements, and a
successful boolean unlock result. It additionally verifies two distinct lock acquisitions, reverse
unlock order, and connection close only after both unlocks, while retaining the original discovery
pages `[1000, 1]` and maximum bulk size of 1000.

The requested fresh `--rerun-tasks` command passed the paging test 1/1 in 16 seconds with all five
Gradle tasks executed. The related importer/reconciliation paging unit slice then passed 5/5 in
20 seconds (import paging 1/1 and reconciliation 4/4). This correction changed test infrastructure
only; production code was unchanged, so the PostgreSQL full class was not rerun.

Parent/detail tombstones remain a documented Task 9 residual PG wiring gap, not a cutover bypass:
the current source scan omits deleted parent/detail rows, retained links become reconciliation
`unexpected`, and reconciliation therefore fails closed. Task 10 must add the explicit archive and
public-runtime non-exposure PostgreSQL coverage required by
`hardDeletedLegacyRowArchivesCanonicalStory`; Task 9 does not add that cutover behavior.

## Static and process audit

- Final post-QA `compileJava`: successful (`UP-TO-DATE`) in 2 seconds.
- `git diff --check`: clean. Index/staging: empty. No commit or push was made.
- The worktree status has 17 tracked modified entries and 14 untracked top-level entries;
  untracked migration/test directories contain the Task 9 classes described in this report.
  Pre-existing unrelated dirty files (`README.md`, `compose.yaml`, `application.yaml`, and
  `TrackedRuntimeConfigurationTest`) were preserved.
- Import methods are referenced only by the migration services. No controller, admin GET,
  `@PostConstruct`, `CommandLineRunner`, `ApplicationRunner`, or application-ready hook invokes
  import/reconciliation.
- No public setter was added to canonical story-content entities. No admin-package import or
  secret pattern was found in the Task 9 source/migration/report scope.
- V13 and V14 were not edited. V15 contains additive DDL only; it performs no legacy data
  backfill.

During an earlier paging verification, stale task-owned Gradle/test processes caused wrapper
timeouts before a fresh XML result. A JVM stack showed Spring test startup in
`DataInitializer.initFairytaleDetailDataIfMissing` waiting in a PostgreSQL socket, not an importer
keyset loop. Only the identified task-owned wrappers (116024, 97124), test worker (114396), and
combined daemon (113364) were stopped; unrelated shared Gradle daemons were preserved. Clean
reruns then passed the synthetic 1001-ID paging test 1/1 and the combined three-method delta
PostgreSQL slice 3/3. No database volume was deleted or reset.
