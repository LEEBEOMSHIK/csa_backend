package org.example.csa_backend.fairytale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.storycontent.LegacyStoryLink;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FairytaleCanonicalStoryLinkTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private FairytaleRepository fairytaleRepository;

    @Mock
    private FairytaleDetailRepository fairytaleDetailRepository;

    @Mock
    private CuratedFairytalePageRepository curatedFairytalePageRepository;

    @Mock
    private LegacyStoryLinkRepository legacyStoryLinkRepository;

    private FairytaleService service;

    @BeforeEach
    void setUp() {
        service = new FairytaleService(
            categoryRepository,
            fairytaleRepository,
            fairytaleDetailRepository,
            curatedFairytalePageRepository,
            legacyStoryLinkRepository
        );
    }

    @Test
    void curatedDtoKeepsLegacyIdAndAddsCanonicalStoryIdWhenImported() {
        Fairytale fairytale = fairytale(12L);
        LegacyStoryLink link = new LegacyStoryLink();
        ReflectionTestUtils.setField(link, "legacyType", LegacyType.CURATED);
        ReflectionTestUtils.setField(link, "legacyId", 12L);
        ReflectionTestUtils.setField(link, "storyId", 77L);
        when(fairytaleRepository.findCurated(isNull(), any(Sort.class))).thenReturn(List.of(fairytale));
        when(legacyStoryLinkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 12L))
            .thenReturn(Optional.of(link));

        FairytaleDto dto = service.getFairytales(null, "latest").get(0);

        assertThat(dto.id()).isEqualTo(12L);
        assertThat(dto.canonicalStoryId()).isEqualTo(77L);
    }

    @Test
    void unimportedCuratedDtoKeepsLegacyIdAndNullCanonicalStoryId() {
        Fairytale fairytale = fairytale(12L);
        when(fairytaleRepository.findCurated(isNull(), any(Sort.class))).thenReturn(List.of(fairytale));
        when(legacyStoryLinkRepository.findByLegacyTypeAndLegacyId(LegacyType.CURATED, 12L))
            .thenReturn(Optional.empty());

        FairytaleDto dto = service.getFairytales(null, "latest").get(0);

        assertThat(dto.id()).isEqualTo(12L);
        assertThat(dto.canonicalStoryId()).isNull();
    }

    private Fairytale fairytale(Long id) {
        Fairytale fairytale = new Fairytale(
            "숲 이야기", "森の物語", "설명", "説明", 4.8, "#FFFFFF", "nature", "N", "Y", "Y"
        );
        ReflectionTestUtils.setField(fairytale, "id", id);
        return fairytale;
    }
}
