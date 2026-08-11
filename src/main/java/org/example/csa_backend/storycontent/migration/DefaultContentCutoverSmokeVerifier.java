package org.example.csa_backend.storycontent.migration;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.example.csa_backend.storycontent.LegacyFairytaleReadAdapter;
import org.example.csa_backend.storycontent.ContentReadRouter;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.StoryRuntimeService;
import org.example.csa_backend.storycontent.StoryVisibility;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.example.csa_backend.fairytale.FairytaleService;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.springframework.stereotype.Component;

@Component
class DefaultContentCutoverSmokeVerifier implements ContentCutoverSmokeVerifier {

    private final Map<LegacyType, LegacyFairytaleReadAdapter> adapters =
        new EnumMap<>(LegacyType.class);
    private final LegacyStoryLinkRepository linkRepository;
    private final LegacyContractNormalizer normalizer;
    private final ContractChecksum checksum;
    private final StoryRuntimeService runtimeService;
    private final ContentReadRouter contentReadRouter;
    private final FairytaleService fairytaleService;
    private final AiFairytaleService aiFairytaleService;

    DefaultContentCutoverSmokeVerifier(
        List<LegacyFairytaleReadAdapter> adapters,
        LegacyStoryLinkRepository linkRepository,
        LegacyContractNormalizer normalizer,
        ContractChecksum checksum,
        StoryRuntimeService runtimeService,
        ContentReadRouter contentReadRouter,
        FairytaleService fairytaleService,
        AiFairytaleService aiFairytaleService
    ) {
        for (LegacyFairytaleReadAdapter adapter : adapters) {
            LegacyFairytaleReadAdapter previous = this.adapters.put(adapter.legacyType(), adapter);
            if (previous != null) {
                throw new IllegalArgumentException("Duplicate legacy adapter: " + adapter.legacyType());
            }
        }
        this.linkRepository = linkRepository;
        this.normalizer = normalizer;
        this.checksum = checksum;
        this.runtimeService = runtimeService;
        this.contentReadRouter = contentReadRouter;
        this.fairytaleService = fairytaleService;
        this.aiFairytaleService = aiFairytaleService;
    }

    @Override
    public SmokeResult verify(long epoch) {
        List<Fixture> fixtures = List.of(
            new Fixture(LegacyType.CURATED, StoryVisibility.PUBLISHED),
            new Fixture(LegacyType.AI, StoryVisibility.OWNER_PRIVATE),
            new Fixture(LegacyType.AI, StoryVisibility.SHARED)
        );
        java.util.ArrayList<String> checksumParts = new java.util.ArrayList<>();
        checksumParts.add("CONTENT_CUTOVER_SMOKE_V1");
        checksumParts.add(Long.toString(epoch));
        for (Fixture fixture : fixtures) {
            Long legacyId = linkRepository.findFirstLegacyIdForVisibility(
                    fixture.type(), fixture.visibility())
                .orElse(null);
            LegacyFairytaleReadAdapter adapter = adapters.get(fixture.type());
            if (legacyId == null || adapter == null) {
                return SmokeResult.failed("CUTOVER_SMOKE_FIXTURE_MISSING");
            }
            try {
                byte[] legacy = normalizer.canonicalBytes(
                    normalizer.normalize(adapter.readLegacy(legacyId))
                );
                Object routedCanonical = contentReadRouter.route(
                    () -> {
                        throw new IllegalStateException("CUTOVER_SMOKE_CANONICAL_SOURCE_REQUIRED");
                    },
                    () -> adapter.readCanonical(legacyId)
                );
                byte[] canonical = normalizer.canonicalBytes(
                    normalizer.normalize(routedCanonical)
                );
                if (!Arrays.equals(legacy, canonical)) {
                    return SmokeResult.failed("LEGACY_CANONICAL_MISMATCH");
                }
                byte[] routedPublicDto = normalizer.canonicalBytes(
                    normalizer.normalize(probePublicService(fixture, legacyId))
                );
                checksumParts.add(fixture.type().name());
                checksumParts.add(fixture.visibility().name());
                checksumParts.add(Long.toString(legacyId));
                checksumParts.add(checksum.ofBytes(canonical));
                checksumParts.add(checksum.ofBytes(routedPublicDto));
            } catch (RuntimeException exception) {
                return SmokeResult.failed("LEGACY_CANONICAL_MISMATCH");
            }
        }

        Long publicStoryId = linkRepository.findFirstPublishedStoryId(LegacyType.CURATED)
            .orElse(null);
        if (publicStoryId == null) {
            return SmokeResult.failed("CUTOVER_SMOKE_FIXTURE_MISSING");
        }
        try {
            StoryRuntimeManifestResponse runtime = runtimeService.getPublishedRuntime(
                publicStoryId,
                new RuntimeCapabilities("SLIDE", "ko", null, List.of(1), List.of("SLIDE"), 1)
            );
            if (runtime == null || runtime.manifestChecksum() == null) {
                return SmokeResult.failed("PUBLIC_RUNTIME_MISMATCH");
            }
            checksumParts.add("PUBLIC_RUNTIME");
            checksumParts.add(Long.toString(publicStoryId));
            checksumParts.add(runtime.manifestChecksum());
        } catch (RuntimeException exception) {
            return SmokeResult.failed("PUBLIC_RUNTIME_MISMATCH");
        }
        return SmokeResult.passed(checksum.ofParts(checksumParts));
    }

    private Object probePublicService(Fixture fixture, long legacyId) {
        if (fixture.type() == LegacyType.CURATED) {
            return List.of(
                fairytaleService.getFairytaleDetail(legacyId),
                fairytaleService.getCuratedSlides(legacyId)
            );
        }
        if (fixture.visibility() == StoryVisibility.OWNER_PRIVATE) {
            Long ownerUserId = linkRepository.findOwnerUserId(fixture.type(), legacyId)
                .orElseThrow(() -> new IllegalStateException("CUTOVER_SMOKE_FIXTURE_OWNER_MISSING"));
            return aiFairytaleService.getMyFairytaleSlides(ownerUserId, legacyId);
        }
        return aiFairytaleService.getSharedFairytaleSlides(legacyId);
    }

    private record Fixture(LegacyType type, StoryVisibility visibility) {
    }
}
