package org.example.csa_backend.fairytale;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.fairytale.dto.CategoryDto;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.fairytale.dto.HomePageDto;
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

    public List<CategoryDto> getCategories() {
        return categoryRepository.findAllOrderByFairytaleCountDesc().stream()
                .map(CategoryDto::from)
                .toList();
    }

    public HomePageDto getHomePage(String categoryKey) {
        String key = (categoryKey != null && !categoryKey.isBlank()) ? categoryKey : null;

        List<FairytaleDto> themes = fairytaleRepository.findThemes(key).stream()
                .map(FairytaleDto::from)
                .toList();

        List<FairytaleDto> newItems = fairytaleRepository.findNewItems(key).stream()
                .map(FairytaleDto::from)
                .toList();

        List<FairytaleDto> recommended = fairytaleRepository.findRecommended(key).stream()
                .map(FairytaleDto::from)
                .toList();

        return new HomePageDto(themes, newItems, recommended);
    }

    public FairytaleDetailDto getFairytaleDetail(Long fairytaleId) {
        FairytaleDetail detail = fairytaleDetailRepository.findByFairytaleId(fairytaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Detail not found"));
        return FairytaleDetailDto.from(detail);
    }
}
