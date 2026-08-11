package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.junit.jupiter.api.Test;

class LegacyShadowReadObserverTest {

    @Test
    void shadowComparisonIsDisabledByDefaultGate() {
        LegacyShadowCompareService compareService = mock(LegacyShadowCompareService.class);
        LegacyShadowReadObserver observer = new LegacyShadowReadObserver(compareService, false);

        observer.observe(LegacyType.CURATED, 7L);

        verifyNoInteractions(compareService);
    }

    @Test
    void enabledCompareFailureNeverEscapesIntoTheLegacyReadPath() {
        LegacyShadowCompareService compareService = mock(LegacyShadowCompareService.class);
        when(compareService.compare(LegacyType.CURATED, 7L))
            .thenThrow(new IllegalStateException("canonical shadow unavailable"));
        LegacyShadowReadObserver observer = new LegacyShadowReadObserver(compareService, true);

        assertThatCode(() -> observer.observe(LegacyType.CURATED, 7L)).doesNotThrowAnyException();
        verify(compareService).compare(LegacyType.CURATED, 7L);
    }
}
