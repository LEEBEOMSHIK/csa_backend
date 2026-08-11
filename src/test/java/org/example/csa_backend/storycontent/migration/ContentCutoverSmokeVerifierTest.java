package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.example.csa_backend.storycontent.LegacyAiReadAdapter;
import org.example.csa_backend.storycontent.LegacyCuratedReadAdapter;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentReadRouter;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.StoryRuntimeException;
import org.example.csa_backend.storycontent.StoryRuntimeService;
import org.example.csa_backend.storycontent.StoryVisibility;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.example.csa_backend.fairytale.FairytaleService;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContentCutoverSmokeVerifierTest {

    private LegacyAiReadAdapter ai;
    private LegacyCuratedReadAdapter curated;
    private LegacyStoryLinkRepository links;
    private StoryRuntimeService runtime;
    private FairytaleService fairytaleService;
    private AiFairytaleService aiFairytaleService;
    private ContentMigrationControl control;
    private DefaultContentCutoverSmokeVerifier verifier;

    @BeforeEach
    void setUp() {
        ai = mock(LegacyAiReadAdapter.class);
        curated = mock(LegacyCuratedReadAdapter.class);
        when(ai.legacyType()).thenReturn(LegacyType.AI);
        when(curated.legacyType()).thenReturn(LegacyType.CURATED);
        links = mock(LegacyStoryLinkRepository.class);
        runtime = mock(StoryRuntimeService.class);
        fairytaleService = mock(FairytaleService.class);
        aiFairytaleService = mock(AiFairytaleService.class);
        ContentMigrationControlRepository controls = mock(ContentMigrationControlRepository.class);
        control = mock(ContentMigrationControl.class);
        when(controls.getSingleton()).thenReturn(control);
        when(control.getReadSource()).thenReturn(ContentSource.CANONICAL);
        verifier = new DefaultContentCutoverSmokeVerifier(
            List.of(curated, ai),
            links,
            new LegacyContractNormalizer(),
            new ContractChecksum(),
            runtime,
            new ContentReadRouter(controls),
            fairytaleService,
            aiFairytaleService
        );
    }

    @Test
    void probesCuratedPublicAiPrivateAiSharedAndPublicRuntime() {
        stubFixture(curated, LegacyType.CURATED, StoryVisibility.PUBLISHED, 11L, "curated");
        stubFixture(ai, LegacyType.AI, StoryVisibility.OWNER_PRIVATE, 12L, "private");
        stubFixture(ai, LegacyType.AI, StoryVisibility.SHARED, 13L, "shared");
        when(links.findFirstPublishedStoryId(LegacyType.CURATED)).thenReturn(Optional.of(101L));
        StoryRuntimeManifestResponse response = mock(StoryRuntimeManifestResponse.class);
        when(response.manifestChecksum()).thenReturn("a".repeat(64));
        when(runtime.getPublishedRuntime(any(Long.class), any(RuntimeCapabilities.class)))
            .thenReturn(response);

        SmokeResult result = verifier.verify(41L);

        assertThat(result.passed()).isTrue();
        assertThat(result.checksum()).hasSize(64);
        verify(curated).readLegacy(11L);
        verify(curated).readCanonical(11L);
        verify(ai).readLegacy(12L);
        verify(ai).readCanonical(12L);
        verify(ai).readLegacy(13L);
        verify(ai).readCanonical(13L);
        verify(fairytaleService).getFairytaleDetail(11L);
        verify(fairytaleService).getCuratedSlides(11L);
        verify(aiFairytaleService).getMyFairytaleSlides(42L, 12L);
        verify(aiFairytaleService).getSharedFairytaleSlides(13L);
        verify(runtime).getPublishedRuntime(any(Long.class), any(RuntimeCapabilities.class));
    }

    @Test
    void adapterMismatchFailsClosedBeforeRuntimeProbe() {
        when(links.findFirstLegacyIdForVisibility(LegacyType.CURATED, StoryVisibility.PUBLISHED))
            .thenReturn(Optional.of(11L));
        when(curated.readLegacy(11L)).thenReturn(Map.of("title", "legacy"));
        when(curated.readCanonical(11L)).thenReturn(Map.of("title", "canonical"));

        SmokeResult result = verifier.verify(41L);

        assertThat(result).isEqualTo(SmokeResult.failed("LEGACY_CANONICAL_MISMATCH"));
    }

    @Test
    void legacyReadSourceFailsClosedInsteadOfComparingLegacyToItself() {
        when(control.getReadSource()).thenReturn(ContentSource.LEGACY);
        when(links.findFirstLegacyIdForVisibility(LegacyType.CURATED, StoryVisibility.PUBLISHED))
            .thenReturn(Optional.of(11L));
        when(curated.readLegacy(11L)).thenReturn(Map.of("title", "legacy"));

        SmokeResult result = verifier.verify(41L);

        assertThat(result).isEqualTo(SmokeResult.failed("LEGACY_CANONICAL_MISMATCH"));
        verify(curated, never()).readCanonical(11L);
    }

    @Test
    void routedPublicServiceProbeFailureFailsClosed() {
        stubFixture(curated, LegacyType.CURATED, StoryVisibility.PUBLISHED, 11L, "curated");
        when(fairytaleService.getFairytaleDetail(11L))
            .thenThrow(new IllegalStateException("canonical DTO unavailable"));

        SmokeResult result = verifier.verify(41L);

        assertThat(result).isEqualTo(SmokeResult.failed("LEGACY_CANONICAL_MISMATCH"));
    }

    @Test
    void publicRuntimeFailureMapsToStableSmokeCode() {
        stubFixture(curated, LegacyType.CURATED, StoryVisibility.PUBLISHED, 11L, "curated");
        stubFixture(ai, LegacyType.AI, StoryVisibility.OWNER_PRIVATE, 12L, "private");
        stubFixture(ai, LegacyType.AI, StoryVisibility.SHARED, 13L, "shared");
        when(links.findFirstPublishedStoryId(LegacyType.CURATED)).thenReturn(Optional.of(101L));
        when(runtime.getPublishedRuntime(any(Long.class), any(RuntimeCapabilities.class)))
            .thenThrow(StoryRuntimeException.notFound("STORY_NOT_FOUND"));

        SmokeResult result = verifier.verify(41L);

        assertThat(result).isEqualTo(SmokeResult.failed("PUBLIC_RUNTIME_MISMATCH"));
    }

    private void stubFixture(
        Object adapter,
        LegacyType type,
        StoryVisibility visibility,
        long legacyId,
        String value
    ) {
        when(links.findFirstLegacyIdForVisibility(type, visibility)).thenReturn(Optional.of(legacyId));
        if (adapter instanceof LegacyCuratedReadAdapter curatedAdapter) {
            when(curatedAdapter.readLegacy(legacyId)).thenReturn(Map.of("value", value));
            when(curatedAdapter.readCanonical(legacyId)).thenReturn(Map.of("value", value));
            when(fairytaleService.getFairytaleDetail(legacyId)).thenReturn(new FairytaleDetailDto(
                "author", "作者", "5-7", 4, 1, "body", "本文", true,
                "LOCAL_OVERLAY", "v1"
            ));
            when(fairytaleService.getCuratedSlides(legacyId)).thenReturn(new CuratedSlidesResponse(
                legacyId, "v1", true, "LOCAL_OVERLAY", List.of()
            ));
        } else {
            LegacyAiReadAdapter aiAdapter = (LegacyAiReadAdapter) adapter;
            when(aiAdapter.readLegacy(legacyId)).thenReturn(Map.of("value", value));
            when(aiAdapter.readCanonical(legacyId)).thenReturn(Map.of("value", value));
            FairytaleGenerateResponse response = new FairytaleGenerateResponse(
                legacyId, value, "ko", "dad", List.of(), null
            );
            if (visibility == StoryVisibility.OWNER_PRIVATE) {
                when(links.findOwnerUserId(type, legacyId)).thenReturn(Optional.of(42L));
                when(aiFairytaleService.getMyFairytaleSlides(42L, legacyId)).thenReturn(response);
            } else {
                when(aiFairytaleService.getSharedFairytaleSlides(legacyId)).thenReturn(response);
            }
        }
    }
}
