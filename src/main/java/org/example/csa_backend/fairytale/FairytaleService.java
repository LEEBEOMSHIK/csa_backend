package org.example.csa_backend.fairytale;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.fairytale.dto.CategoryDto;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.fairytale.dto.HomePageDto;
import org.example.csa_backend.fairytale.dto.CuratedSlidesResponse;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FairytaleService {

    private final CategoryRepository categoryRepository;
    private final FairytaleRepository fairytaleRepository;
    private final FairytaleDetailRepository fairytaleDetailRepository;
    private final CuratedFairytalePageRepository curatedFairytalePageRepository;
    private final LegacyStoryLinkRepository legacyStoryLinkRepository;

    public List<CategoryDto> getCategories() {
        return categoryRepository.findAllOrderByFairytaleCountDesc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    public HomePageDto getHomePage(String categoryKey) {
        String key = (categoryKey != null && !categoryKey.isBlank()) ? categoryKey : null;

        List<FairytaleDto> themes = fairytaleRepository.findThemes(key).stream()
                .map(this::toDto)
                .toList();

        List<FairytaleDto> newItems = fairytaleRepository.findNewItems(key).stream()
                .map(this::toDto)
                .toList();

        List<FairytaleDto> recommended = fairytaleRepository.findRecommended(key).stream()
                .map(this::toDto)
                .toList();

        return new HomePageDto(themes, newItems, recommended);
    }

    public FairytaleDetailDto getFairytaleDetail(Long fairytaleId) {
        FairytaleDetail detail = fairytaleDetailRepository.findActiveByFairytaleId(fairytaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detail not found"));
        return FairytaleDetailDto.from(detail);
    }

    public CuratedSlidesResponse getCuratedSlides(Long fairytaleId) {
        FairytaleDetail detail = fairytaleDetailRepository.findActiveByFairytaleId(fairytaleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        String contentVersion = detail.getContentVersion();
        if (contentVersion == null || contentVersion.isBlank()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        var pages = curatedFairytalePageRepository
                .findActiveByFairytaleIdAndContentVersionOrderByPageIndexAsc(fairytaleId, contentVersion);
        if (pages.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        return CuratedSlidesResponse.from(detail, pages);
    }

    public List<FairytaleDto> getFairytales(String categoryKey, String sort) {
        String key = (categoryKey != null && !categoryKey.isBlank()) ? categoryKey : null;
        return fairytaleRepository.findCurated(key, resolveSort(sort)).stream()
                .map(this::toDto)
                .toList();
    }

    private FairytaleDto toDto(Fairytale fairytale) {
        Long canonicalStoryId = legacyStoryLinkRepository
                .findByLegacyTypeAndLegacyId(LegacyType.CURATED, fairytale.getId())
                .map(link -> link.getStoryId())
                .orElse(null);
        return FairytaleDto.from(fairytale, canonicalStoryId);
    }

    private Sort resolveSort(String sort) {
        return switch (sort != null ? sort : "latest") {
            case "rating" -> Sort.by(Sort.Direction.DESC, "rating");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }
}
