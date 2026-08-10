package org.example.csa_backend.fairytale;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FairytaleServiceTest {

    private final FairytaleDetailRepository fairytaleDetailRepository = mock(FairytaleDetailRepository.class);
    private final CuratedFairytalePageRepository curatedFairytalePageRepository = mock(CuratedFairytalePageRepository.class);
    private final FairytaleService service = new FairytaleService(
            mock(CategoryRepository.class),
            mock(FairytaleRepository.class),
            fairytaleDetailRepository,
            curatedFairytalePageRepository,
            mock(org.example.csa_backend.storycontent.LegacyStoryLinkRepository.class)
    );

    @Test
    void curatedSlidesReturnsLocalizedMediaAndCharacterPlacement() {
        FairytaleDetail detail = detail(12L, true, "2026-07-20.1");
        CuratedFairytalePage page = new CuratedFairytalePage(
                detail.getFairytale(), 1, "https://cdn.example/curated/12/2026-07-20.1/page-1.png",
                "한국어 본문", "日本語の本文", "2026-07-20.1", 0.18, 0.42, 0.24, 0.36, 2, "standing", false);
        page.getAudios().add(new CuratedFairytaleAudio(
                page, "dad", "ko", "https://cdn.example/curated/12/2026-07-20.1/page-1-dad-ko.mp3"));
        page.getAudios().add(new CuratedFairytaleAudio(
                page, "dad", "ja", "https://cdn.example/curated/12/2026-07-20.1/page-1-dad-ja.mp3"));
        when(fairytaleDetailRepository.findActiveByFairytaleId(12L)).thenReturn(Optional.of(detail));
        when(curatedFairytalePageRepository.findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(12L, "2026-07-20.1"))
                .thenReturn(List.of(page));

        CuratedSlidesResponse response = service.getCuratedSlides(12L);

        assertThat(response.fairytaleId()).isEqualTo(12L);
        assertThat(response.contentVersion()).isEqualTo("2026-07-20.1");
        assertThat(response.characterSupported()).isTrue();
        assertThat(response.characterRenderMode()).isEqualTo("LOCAL_OVERLAY");
        assertThat(response.pages()).singleElement().satisfies(result -> {
            assertThat(result.pageIndex()).isEqualTo(1);
            assertThat(result.text().ko()).isEqualTo("한국어 본문");
            assertThat(result.text().ja()).isEqualTo("日本語の本文");
            assertThat(result.audioUrls()).containsEntry("dad", java.util.Map.of(
                    "ko", "https://cdn.example/curated/12/2026-07-20.1/page-1-dad-ko.mp3",
                    "ja", "https://cdn.example/curated/12/2026-07-20.1/page-1-dad-ja.mp3"));
            assertThat(result.characterPlacement()).isNotNull();
            assertThat(result.characterPlacement().x()).isEqualTo(0.18);
            assertThat(result.characterPlacement().pose()).isEqualTo("standing");
        });
    }

    @Test
    void curatedSlidesSuppressesPlacementForUnsupportedFairytale() {
        FairytaleDetail detail = detail(12L, false, "2026-07-20.1");
        CuratedFairytalePage page = new CuratedFairytalePage(
                detail.getFairytale(), 1, "https://cdn.example/page-1.png", "본문", "本文",
                "2026-07-20.1", 0.18, 0.42, 0.24, 0.36, 2, "standing", false);
        when(fairytaleDetailRepository.findActiveByFairytaleId(12L)).thenReturn(Optional.of(detail));
        when(curatedFairytalePageRepository.findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(12L, "2026-07-20.1"))
                .thenReturn(List.of(page));

        CuratedSlidesResponse response = service.getCuratedSlides(12L);

        assertThat(response.characterSupported()).isFalse();
        assertThat(response.pages()).singleElement()
                .extracting(CuratedSlidesResponse.Page::characterPlacement)
                .isNull();
    }

    @Test
    void curatedSlidesReturnsNotFoundUntilPublishedVersionAndPagesExistTogether() {
        FairytaleDetail detail = detail(12L, true, null);
        when(fairytaleDetailRepository.findActiveByFairytaleId(12L)).thenReturn(Optional.of(detail));
        assertThatThrownBy(() -> service.getCuratedSlides(12L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void curatedSlidesReturnsNotFoundWhenPagesDoNotMatchPublishedContentVersion() {
        FairytaleDetail detail = detail(12L, true, "2026-07-20.2");
        when(fairytaleDetailRepository.findActiveByFairytaleId(12L)).thenReturn(Optional.of(detail));
        when(curatedFairytalePageRepository.findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(
                12L, "2026-07-20.2")).thenReturn(List.of());

        assertThatThrownBy(() -> service.getCuratedSlides(12L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void curatedSlidesReturnsNotFoundForMissingCuratedFairytale() {
        when(fairytaleDetailRepository.findActiveByFairytaleId(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCuratedSlides(404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    private FairytaleDetail detail(Long id, boolean characterSupported, String contentVersion) {
        Fairytale fairytale = new Fairytale(
                "큐레이션 동화", "キュレーション童話", "설명", "説明", 5.0,
                "#FFFFFF", null, "N", "Y", "N");
        ReflectionTestUtils.setField(fairytale, "id", id);
        ReflectionTestUtils.setField(fairytale, "characterSupported", characterSupported);
        FairytaleDetail detail = new FairytaleDetail(
                fairytale, "작가", "作者", "3-5", 5, 1, "본문", "本文");
        ReflectionTestUtils.setField(detail, "contentVersion", contentVersion);
        return detail;
    }
}
