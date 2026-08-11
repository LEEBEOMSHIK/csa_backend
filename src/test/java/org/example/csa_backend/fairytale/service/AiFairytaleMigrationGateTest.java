package org.example.csa_backend.fairytale.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.AiFairytalePageRepository;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.example.csa_backend.storycontent.migration.ContentMigrationException;
import org.example.csa_backend.storycontent.migration.ContentMigrationGate;
import org.example.csa_backend.storycontent.migration.ContentWriteActivityTracker;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AiFairytaleMigrationGateTest {

    private final AiFairytaleRepository fairytaleRepository = mock(AiFairytaleRepository.class);
    private final AiFairytalePageRepository pageRepository = mock(AiFairytalePageRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ContentMigrationException frozen = ContentMigrationException.serviceUnavailable(
        "CONTENT_MIGRATION_FREEZE",
        43L
    );
    private final AiFairytaleService service = frozenService();

    @Test
    void freezeRejectsGenerateShareAndDeleteBeforeAnyLegacySideEffect() {
        assertThatThrownBy(() -> service.generate(null, 1L)).isSameAs(frozen);
        assertThatThrownBy(() -> service.toggleShare(1L, 2L)).isSameAs(frozen);
        assertThatThrownBy(() -> service.deleteMyFairytale(1L, 2L)).isSameAs(frozen);

        verifyNoInteractions(fairytaleRepository, pageRepository, fileStorageService, userRepository);
    }

    @SuppressWarnings("unchecked")
    private AiFairytaleService frozenService() {
        ContentMigrationGate gate = mock(ContentMigrationGate.class);
        doThrow(frozen).when(gate).assertWritesAllowed(any());
        return new AiFairytaleService(
            fairytaleRepository,
            pageRepository,
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            fileStorageService,
            mock(AiVideoAssemblyService.class),
            userRepository,
            new AiGenerationProperties(),
            mock(LegacyShadowReadObserver.class),
            new ContentWriteActivityTracker(gate),
            legacyReadRouter(),
            mock(org.example.csa_backend.fairytale.CanonicalAiReadRepository.class)
        );
    }

    private org.example.csa_backend.storycontent.ContentReadRouter legacyReadRouter() {
        var repository = mock(
            org.example.csa_backend.storycontent.ContentMigrationControlRepository.class);
        var control = mock(org.example.csa_backend.storycontent.ContentMigrationControl.class);
        org.mockito.Mockito.when(repository.getSingleton()).thenReturn(control);
        org.mockito.Mockito.when(control.getReadSource())
            .thenReturn(org.example.csa_backend.storycontent.ContentSource.LEGACY);
        return new org.example.csa_backend.storycontent.ContentReadRouter(repository);
    }
}
