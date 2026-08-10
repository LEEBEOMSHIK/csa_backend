package org.example.csa_backend.fairytale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.example.csa_backend.fairytale.service.AiImageService;
import org.example.csa_backend.fairytale.service.AiTextService;
import org.example.csa_backend.fairytale.service.AiTtsService;
import org.example.csa_backend.fairytale.service.AiVideoAssemblyService;
import org.example.csa_backend.fairytale.service.FileStorageService;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyFairytaleContractFixtureTest {

    private static final Long OWNER_ID = 42L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AiFairytaleRepository aiFairytaleRepository = mock(AiFairytaleRepository.class);
    private final AiFairytaleService aiFairytaleService = new AiFairytaleService(
            aiFairytaleRepository,
            mock(AiFairytalePageRepository.class),
            unavailableProvider(),
            unavailableProvider(),
            unavailableProvider(),
            mock(FileStorageService.class),
            mock(AiVideoAssemblyService.class),
            mock(UserRepository.class),
            new AiGenerationProperties()
    );
    private final FairytaleDetailRepository fairytaleDetailRepository = mock(FairytaleDetailRepository.class);
    private final CuratedFairytalePageRepository curatedFairytalePageRepository = mock(CuratedFairytalePageRepository.class);
    private final FairytaleService fairytaleService = new FairytaleService(
            mock(CategoryRepository.class),
            mock(FairytaleRepository.class),
            fairytaleDetailRepository,
            curatedFairytalePageRepository,
            mock(org.example.csa_backend.storycontent.LegacyStoryLinkRepository.class)
    );

    @ParameterizedTest
    @MethodSource("legacyCases")
    void legacyAiContractSerializesActualServiceResult(LegacyCase legacyCase) throws Exception {
        AiFairytale entity = LegacyEntityFactory.create(legacyCase, OWNER_ID);
        when(aiFairytaleRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(aiFairytaleRepository.findByOwnerIdOrderByIdDesc(OWNER_ID)).thenReturn(List.of(entity));

        MyFairytaleDto my = aiFairytaleService.getMyFairytales(OWNER_ID).get(0);
        assertThat(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(my)))
                .isEqualTo(legacyCase.expectedMyDto());

        if (legacyCase.ownerSlides().equals("OK")) {
            FairytaleGenerateResponse slides = aiFairytaleService.getMyFairytaleSlides(OWNER_ID, entity.getId());
            assertThat(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(slides)))
                    .isEqualTo(legacyCase.expectedSlides());
        } else {
            assertApiFailure(() -> aiFairytaleService.getMyFairytaleSlides(OWNER_ID, entity.getId()),
                    409, "INVALID_STATE");
        }
    }

    @Test
    void fixtureContainsEveryLegacyProjectionBranch() throws IOException {
        assertThat(loadLegacyCases()).extracting(LegacyCase::status)
                .containsExactly("COMPLETED", "COMPLETED", "FAILED", "FAILED", "PENDING", "GENERATING");
    }

    @Test
    void curatedSlidesSerializeActualServiceResultAsGoldenFixture() throws Exception {
        CuratedFixture fixture = readResource("contracts/legacy-curated-slides.json", CuratedFixture.class);
        FairytaleDetail detail = curatedDetail(fixture.input());
        List<CuratedFairytalePage> pages = fixture.input().pages().stream()
                .map(page -> curatedPage(detail.getFairytale(), fixture.input().contentVersion(), page))
                .toList();
        when(fairytaleDetailRepository.findActiveByFairytaleId(fixture.input().fairytaleId()))
                .thenReturn(Optional.of(detail));
        when(curatedFairytalePageRepository.findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(
                fixture.input().fairytaleId(), fixture.input().contentVersion())).thenReturn(pages);

        CuratedSlidesResponse response = fairytaleService.getCuratedSlides(fixture.input().fairytaleId());

        assertThat(OBJECT_MAPPER.readTree(OBJECT_MAPPER.writeValueAsBytes(response)))
                .isEqualTo(fixture.expected());
    }

    private static Stream<LegacyCase> legacyCases() throws IOException {
        return loadLegacyCases().stream();
    }

    private static List<LegacyCase> loadLegacyCases() throws IOException {
        return List.of(readResource("contracts/legacy-ai-cases.json", LegacyCase[].class));
    }

    private static <T> T readResource(String resource, Class<T> type) throws IOException {
        try (InputStream input = LegacyFairytaleContractFixtureTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing test resource: " + resource);
            }
            return OBJECT_MAPPER.readValue(input, type);
        }
    }

    private FairytaleDetail curatedDetail(CuratedInput input) {
        Fairytale fairytale = new Fairytale(
                "큐레이션 동화", "キュレーション童話", "설명", "説明", 5.0,
                "#FFFFFF", null, "N", "Y", "N"
        );
        ReflectionTestUtils.setField(fairytale, "id", input.fairytaleId());
        ReflectionTestUtils.setField(fairytale, "characterSupported", input.characterSupported());
        FairytaleDetail detail = new FairytaleDetail(
                fairytale, "작가", "作者", "3-5", 5, input.pages().size(), "본문", "本文"
        );
        ReflectionTestUtils.setField(detail, "contentVersion", input.contentVersion());
        return detail;
    }

    private CuratedFairytalePage curatedPage(Fairytale fairytale, String contentVersion, CuratedPage page) {
        CuratedFairytalePage result = new CuratedFairytalePage(
                fairytale, page.pageIndex(), page.imageUrl(), page.textKo(), page.textJa(), contentVersion,
                page.placementX(), page.placementY(), page.placementWidth(), page.placementHeight(),
                page.placementZIndex(), page.placementPose(), page.placementFlipX()
        );
        page.audios().forEach(audio -> result.getAudios().add(new CuratedFairytaleAudio(
                result, audio.voiceType(), audio.locale(), audio.audioUrl()
        )));
        return result;
    }

    private void assertApiFailure(ThrowingCall call, int status, String errorCode) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException businessException = (BusinessException) error;
                    assertThat(businessException.getErrorCode().getStatus().value()).isEqualTo(status);
                    assertThat(businessException.getErrorCode().name()).isEqualTo(errorCode);
                });
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> unavailableProvider() {
        return (ObjectProvider<T>) mock(ObjectProvider.class);
    }

    private record LegacyCase(Long id, String title, String settings, String genre, String theme, int chapterCount,
                              String voiceType, String language, String format, String status, boolean shared,
                              String videoUrl, int pageCount, String ownerSlides, List<LegacyPage> pages,
                              JsonNode expectedMyDto, JsonNode expectedSlides) {
    }

    private record LegacyPage(int pageIndex, String text, String imageUrl, String audioUrl) {
    }

    private record CuratedFixture(CuratedInput input, JsonNode expected) {
    }

    private record CuratedInput(Long fairytaleId, String contentVersion, boolean characterSupported,
                                List<CuratedPage> pages) {
    }

    private record CuratedPage(int pageIndex, String imageUrl, String textKo, String textJa,
                               Double placementX, Double placementY, Double placementWidth, Double placementHeight,
                               Integer placementZIndex, String placementPose, Boolean placementFlipX,
                               List<CuratedAudio> audios) {
    }

    private record CuratedAudio(String voiceType, String locale, String audioUrl) {
    }

    private static final class LegacyEntityFactory {

        private static AiFairytale create(LegacyCase legacyCase, Long ownerId) {
            if (legacyCase.pages().size() != legacyCase.pageCount()) {
                throw new IllegalArgumentException("pageCount must match fixture pages");
            }
            AiFairytale fairytale = new AiFairytale(
                    legacyCase.title(), legacyCase.settings(), legacyCase.genre(), legacyCase.theme(),
                    legacyCase.chapterCount(), legacyCase.voiceType(), legacyCase.language(),
                    legacyCase.format(), legacyCase.status()
            );
            ReflectionTestUtils.setField(fairytale, "id", legacyCase.id());
            fairytale.updateShared(legacyCase.shared());
            fairytale.updateVideoUrl(legacyCase.videoUrl());
            User owner = new User("legacy-owner@example.com", "password");
            ReflectionTestUtils.setField(owner, "id", ownerId);
            fairytale.assignOwner(owner);
            legacyCase.pages().forEach(page -> fairytale.getPages().add(new AiFairytalePage(
                    fairytale, page.pageIndex(), page.text(), page.imageUrl(), page.audioUrl()
            )));
            return fairytale;
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run();
    }
}
