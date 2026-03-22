package org.example.csa_backend.fairytale;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.fairytale.dto.CategoryDto;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.HomePageDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fairytale")
@RequiredArgsConstructor
public class FairytaleController {

    private final FairytaleService fairytaleService;

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryDto>> getCategories() {
        return ResponseEntity.ok(fairytaleService.getCategories());
    }

    @GetMapping("/home")
    public ResponseEntity<HomePageDto> getHomePage(
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(fairytaleService.getHomePage(category));
    }

    @GetMapping("/{id}/detail")
    public ResponseEntity<FairytaleDetailDto> getDetail(@PathVariable Long id) {
        return ResponseEntity.ok(fairytaleService.getFairytaleDetail(id));
    }
}
