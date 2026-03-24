package org.example.csa_backend.favorite;

import org.example.csa_backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    @Query("SELECT f FROM Favorite f JOIN FETCH f.fairytale JOIN FETCH f.fairytale.categories WHERE f.user = :user")
    List<Favorite> findByUserWithFairytale(@Param("user") User user);

    Optional<Favorite> findByUserAndFairytaleId(User user, Long fairytaleId);

    boolean existsByUserAndFairytaleId(User user, Long fairytaleId);
}
