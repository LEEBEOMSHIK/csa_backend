package org.example.csa_backend.favorite;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.fairytale.Fairytale;
import org.example.csa_backend.fairytale.FairytaleRepository;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final FairytaleRepository fairytaleRepository;

    @Transactional(readOnly = true)
    public List<FairytaleDto> getFavorites(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return favoriteRepository.findByUserWithFairytale(user).stream()
                .map(f -> FairytaleDto.from(f.getFairytale()))
                .toList();
    }

    @Transactional
    public void addFavorite(Long userId, Long fairytaleId) {
        User user = userRepository.findById(userId).orElseThrow();
        if (favoriteRepository.existsByUserAndFairytaleId(user, fairytaleId)) {
            return;
        }
        Fairytale fairytale = fairytaleRepository.findById(fairytaleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fairytale not found"));
        favoriteRepository.save(new Favorite(user, fairytale));
    }

    @Transactional
    public void removeFavorite(Long userId, Long fairytaleId) {
        User user = userRepository.findById(userId).orElseThrow();
        favoriteRepository.findByUserAndFairytaleId(user, fairytaleId)
                .ifPresent(favoriteRepository::delete);
    }
}
