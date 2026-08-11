package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentMigrationGateTest {

    private final ContentMigrationControlRepository controlRepository = mock(ContentMigrationControlRepository.class);
    private final ContentMigrationControl control = mock(ContentMigrationControl.class);
    private ContentMigrationGate gate;

    @BeforeEach
    void setUp() {
        gate = new ContentMigrationGate(controlRepository);
        when(controlRepository.getSingleton()).thenReturn(control);
        when(control.getBarrierEpoch()).thenReturn(17L);
    }

    @Test
    void freezeRejectsEveryWriteWithStableServiceUnavailableCodeAndEpoch() {
        when(control.getState()).thenReturn(MigrationState.FREEZE_REQUESTED);

        assertThatThrownBy(() -> gate.assertWritesAllowed(ContentWriteKind.LEGACY_AI))
            .isInstanceOfSatisfying(ContentMigrationException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.getStatus().value()).isEqualTo(503);
                org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("CONTENT_MIGRATION_FREEZE");
                org.assertj.core.api.Assertions.assertThat(exception.getBarrierEpoch()).isEqualTo(17L);
            });
    }

    @Test
    void openGateRejectsLegacyWriteAfterCanonicalWriteSourceOpens() {
        when(control.getState()).thenReturn(MigrationState.OPEN);
        when(control.getWriteSource()).thenReturn(ContentSource.CANONICAL);

        assertThatThrownBy(() -> gate.assertWritesAllowed(ContentWriteKind.LEGACY_CURATED))
            .isInstanceOfSatisfying(ContentMigrationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("LEGACY_WRITE_DISABLED"));
    }

    @Test
    void openGateRejectsCanonicalWriteWhileLegacyWriteSourceRemainsActive() {
        when(control.getState()).thenReturn(MigrationState.OPEN);
        when(control.getWriteSource()).thenReturn(ContentSource.LEGACY);

        assertThatThrownBy(() -> gate.assertWritesAllowed(ContentWriteKind.CANONICAL_AUTHORING))
            .isInstanceOfSatisfying(ContentMigrationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getCode()).isEqualTo("CANONICAL_WRITE_DISABLED"));
    }

    @Test
    void openGateAllowsWriteMatchingActiveSource() {
        when(control.getState()).thenReturn(MigrationState.OPEN);
        when(control.getWriteSource()).thenReturn(ContentSource.LEGACY);

        assertThatCode(() -> gate.assertWritesAllowed(ContentWriteKind.LEGACY_AI)).doesNotThrowAnyException();
    }
}
