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
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.ContentReadRouter;
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

    private final LegacyShadowReadObserver shadowReadObserver;
    private final ContentReadRouter contentReadRouter;
    private final CanonicalCuratedReadRepository canonicalReadRepository;

    public List<CategoryDto> getCategories() {
        return categoryRepository.findAllOrderByFairytaleCountDesc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    public HomePageDto getHomePage(String categoryKey) {
        return contentReadRouter.route(
            () -> getLegacyHomePage(categoryKey),
            () -> canonicalReadRepository.getHomePage(categoryKey)
        );
    }

    private HomePageDto getLegacyHomePage(String categoryKey) {
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
        return contentReadRouter.route(
            () -> getLegacyFairytaleDetail(fairytaleId),
            () -> canonicalReadRepository.getFairytaleDetail(fairytaleId)
        );
    }

    private FairytaleDetailDto getLegacyFairytaleDetail(Long fairytaleId) {
        FairytaleDetail detail = fairytaleDetailRepository.findActiveByFairytaleId(fairytaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detail not found"));
        FairytaleDetailDto response = FairytaleDetailDto.from(detail);
        observeShadow(fairytaleId);
        return response;
    }

    public CuratedSlidesResponse getCuratedSlides(Long fairytaleId) {
        return contentReadRouter.route(
            () -> getLegacyCuratedSlides(fairytaleId),
            () -> canonicalReadRepository.getCuratedSlides(fairytaleId)
        );
    }

    private CuratedSlidesResponse getLegacyCuratedSlides(Long fairytaleId) {
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
        CuratedSlidesResponse response = CuratedSlidesResponse.from(detail, pages);
        observeShadow(fairytaleId);
        return response;
    }

    public List<FairytaleDto> getFairytales(String categoryKey, String sort) {
        return contentReadRouter.route(
            () -> getLegacyFairytales(categoryKey, sort),
            () -> canonicalReadRepository.getFairytales(categoryKey, sort)
        );
    }

    private List<FairytaleDto> getLegacyFairytales(String categoryKey, String sort) {
        String key = (categoryKey != null && !categoryKey.isBlank()) ? categoryKey : null;
        return fairytaleRepository.findCurated(key, resolveSort(sort)).stream()
                .map(this::toDto)
                .toList();
    }

    private FairytaleDto toDto(Fairytale fairytale) {
        Long canonicalStoryId = legacyStoryLinkRepository
                .findPublishedStoryId(LegacyType.CURATED.name(), fairytale.getId())
                .orElse(null);
        return FairytaleDto.from(fairytale, canonicalStoryId);
    }

    private void observeShadow(Long fairytaleId) {
        if (fairytaleId == null) {
            return;
        }
        shadowReadObserver.observe(LegacyType.CURATED, fairytaleId);
    }

    private Sort resolveSort(String sort) {
        return switch (sort != null ? sort : "latest") {
            case "rating" -> Sort.by(Sort.Direction.DESC, "rating");
            case "title" -> Sort.by(Sort.Direction.ASC, "title");
            default -> Sort.by(Sort.Direction.DESC, "id");
        };
    }
}
