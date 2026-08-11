package org.example.csa_backend.fairytale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.fairytale.dto.HomePageDto;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentReadRouter;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CuratedReadRoutingTest {

    private final CategoryRepository categoryRepository = mock(CategoryRepository.class);
    private final FairytaleRepository fairytaleRepository = mock(FairytaleRepository.class);
    private final FairytaleDetailRepository detailRepository = mock(FairytaleDetailRepository.class);
    private final CuratedFairytalePageRepository pageRepository =
        mock(CuratedFairytalePageRepository.class);
    private final LegacyStoryLinkRepository linkRepository = mock(LegacyStoryLinkRepository.class);
    private final LegacyShadowReadObserver shadowObserver = mock(LegacyShadowReadObserver.class);
    private final ContentMigrationControlRepository controlRepository =
        mock(ContentMigrationControlRepository.class);
    private final CanonicalCuratedReadRepository canonicalRepository =
        mock(CanonicalCuratedReadRepository.class);
    private ContentMigrationControl control;
    private FairytaleService service;

    @BeforeEach
    void setUp() {
        control = control(MigrationState.OPEN, ContentSource.LEGACY, 40L);
        when(controlRepository.getSingleton()).thenReturn(control);
        service = new FairytaleService(
            categoryRepository,
            fairytaleRepository,
            detailRepository,
            pageRepository,
            linkRepository,
            shadowObserver,
            new ContentReadRouter(controlRepository),
            canonicalRepository
        );
    }

    @Test
    void canonicalSourceRoutesAllCuratedReadsWithoutLegacyContentTableReads() {
        ReflectionTestUtils.setField(control, "readSource", ContentSource.CANONICAL);
        FairytaleDto summary = new FairytaleDto(
            7L, "canonical", "正本", "description", "説明", 4.5, "#123456", "tag",
            List.of("forest"), true, 70L
        );
        HomePageDto home = new HomePageDto(List.of(summary), List.of(), List.of());
        FairytaleDetailDto detail = new FairytaleDetailDto(
            "author", "作者", "5-7", 4, 1, "body", "本文", true,
            "LOCAL_OVERLAY", "v1"
        );
        CuratedSlidesResponse slides = new CuratedSlidesResponse(
            7L, "v1", true, "LOCAL_OVERLAY", List.of()
        );
        when(canonicalRepository.getHomePage("forest")).thenReturn(home);
        when(canonicalRepository.getFairytales("forest", "rating")).thenReturn(List.of(summary));
        when(canonicalRepository.getFairytaleDetail(7L)).thenReturn(detail);
        when(canonicalRepository.getCuratedSlides(7L)).thenReturn(slides);

        assertThat(service.getHomePage("forest")).isSameAs(home);
        assertThat(service.getFairytales("forest", "rating")).containsExactly(summary);
        assertThat(service.getFairytaleDetail(7L)).isSameAs(detail);
        assertThat(service.getCuratedSlides(7L)).isSameAs(slides);

        verify(controlRepository, times(4)).getSingleton();
        verifyNoInteractions(
            fairytaleRepository, detailRepository, pageRepository, linkRepository, shadowObserver
        );
    }

    @Test
    void rollbackRoutesAllCuratedReadsToLegacyWithoutCanonicalReads() {
        ReflectionTestUtils.setField(control, "state", MigrationState.CUTOVER_PENDING);
        ReflectionTestUtils.setField(control, "readSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(control, "writeSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(control, "barrierEpoch", 41L);
        control.rollbackToLegacy(41L, Instant.parse("2026-08-12T00:00:00Z"));
        FairytaleDetail detail = detail(7L, "v1");
        CuratedFairytalePage page = new CuratedFairytalePage(
            detail.getFairytale(), 1, "legacy-image", "legacy-body", "legacy-body-ja", "v1",
            null, null, null, null, null, null, null
        );
        when(fairytaleRepository.findThemes(null)).thenReturn(List.of());
        when(fairytaleRepository.findNewItems(null)).thenReturn(List.of());
        when(fairytaleRepository.findRecommended(null)).thenReturn(List.of());
        when(fairytaleRepository.findCurated(any(), any())).thenReturn(List.of());
        when(detailRepository.findActiveByFairytaleId(7L)).thenReturn(Optional.of(detail));
        when(pageRepository.findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(7L, "v1"))
            .thenReturn(List.of(page));

        assertThat(service.getHomePage(null)).isEqualTo(new HomePageDto(List.of(), List.of(), List.of()));
        assertThat(service.getFairytales(null, "latest")).isEmpty();
        assertThat(service.getFairytaleDetail(7L).authorKo()).isEqualTo("legacy-author");
        assertThat(service.getCuratedSlides(7L).pages()).singleElement()
            .extracting(CuratedSlidesResponse.Page::imageUrl)
            .isEqualTo("legacy-image");

        verify(controlRepository, times(4)).getSingleton();
        verifyNoInteractions(canonicalRepository);
    }

    private FairytaleDetail detail(long id, String contentVersion) {
        Fairytale fairytale = new Fairytale(
            "legacy", "旧", "description", "説明", 3.0, "#FFFFFF", null, "N", "Y", "N"
        );
        ReflectionTestUtils.setField(fairytale, "id", id);
        ReflectionTestUtils.setField(fairytale, "characterSupported", false);
        FairytaleDetail detail = new FairytaleDetail(
            fairytale, "legacy-author", "旧作者", "3-5", 3, 1, "legacy-body", "旧本文"
        );
        ReflectionTestUtils.setField(detail, "contentVersion", contentVersion);
        return detail;
    }

    private ContentMigrationControl control(MigrationState state, ContentSource source, long epoch) {
        ContentMigrationControl value = new ContentMigrationControl();
        ReflectionTestUtils.setField(value, "singletonId", (short) 1);
        ReflectionTestUtils.setField(value, "state", state);
        ReflectionTestUtils.setField(value, "readSource", source);
        ReflectionTestUtils.setField(value, "writeSource", source);
        ReflectionTestUtils.setField(value, "barrierEpoch", epoch);
        ReflectionTestUtils.setField(value, "updatedAt", Instant.parse("2026-08-11T00:00:00Z"));
        return value;
    }
}
