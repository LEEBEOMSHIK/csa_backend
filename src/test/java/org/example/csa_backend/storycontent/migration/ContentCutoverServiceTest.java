package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.MigrationReconciliation;
import org.example.csa_backend.storycontent.MigrationReconciliationRepository;
import org.example.csa_backend.storycontent.MigrationState;
import org.example.csa_backend.storycontent.OutboxEvent;
import org.example.csa_backend.storycontent.OutboxEventRepository;
import org.example.csa_backend.storycontent.ReconciliationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ContentCutoverServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-11T03:00:00Z");

    @Mock
    private ContentMigrationControlRepository controlRepository;

    @Mock
    private MigrationReconciliationRepository reconciliationRepository;

    @Mock
    private OutboxEventRepository outboxRepository;

    @Mock
    private LegacyStoryImportService importer;

    @Mock
    private LegacyStoryReconciliationService reconciliation;

    @Mock
    private ContentCutoverSmokeVerifier smokeVerifier;

    @Mock
    private CutoverTransactionHook transactionHook;

    private ContentMigrationControl control;
    private ContentCutoverService service;

    @BeforeEach
    void setUp() {
        control = control(MigrationState.OPEN, ContentSource.LEGACY, ContentSource.LEGACY, 40L, null, null);
        when(controlRepository.getSingletonForUpdate()).thenReturn(control);
        service = new ContentCutoverService(
            controlRepository,
            reconciliationRepository,
            outboxRepository,
            importer,
            reconciliation,
            smokeVerifier,
            transactionHook,
            new ContentCutoverTransactions(),
            new ContentMigrationActor(),
            new ContractChecksum(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void requestFreezeIncrementsEpochClearsEvidenceAndAuditsFixedActor() {
        ReflectionTestUtils.setField(control, "backendAckEpoch", 40L);
        ReflectionTestUtils.setField(control, "adminBackendAckEpoch", 40L);
        ReflectionTestUtils.setField(control, "reconciliationHash", "a".repeat(64));
        ReflectionTestUtils.setField(control, "smokeHash", "b".repeat(64));
        ReflectionTestUtils.setField(control, "smokePassedAt", NOW.minusSeconds(10));

        MigrationEpoch epoch = service.requestFreeze(new ContentMigrationActor().value());

        assertThat(epoch.value()).isEqualTo(41L);
        assertThat(control.getState()).isEqualTo(MigrationState.FREEZE_REQUESTED);
        assertThat(control.getBackendAckEpoch()).isNull();
        assertThat(control.getAdminBackendAckEpoch()).isNull();
        assertThat(control.getReconciliationHash()).isNull();
        assertThat(control.getSmokeHash()).isNull();
        assertThat(control.getSmokePassedAt()).isNull();
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("CUTOVER_FREEZE_REQUESTED");
        assertThat(event.getValue().getBarrierEpoch()).isEqualTo(41L);
        assertThat(event.getValue().getPayloadJson())
            .containsEntry("actor", "csa_backend:content-migration-cli")
            .containsEntry("epoch", 41L);
    }

    @Test
    void cutoverWaitsForBothBackendAcknowledgements() {
        ReflectionTestUtils.setField(control, "state", MigrationState.FREEZE_REQUESTED);
        ReflectionTestUtils.setField(control, "barrierEpoch", 41L);
        service.acknowledgeFrozen("csa_backend", 41L);

        assertThatThrownBy(() -> service.cutover(41L))
            .isInstanceOfSatisfying(ContentMigrationException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("CSA_ADM_BACKEND_ACK_REQUIRED"));
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        verify(transactionHook, never()).afterCanonicalSourceUpdate();
    }

    @Test
    void cutoverAtomicallyPreparesCanonicalSourcesAfterSuccessfulReconciliation() {
        ReflectionTestUtils.setField(control, "state", MigrationState.FROZEN);
        ReflectionTestUtils.setField(control, "barrierEpoch", 41L);
        ReflectionTestUtils.setField(control, "backendAckEpoch", 41L);
        ReflectionTestUtils.setField(control, "adminBackendAckEpoch", 41L);
        MigrationReconciliation successful = mock(MigrationReconciliation.class);
        when(successful.getChecksum()).thenReturn("c".repeat(64));
        when(reconciliationRepository.requireSuccessful(41L)).thenReturn(successful);

        CutoverResult result = service.cutover(41L);

        assertThat(result.readSource()).isEqualTo(ContentSource.CANONICAL);
        assertThat(result.writeSource()).isEqualTo(ContentSource.CANONICAL);
        assertThat(control.getState()).isEqualTo(MigrationState.CUTOVER_PENDING);
        assertThat(control.getReconciliationHash()).isEqualTo("c".repeat(64));
        verify(transactionHook).afterCanonicalSourceUpdate();
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void finalReconciliationRunsFullDeltaThenStoresSameEpochSuccess() {
        frozenAt(41L);
        ReconciliationReport report = new ReconciliationReport(
            true,
            2,
            2,
            List.of(),
            List.of(),
            List.of(),
            0,
            "d".repeat(64)
        );
        when(reconciliation.reconcileAll()).thenReturn(report);

        ReconciliationReport result = service.runFinalReconciliation(41L);

        assertThat(result).isEqualTo(report);
        verify(importer).importDelta(Instant.EPOCH);
        ArgumentCaptor<MigrationReconciliation> stored =
            ArgumentCaptor.forClass(MigrationReconciliation.class);
        verify(reconciliationRepository).save(stored.capture());
        assertThat(stored.getValue().getEpoch()).isEqualTo(41L);
        assertThat(stored.getValue().getStatus()).isEqualTo(ReconciliationStatus.SUCCEEDED);
        assertThat(stored.getValue().getChecksum()).isEqualTo("d".repeat(64));
        assertThat(control.getState()).isEqualTo(MigrationState.FROZEN);
    }

    @Test
    void finalImportAndReconciliationRunOutsideControlTransactions() {
        frozenAt(41L);
        AtomicBoolean insideControlTransaction = new AtomicBoolean();
        ContentCutoverTransactions boundaries = mock(ContentCutoverTransactions.class);
        when(boundaries.required(any())).thenAnswer(invocation -> {
            Supplier<?> action = invocation.getArgument(0);
            assertThat(insideControlTransaction.compareAndSet(false, true)).isTrue();
            try {
                return action.get();
            } finally {
                insideControlTransaction.set(false);
            }
        });
        when(importer.importDelta(Instant.EPOCH)).thenAnswer(invocation -> {
            assertThat(insideControlTransaction).isFalse();
            return new ImportBatchResult(0, 0, 0, true);
        });
        ReconciliationReport report = new ReconciliationReport(
            true, 0, 0, List.of(), List.of(), List.of(), 0, "d".repeat(64)
        );
        when(reconciliation.reconcileAll()).thenAnswer(invocation -> {
            assertThat(insideControlTransaction).isFalse();
            return report;
        });
        ContentCutoverService isolated = new ContentCutoverService(
            controlRepository,
            reconciliationRepository,
            outboxRepository,
            importer,
            reconciliation,
            smokeVerifier,
            transactionHook,
            boundaries,
            new ContentMigrationActor(),
            new ContractChecksum(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        isolated.runFinalReconciliation(41L);

        assertThat(insideControlTransaction).isFalse();
        verify(boundaries, org.mockito.Mockito.times(2)).required(any());
    }

    @Test
    void mismatchStoresFailureAndRestoresLegacyReadWriteOpen() {
        frozenAt(41L);
        ReconciliationReport report = new ReconciliationReport(
            false,
            2,
            1,
            List.of(),
            List.of("AI:9"),
            List.of(),
            0,
            "e".repeat(64)
        );
        when(reconciliation.reconcileAll()).thenReturn(report);

        service.runFinalReconciliation(41L);

        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getWriteSource()).isEqualTo(ContentSource.LEGACY);
        ArgumentCaptor<MigrationReconciliation> stored =
            ArgumentCaptor.forClass(MigrationReconciliation.class);
        verify(reconciliationRepository).save(stored.capture());
        assertThat(stored.getValue().getStatus()).isEqualTo(ReconciliationStatus.FAILED);
    }

    @Test
    void finalImportFailureStoresFailureAndRestoresLegacyReadWriteOpen() {
        frozenAt(41L);
        when(importer.importDelta(Instant.EPOCH)).thenThrow(new LegacyImportException(
            "LEGACY_MEDIA_PREFLIGHT_FAILED",
            "missing source media"
        ));

        ReconciliationReport result = service.runFinalReconciliation(41L);

        assertThat(result.complete()).isFalse();
        assertThat(result.hashMismatches()).containsExactly("LEGACY_MEDIA_PREFLIGHT_FAILED");
        verify(reconciliation, never()).reconcileAll();
        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getWriteSource()).isEqualTo(ContentSource.LEGACY);
        ArgumentCaptor<MigrationReconciliation> stored =
            ArgumentCaptor.forClass(MigrationReconciliation.class);
        verify(reconciliationRepository).save(stored.capture());
        assertThat(stored.getValue().getEpoch()).isEqualTo(41L);
        assertThat(stored.getValue().getStatus()).isEqualTo(ReconciliationStatus.FAILED);
    }

    @Test
    void finalImportDataAccessFailureStoresStableFailureAndRestoresLegacyReadWriteOpen() {
        frozenAt(41L);
        when(importer.importDelta(Instant.EPOCH))
            .thenThrow(new DataAccessResourceFailureException("database unavailable"));

        ReconciliationReport result = service.runFinalReconciliation(41L);

        assertThat(result.complete()).isFalse();
        assertThat(result.hashMismatches())
            .containsExactly("FINAL_RECONCILIATION_RUNTIME_FAILURE");
        verify(reconciliation, never()).reconcileAll();
        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getWriteSource()).isEqualTo(ContentSource.LEGACY);
        ArgumentCaptor<MigrationReconciliation> stored =
            ArgumentCaptor.forClass(MigrationReconciliation.class);
        verify(reconciliationRepository).save(stored.capture());
        assertThat(stored.getValue().getStatus()).isEqualTo(ReconciliationStatus.FAILED);
    }

    @Test
    void unexpectedReconciliationRuntimeStoresStableFailureAndRestoresLegacyReadWriteOpen() {
        frozenAt(41L);
        when(reconciliation.reconcileAll()).thenThrow(new IllegalStateException("unexpected"));

        ReconciliationReport result = service.runFinalReconciliation(41L);

        assertThat(result.complete()).isFalse();
        assertThat(result.hashMismatches())
            .containsExactly("FINAL_RECONCILIATION_RUNTIME_FAILURE");
        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getWriteSource()).isEqualTo(ContentSource.LEGACY);
        ArgumentCaptor<MigrationReconciliation> stored =
            ArgumentCaptor.forClass(MigrationReconciliation.class);
        verify(reconciliationRepository).save(stored.capture());
        assertThat(stored.getValue().getStatus()).isEqualTo(ReconciliationStatus.FAILED);
    }

    @Test
    void failedRollbackTransactionPropagatesOriginalReconciliationFailure() {
        frozenAt(41L);
        IllegalStateException original = new IllegalStateException("unexpected");
        IllegalStateException rollbackFailure = new IllegalStateException("rollback transaction failed");
        when(reconciliation.reconcileAll()).thenThrow(original);
        AtomicInteger transactionCalls = new AtomicInteger();
        ContentCutoverTransactions boundaries = new ContentCutoverTransactions() {
            @Override
            public <T> T required(Supplier<T> action) {
                if (transactionCalls.incrementAndGet() == 2) {
                    throw rollbackFailure;
                }
                return action.get();
            }
        };
        ContentCutoverService isolated = new ContentCutoverService(
            controlRepository,
            reconciliationRepository,
            outboxRepository,
            importer,
            reconciliation,
            smokeVerifier,
            transactionHook,
            boundaries,
            new ContentMigrationActor(),
            new ContractChecksum(),
            Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> isolated.runFinalReconciliation(41L))
            .isSameAs(original)
            .satisfies(exception -> assertThat(exception.getSuppressed())
                .containsExactly(rollbackFailure));
        verify(reconciliationRepository, never()).save(any());
        assertThat(control.getState()).isEqualTo(MigrationState.FROZEN);
    }

    @Test
    void smokeFailureRollsBackBeforeCanonicalWritesOpen() {
        cutoverPendingAt(41L);
        when(smokeVerifier.verify(41L)).thenReturn(SmokeResult.failed("PUBLIC_RUNTIME_MISMATCH"));

        SmokeResult result = service.verifySmoke(41L);

        assertThat(result.passed()).isFalse();
        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
        assertThat(control.getReadSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getWriteSource()).isEqualTo(ContentSource.LEGACY);
    }

    @Test
    void openWritesRequiresStoredPassingSmokeForSameEpoch() {
        cutoverPendingAt(41L);
        MigrationReconciliation successful = mock(MigrationReconciliation.class);
        when(successful.getChecksum()).thenReturn("c".repeat(64));
        when(reconciliationRepository.requireSuccessful(41L)).thenReturn(successful);

        assertThatThrownBy(() -> service.openCanonicalWrites(41L))
            .isInstanceOfSatisfying(ContentMigrationException.class, exception ->
                assertThat(exception.getCode()).isEqualTo("CUTOVER_SMOKE_REQUIRED"));

        when(smokeVerifier.verify(41L)).thenReturn(SmokeResult.passed("f".repeat(64)));
        assertThat(service.verifySmoke(41L).passed()).isTrue();
        CutoverResult result = service.openCanonicalWrites(41L);

        assertThat(result.writeSource()).isEqualTo(ContentSource.CANONICAL);
        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
    }

    @Test
    void rollbackBeforeWritesOpenRestoresLegacyAndAuditsReason() {
        cutoverPendingAt(41L);

        CutoverResult result = service.rollbackToLegacy(41L, "operator-abort");

        assertThat(result.readSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(result.writeSource()).isEqualTo(ContentSource.LEGACY);
        assertThat(control.getState()).isEqualTo(MigrationState.OPEN);
        ArgumentCaptor<OutboxEvent> event = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(event.capture());
        assertThat(event.getValue().getEventType()).isEqualTo("CUTOVER_ROLLED_BACK");
        assertThat(event.getValue().getPayloadJson()).containsEntry("reason", "operator-abort");
    }

    private void frozenAt(long epoch) {
        ReflectionTestUtils.setField(control, "state", MigrationState.FROZEN);
        ReflectionTestUtils.setField(control, "barrierEpoch", epoch);
        ReflectionTestUtils.setField(control, "backendAckEpoch", epoch);
        ReflectionTestUtils.setField(control, "adminBackendAckEpoch", epoch);
    }

    private void cutoverPendingAt(long epoch) {
        ReflectionTestUtils.setField(control, "state", MigrationState.CUTOVER_PENDING);
        ReflectionTestUtils.setField(control, "readSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(control, "writeSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(control, "barrierEpoch", epoch);
        ReflectionTestUtils.setField(control, "backendAckEpoch", epoch);
        ReflectionTestUtils.setField(control, "adminBackendAckEpoch", epoch);
        ReflectionTestUtils.setField(control, "reconciliationHash", "c".repeat(64));
    }

    private ContentMigrationControl control(
        MigrationState state,
        ContentSource readSource,
        ContentSource writeSource,
        long epoch,
        Long backendAck,
        Long adminAck
    ) {
        ContentMigrationControl value = new ContentMigrationControl();
        ReflectionTestUtils.setField(value, "singletonId", (short) 1);
        ReflectionTestUtils.setField(value, "state", state);
        ReflectionTestUtils.setField(value, "readSource", readSource);
        ReflectionTestUtils.setField(value, "writeSource", writeSource);
        ReflectionTestUtils.setField(value, "barrierEpoch", epoch);
        ReflectionTestUtils.setField(value, "backendAckEpoch", backendAck);
        ReflectionTestUtils.setField(value, "adminBackendAckEpoch", adminAck);
        ReflectionTestUtils.setField(value, "updatedAt", NOW.minusSeconds(60));
        return value;
    }
}
