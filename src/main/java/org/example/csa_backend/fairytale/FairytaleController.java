package org.example.csa_backend.fairytale;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.fairytale.dto.CategoryDto;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateRequest;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.HomePageDto;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
import org.example.csa_backend.fairytale.dto.ShareResponse;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/fairytale")
@RequiredArgsConstructor
public class FairytaleController {

    private final FairytaleService fairytaleService;
    private final AiFairytaleService aiFairytaleService;

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

    @PostMapping("/generate")
    public ResponseEntity<FairytaleGenerateResponse> generate(
            @RequestBody FairytaleGenerateRequest request, Authentication authentication) {
        return ResponseEntity.ok(aiFairytaleService.generate(request, currentUserId(authentication)));
    }

    @GetMapping("/my")
    public ResponseEntity<List<MyFairytaleDto>> getMyFairytales(Authentication authentication) {
        return ResponseEntity.ok(aiFairytaleService.getMyFairytales(requireUserId(authentication)));
    }

    @GetMapping("/shared")
    public ResponseEntity<List<MyFairytaleDto>> getSharedFairytales() {
        return ResponseEntity.ok(aiFairytaleService.getSharedFairytales());
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ShareResponse> toggleShare(
            @PathVariable Long id, Authentication authentication) {
        boolean shared = aiFairytaleService.toggleShare(requireUserId(authentication), id);
        return ResponseEntity.ok(new ShareResponse(shared));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        aiFairytaleService.deleteMyFairytale(requireUserId(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private Long requireUserId(Authentication authentication) {
        Long userId = currentUserId(authentication);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return userId;
    }

    private Long currentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String name = authentication.getName();
        if (name == null || "anonymousUser".equals(name)) {
            return null;
        }
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
