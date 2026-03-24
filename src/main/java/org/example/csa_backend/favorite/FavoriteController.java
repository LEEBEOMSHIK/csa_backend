package org.example.csa_backend.favorite;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @GetMapping
    public ResponseEntity<List<FairytaleDto>> getFavorites(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(favoriteService.getFavorites(userId));
    }

    @PostMapping("/{fairytaleId}")
    public ResponseEntity<Void> addFavorite(@PathVariable Long fairytaleId,
                                            Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        favoriteService.addFavorite(userId, fairytaleId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{fairytaleId}")
    public ResponseEntity<Void> removeFavorite(@PathVariable Long fairytaleId,
                                               Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        favoriteService.removeFavorite(userId, fairytaleId);
        return ResponseEntity.noContent().build();
    }
}
