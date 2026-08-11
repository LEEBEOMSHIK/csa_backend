package org.example.csa_backend.storycontent.migration;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.MigrationReconciliation;
import org.example.csa_backend.storycontent.MigrationReconciliationRepository;
import org.example.csa_backend.storycontent.OutboxEvent;
import org.example.csa_backend.storycontent.OutboxEventRepository;
import org.example.csa_backend.storycontent.ReconciliationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContentCutoverService {

    private final ContentMigrationControlRepository controlRepository;
    private final MigrationReconciliationRepository reconciliationRepository;
    private final OutboxEventRepository outboxRepository;
    private final LegacyStoryImportService importer;
    private final LegacyStoryReconciliationService reconciliation;
    private final ContentCutoverSmokeVerifier smokeVerifier;
    private final CutoverTransactionHook transactionHook;
    private final ContentCutoverTransactions transactions;
    private final ContentMigrationActor actor;
    private final ContractChecksum checksum;
    private final Clock clock;

    @Autowired
    public ContentCutoverService(
        ContentMigrationControlRepository controlRepository,
        MigrationReconciliationRepository reconciliationRepository,
        OutboxEventRepository outboxRepository,
        LegacyStoryImportService importer,
        LegacyStoryReconciliationService reconciliation,
        ContentCutoverSmokeVerifier smokeVerifier,
        CutoverTransactionHook transactionHook,
        ContentCutoverTransactions transactions,
        ContentMigrationActor actor,
        ContractChecksum checksum
    ) {
        this(
            controlRepository,
            reconciliationRepository,
            outboxRepository,
            importer,
            reconciliation,
            smokeVerifier,
            transactionHook,
            transactions,
            actor,
            checksum,
            Clock.systemUTC()
        );
    }

    ContentCutoverService(
        ContentMigrationControlRepository controlRepository,
        MigrationReconciliationRepository reconciliationRepository,
        OutboxEventRepository outboxRepository,
        LegacyStoryImportService importer,
        LegacyStoryReconciliationService reconciliation,
        ContentCutoverSmokeVerifier smokeVerifier,
        CutoverTransactionHook transactionHook,
        ContentCutoverTransactions transactions,
        ContentMigrationActor actor,
        ContractChecksum checksum,
        Clock clock
    ) {
        this.controlRepository = controlRepository;
        this.reconciliationRepository = reconciliationRepository;
        this.outboxRepository = outboxRepository;
        this.importer = importer;
        this.reconciliation = reconciliation;
        this.smokeVerifier = smokeVerifier;
        this.transactionHook = transactionHook;
        this.transactions = transactions;
        this.actor = actor;
        this.checksum = checksum;
        this.clock = clock;
    }

    public MigrationEpoch requestFreeze(String auditActor) {
        if (!actor.value().equals(auditActor)) {
            throw new IllegalArgumentException("INVALID_CONTENT_MIGRATION_ACTOR");
        }
        return transactions.required(() -> {
            ContentMigrationControl control = controlRepository.getSingletonForUpdate();
            long epoch = control.requestFreeze(clock.instant());
            outboxRepository.save(OutboxEvent.pending(
                epoch,
                "CUTOVER_FREEZE_REQUESTED",
                Map.of(
                    "actor", actor.value(),
                    "epoch", epoch,
                    "timestamp", clock.instant().toString()
                ),
                clock.instant()
            ));
            return new MigrationEpoch(epoch);
        });
    }

    @Transactional(readOnly = true)
    public long nextFreezeEpoch() {
        return Math.addExact(controlRepository.getSingleton().getBarrierEpoch(), 1L);
    }

    public void acknowledgeFrozen(String serviceInstance, long epoch) {
        transactions.required(() -> {
            controlRepository.getSingletonForUpdate().acknowledge(serviceInstance, epoch, clock.instant());
            return null;
        });
    }

    public CutoverResult cutover(long epoch) {
        return transactions.required(() -> {
            ContentMigrationControl control = controlRepository.getSingletonForUpdate();
            control.assertBothBackendsFrozen(epoch);
            MigrationReconciliation report = reconciliationRepository.requireSuccessful(epoch);
            control.prepareCanonicalCutover(epoch, report.getChecksum(), clock.instant());
            outboxRepository.save(OutboxEvent.pending(
                epoch,
                "CUTOVER_PREPARED",
                Map.of("epoch", epoch, "checksum", report.getChecksum()),
                clock.instant()
            ));
            transactionHook.afterCanonicalSourceUpdate();
            return CutoverResult.prepared(epoch, report.getChecksum());
        });
    }

    public ReconciliationReport runFinalReconciliation(long epoch) {
        transactions.required(() -> {
            controlRepository.getSingletonForUpdate().assertBothBackendsFrozen(epoch);
            return null;
        });

        ReconciliationReport report;
        RuntimeException reconciliationFailure = null;
        try {
            importer.importDelta(Instant.EPOCH);
            report = reconciliation.reconcileAll();
        } catch (LegacyImportException exception) {
            reconciliationFailure = exception;
            report = failedReport(exception.getCode());
        } catch (RuntimeException exception) {
            reconciliationFailure = exception;
            report = failedReport("FINAL_RECONCILIATION_RUNTIME_FAILURE");
        }
        ReconciliationReport completedReport = report;
        RuntimeException originalFailure = reconciliationFailure;

        try {
            return transactions.required(() -> {
                ContentMigrationControl control = controlRepository.getSingletonForUpdate();
                control.assertBothBackendsFrozen(epoch);
                ReconciliationStatus status = completedReport.complete()
                    ? ReconciliationStatus.SUCCEEDED
                    : ReconciliationStatus.FAILED;
                reconciliationRepository.save(MigrationReconciliation.completed(
                    epoch,
                    status,
                    completedReport.checksum(),
                    reportJson(completedReport),
                    clock.instant()
                ));
                if (completedReport.complete()) {
                    control.recordFinalReconciliation(epoch, completedReport.checksum(), clock.instant());
                } else {
                    control.rollbackToLegacy(epoch, clock.instant());
                }
                return completedReport;
            });
        } catch (RuntimeException completionFailure) {
            if (originalFailure != null) {
                if (completionFailure != originalFailure) {
                    originalFailure.addSuppressed(completionFailure);
                }
                throw originalFailure;
            }
            throw completionFailure;
        }
    }

    public SmokeResult verifySmoke(long epoch) {
        return transactions.required(() -> {
            ContentMigrationControl control = controlRepository.getSingletonForUpdate();
            control.assertCutoverPending(epoch);
            SmokeResult result = smokeVerifier.verify(epoch);
            if (!result.passed()) {
                control.rollbackToLegacy(epoch, clock.instant());
                outboxRepository.save(OutboxEvent.pending(
                    epoch,
                    "CUTOVER_SMOKE_FAILED",
                    Map.of("epoch", epoch, "code", result.code()),
                    clock.instant()
                ));
                return result;
            }
            control.recordSmoke(epoch, result.checksum(), clock.instant());
            return result;
        });
    }

    public CutoverResult openCanonicalWrites(long epoch) {
        return transactions.required(() -> {
            ContentMigrationControl control = controlRepository.getSingletonForUpdate();
            MigrationReconciliation report = reconciliationRepository.requireSuccessful(epoch);
            control.assertPassingSmoke(epoch, report.getChecksum());
            control.openCanonicalWrites(epoch, clock.instant());
            outboxRepository.save(OutboxEvent.pending(
                epoch,
                "CUTOVER_COMPLETED",
                Map.of(
                    "epoch", epoch,
                    "reconciliationChecksum", report.getChecksum(),
                    "smokeChecksum", control.getSmokeHash()
                ),
                clock.instant()
            ));
            return CutoverResult.open(epoch, report.getChecksum());
        });
    }

    public CutoverResult rollbackToLegacy(long epoch, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("CONTENT_MIGRATION_ROLLBACK_REASON_REQUIRED");
        }
        return transactions.required(() -> {
            ContentMigrationControl control = controlRepository.getSingletonForUpdate();
            control.assertCutoverPending(epoch);
            String reconciliationChecksum = control.getReconciliationHash();
            control.rollbackToLegacy(epoch, clock.instant());
            outboxRepository.save(OutboxEvent.pending(
                epoch,
                "CUTOVER_ROLLED_BACK",
                Map.of("epoch", epoch, "reason", reason),
                clock.instant()
            ));
            return CutoverResult.rolledBack(epoch, reconciliationChecksum);
        });
    }

    private ReconciliationReport failedReport(String code) {
        return new ReconciliationReport(
            false,
            0,
            0,
            List.of(),
            List.of(),
            List.of(code),
            0,
            checksum.ofParts(List.of("FINAL_RECONCILIATION_FAILED", code))
        );
    }

    private Map<String, Object> reportJson(ReconciliationReport report) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("complete", report.complete());
        json.put("legacyCount", report.legacyCount());
        json.put("linkedCount", report.linkedCount());
        json.put("missingLinks", report.missingLinks());
        json.put("unexpectedLinks", report.unexpectedLinks());
        json.put("hashMismatches", report.hashMismatches());
        json.put("openMismatchCount", report.openMismatchCount());
        json.put("checksum", report.checksum());
        return json;
    }
}
