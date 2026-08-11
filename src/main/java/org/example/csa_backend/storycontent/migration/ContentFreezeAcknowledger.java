package org.example.csa_backend.storycontent.migration;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.MigrationState;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("!content-migration")
@RequiredArgsConstructor
public class ContentFreezeAcknowledger {

    private final ContentMigrationControlRepository controlRepository;
    private final ContentWriteActivityTracker tracker;
    private final ContentServiceIdentity identity;

    @Scheduled(fixedDelayString = "${content.migration.freeze-poll-ms:1000}")
    @Transactional
    public void poll() {
        tracker.whenQuiescent(() -> {
            ContentMigrationControl control = controlRepository.getSingletonForUpdate();
            if (control.getState() == MigrationState.FREEZE_REQUESTED) {
                control.acknowledge(identity.value(), control.getBarrierEpoch());
            }
        });
    }
}
