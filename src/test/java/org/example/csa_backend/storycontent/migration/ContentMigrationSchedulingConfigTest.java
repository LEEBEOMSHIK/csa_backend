package org.example.csa_backend.storycontent.migration;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;

@SpringJUnitConfig(classes = {ContentMigrationSchedulingConfig.class, ContentFreezeAcknowledger.class})
@ActiveProfiles("test")
@TestPropertySource(properties = "content.migration.freeze-poll-ms=50")
class ContentMigrationSchedulingConfigTest {

    @MockitoBean
    private ContentMigrationControlRepository controlRepository;

    @MockitoBean
    private ContentWriteActivityTracker tracker;

    @MockitoBean
    private ContentServiceIdentity identity;

    @MockitoSpyBean
    private ContentFreezeAcknowledger acknowledger;

    @BeforeEach
    void stubOpenControl() {
        ContentMigrationControl control = mock(ContentMigrationControl.class);
        when(control.getState()).thenReturn(MigrationState.OPEN);
        when(controlRepository.getSingletonForUpdate()).thenReturn(control);
        doAnswer(invocation -> {
            invocation.getArgument(0, Runnable.class).run();
            return null;
        }).when(tracker).whenQuiescent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void schedulerActuallyInvokesFreezePoll() {
        verify(acknowledger, timeout(2000).atLeastOnce()).poll();
    }
}
