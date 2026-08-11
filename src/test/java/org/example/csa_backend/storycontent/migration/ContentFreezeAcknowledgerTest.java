package org.example.csa_backend.storycontent.migration;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.MigrationState;
import org.junit.jupiter.api.Test;

class ContentFreezeAcknowledgerTest {

    @Test
    void liveBackendAcknowledgesItsFixedIdentityOnlyInsideQuiescentSection() {
        ContentMigrationControlRepository controlRepository = mock(ContentMigrationControlRepository.class);
        ContentWriteActivityTracker tracker = mock(ContentWriteActivityTracker.class);
        ContentServiceIdentity identity = mock(ContentServiceIdentity.class);
        ContentMigrationControl control = mock(ContentMigrationControl.class);
        when(controlRepository.getSingletonForUpdate()).thenReturn(control);
        when(control.getState()).thenReturn(MigrationState.FREEZE_REQUESTED);
        when(control.getBarrierEpoch()).thenReturn(31L);
        when(identity.value()).thenReturn("csa_backend");
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(tracker).whenQuiescent(org.mockito.ArgumentMatchers.any());
        ContentFreezeAcknowledger acknowledger = new ContentFreezeAcknowledger(controlRepository, tracker, identity);

        acknowledger.poll();

        verify(control).acknowledge("csa_backend", 31L);
    }
}
