package org.example.csa_backend.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.example.csa_backend.fairytale.CategoryRepository;
import org.example.csa_backend.fairytale.FairytaleDetailRepository;
import org.example.csa_backend.fairytale.FairytaleRepository;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.migration.ContentMigrationException;
import org.example.csa_backend.storycontent.migration.ContentMigrationGate;
import org.example.csa_backend.storycontent.migration.ContentWriteActivityTracker;
import org.example.csa_backend.storycontent.migration.ContentWriteKind;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class DataInitializerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final FairytaleRepository fairytaleRepository = mock(FairytaleRepository.class);
    private final FairytaleDetailRepository fairytaleDetailRepository =
        mock(FairytaleDetailRepository.class);
    private final ContentMigrationControlRepository contentMigrationControlRepository =
        mock(ContentMigrationControlRepository.class);

    @Test
    void frozenContentSeedIsRejectedBeforeAnyCategoryFairytaleOrDetailMutation() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);
        when(categoryRepository.count()).thenReturn(0L);
        when(fairytaleDetailRepository.count()).thenReturn(0L);
        when(contentMigrationControlRepository.existsById((short) 1)).thenReturn(true);
        ContentMigrationGate gate = mock(ContentMigrationGate.class);
        doThrow(ContentMigrationException.serviceUnavailable("CONTENT_MIGRATION_FREEZE", 41L))
            .when(gate).assertWritesAllowed(ContentWriteKind.LEGACY_CURATED);
        ContentWriteActivityTracker tracker = new ContentWriteActivityTracker(gate);
        DataInitializer initializer = initializer(tracker);

        assertThatThrownBy(() -> initializer.run(mock(ApplicationArguments.class)))
            .isInstanceOfSatisfying(ContentMigrationException.class, exception ->
                org.assertj.core.api.Assertions.assertThat(exception.getCode())
                    .isEqualTo("CONTENT_MIGRATION_FREEZE"));

        verify(categoryRepository, never()).save(any());
        verify(fairytaleRepository, never()).save(any());
        verify(fairytaleRepository, never()).saveAll(any());
        verify(fairytaleDetailRepository, never()).save(any());
    }

    @Test
    void missingMigrationControlSkipsContentSeedWithoutTracker() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);
        when(categoryRepository.count()).thenReturn(0L);
        when(fairytaleDetailRepository.count()).thenReturn(0L);
        when(contentMigrationControlRepository.existsById((short) 1)).thenReturn(false);
        ContentWriteActivityTracker tracker = mock(ContentWriteActivityTracker.class);
        DataInitializer initializer = initializer(tracker);

        initializer.run(mock(ApplicationArguments.class));

        verifyNoInteractions(tracker);
        verify(categoryRepository, never()).save(any());
        verify(fairytaleRepository, never()).save(any());
        verify(fairytaleDetailRepository, never()).save(any());
    }

    @Test
    void fullySeededContentDoesNotEnterWriteTracker() {
        when(userRepository.existsByEmail("test@test.com")).thenReturn(true);
        when(categoryRepository.count()).thenReturn(1L);
        when(fairytaleDetailRepository.count()).thenReturn(1L);
        ContentWriteActivityTracker tracker = mock(ContentWriteActivityTracker.class);
        DataInitializer initializer = initializer(tracker);

        initializer.run(mock(ApplicationArguments.class));

        verifyNoInteractions(tracker);
        verify(categoryRepository, never()).save(any());
        verify(fairytaleRepository, never()).save(any());
        verify(fairytaleDetailRepository, never()).save(any());
    }

    private DataInitializer initializer(ContentWriteActivityTracker tracker) {
        return new DataInitializer(
            userRepository,
            passwordEncoder,
            categoryRepository,
            fairytaleRepository,
            fairytaleDetailRepository,
            contentMigrationControlRepository,
            tracker
        );
    }
}
