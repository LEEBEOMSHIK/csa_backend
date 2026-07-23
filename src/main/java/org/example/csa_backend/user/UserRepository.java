package org.example.csa_backend.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByProviderAndProviderId(String provider, String providerId);

    // admin 검색 전용 (Pageable 최초 도입) — 기존 조회 메서드는 변경하지 않음
    Page<User> findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(
            String email, String name, Pageable pageable);
}
