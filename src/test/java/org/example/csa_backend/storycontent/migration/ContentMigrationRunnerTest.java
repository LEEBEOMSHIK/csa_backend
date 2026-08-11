package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.LegacyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Profile;

class ContentMigrationRunnerTest {

    private ContentCutoverService cutoverService;
    private LegacyStoryImportService importService;
    private ContentMigrationActor actor;
    private ContentMigrationRunner runner;

    @BeforeEach
    void setUp() {
        cutoverService = mock(ContentCutoverService.class);
        importService = mock(LegacyStoryImportService.class);
        actor = new ContentMigrationActor();
        runner = new ContentMigrationRunner(cutoverService, importService, actor, new ContractChecksum());
    }

    @Test
    void runnerIsRegisteredOnlyForContentMigrationProfile() {
        Profile profile = ContentMigrationRunner.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactly("content-migration");
    }

    @Test
    void confirmationMismatchAbortsBeforeAnyStateChangingServiceCall() {
        DefaultApplicationArguments arguments = args(
            "finalize",
            41L,
            "--content.migration.confirm-epoch=40"
        );

        assertThatThrownBy(() -> runner.run(arguments))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CONTENT_MIGRATION_CONFIRM_EPOCH_MISMATCH");
        verifyNoInteractions(cutoverService, importService);
    }

    @Test
    void requestFreezeValidatesNextEpochBeforeStartingMutation() {
        when(cutoverService.nextFreezeEpoch()).thenReturn(42L);
        DefaultApplicationArguments arguments = args(
            "request-freeze",
            41L,
            "--content.migration.confirm-epoch=41"
        );

        assertThatThrownBy(() -> runner.run(arguments))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CONTENT_MIGRATION_EPOCH_MISMATCH");
        verify(cutoverService, never()).requestFreeze(actor.value());
    }

    @Test
    void confirmedRequestFreezeUsesFixedAuditActor() throws Exception {
        when(cutoverService.nextFreezeEpoch()).thenReturn(41L);
        when(cutoverService.requestFreeze(actor.value())).thenReturn(new MigrationEpoch(41L));

        runner.run(args(
            "request-freeze",
            41L,
            "--content.migration.confirm-epoch=41"
        ));

        verify(cutoverService).requestFreeze("csa_backend:content-migration-cli");
    }

    @Test
    void finalizeReconcilesThenPreparesCutoverForSameEpoch() throws Exception {
        ReconciliationReport report = new ReconciliationReport(
            true, 1, 1, List.of(), List.of(), List.of(), 0, "a".repeat(64)
        );
        when(cutoverService.runFinalReconciliation(41L)).thenReturn(report);

        runner.run(args("finalize", 41L, "--content.migration.confirm-epoch=41"));

        InOrder order = inOrder(cutoverService);
        order.verify(cutoverService).runFinalReconciliation(41L);
        order.verify(cutoverService).cutover(41L);
    }

    @Test
    void bulkImportDryRunReadsHashesWithoutInvokingImportWrites() throws Exception {
        when(importService.sourceHashes(LegacyType.CURATED)).thenReturn(Map.of(1L, "a".repeat(64)));
        when(importService.sourceHashes(LegacyType.AI)).thenReturn(Map.of(2L, "b".repeat(64)));

        runner.run(args("bulk-import", 41L, "--content.migration.dry-run=true"));

        verify(importService).sourceHashes(LegacyType.CURATED);
        verify(importService).sourceHashes(LegacyType.AI);
        verify(importService, never()).importCuratedBatch(0, 500);
        verify(importService, never()).importAiBatch(0, 500);
        verifyNoInteractions(cutoverService);
    }

    @Test
    void bulkImportRequiresExplicitDryRunArgumentBeforeAnyServiceCall() {
        DefaultApplicationArguments arguments = args("bulk-import", 41L);

        assertThatThrownBy(() -> runner.run(arguments))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CONTENT_MIGRATION_ARGUMENT_REQUIRED: content.migration.dry-run");
        verifyNoInteractions(cutoverService, importService);
    }

    @Test
    void bulkImportRejectsInvalidDryRunLiteralBeforeAnyServiceCall() {
        DefaultApplicationArguments arguments = args(
            "bulk-import",
            41L,
            "--content.migration.dry-run=treu"
        );

        assertThatThrownBy(() -> runner.run(arguments))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("CONTENT_MIGRATION_ARGUMENT_INVALID: content.migration.dry-run");
        verifyNoInteractions(cutoverService, importService);
    }

    @Test
    void bulkImportAcceptsExplicitFalseAndInvokesImportBatches() throws Exception {
        ImportBatchResult complete = new ImportBatchResult(0, 0, 0, true);
        when(importService.importCuratedBatch(0, 500)).thenReturn(complete);
        when(importService.importAiBatch(0, 500)).thenReturn(complete);

        runner.run(args("bulk-import", 41L, "--content.migration.dry-run=false"));

        verify(importService).importCuratedBatch(0, 500);
        verify(importService).importAiBatch(0, 500);
        verify(importService, never()).sourceHashes(LegacyType.CURATED);
        verify(importService, never()).sourceHashes(LegacyType.AI);
        verifyNoInteractions(cutoverService);
    }

    private DefaultApplicationArguments args(String command, long epoch, String... extras) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        values.add("--content.migration.command=" + command);
        values.add("--content.migration.epoch=" + epoch);
        values.addAll(List.of(extras));
        return new DefaultApplicationArguments(values.toArray(String[]::new));
    }
}
