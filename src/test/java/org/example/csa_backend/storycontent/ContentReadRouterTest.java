package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ContentReadRouterTest {

    @Test
    void canonicalSourceQueriesControlOnceAndInvokesOnlyCanonicalBranch() {
        ContentMigrationControlRepository repository = mock(ContentMigrationControlRepository.class);
        ContentMigrationControl control = control(ContentSource.CANONICAL);
        when(repository.getSingleton()).thenReturn(control);
        ContentReadRouter router = new ContentReadRouter(repository);
        AtomicInteger legacyCalls = new AtomicInteger();
        AtomicInteger canonicalCalls = new AtomicInteger();

        String result = router.route(
            () -> "legacy-" + legacyCalls.incrementAndGet(),
            () -> "canonical-" + canonicalCalls.incrementAndGet()
        );

        assertThat(result).isEqualTo("canonical-1");
        assertThat(legacyCalls).hasValue(0);
        verify(repository).getSingleton();
    }

    @Test
    void legacySourceQueriesControlOnceAndInvokesOnlyLegacyBranch() {
        ContentMigrationControlRepository repository = mock(ContentMigrationControlRepository.class);
        ContentMigrationControl control = control(ContentSource.LEGACY);
        when(repository.getSingleton()).thenReturn(control);
        ContentReadRouter router = new ContentReadRouter(repository);
        AtomicInteger canonicalCalls = new AtomicInteger();

        String result = router.route(() -> "legacy", () -> {
            canonicalCalls.incrementAndGet();
            return "canonical";
        });

        assertThat(result).isEqualTo("legacy");
        assertThat(canonicalCalls).hasValue(0);
        verify(repository).getSingleton();
        verify(repository, never()).getSingletonForUpdate();
    }

    private ContentMigrationControl control(ContentSource source) {
        ContentMigrationControl control = new ContentMigrationControl();
        ReflectionTestUtils.setField(control, "singletonId", (short) 1);
        ReflectionTestUtils.setField(control, "readSource", source);
        return control;
    }
}
