package org.example.csa_backend.storycontent;

import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.storycontent.migration.LegacyShadowCompareService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LegacyShadowReadObserver {

    private final LegacyShadowCompareService compareService;
    private final boolean enabled;

    public LegacyShadowReadObserver(
        LegacyShadowCompareService compareService,
        @Value("${csa.migration.shadow-read-enabled:false}") boolean enabled
    ) {
        this.compareService = compareService;
        this.enabled = enabled;
    }

    public void observe(LegacyType type, long legacyId) {
        if (!enabled) {
            return;
        }
        try {
            compareService.compare(type, legacyId);
        } catch (RuntimeException exception) {
            log.warn("Legacy shadow compare failed open: type={}, legacyId={}", type, legacyId, exception);
        }
    }
}
