package org.example.csa_backend.setting;

import org.example.csa_backend.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TermAgreementRepository extends JpaRepository<TermAgreement, Long> {

    List<TermAgreement> findByUser(User user);

    Optional<TermAgreement> findFirstByUserAndTermTypeOrderByAgreedAtDesc(User user, TermType termType);

    boolean existsByUserAndTermTypeAndTermVersion(User user, TermType termType, String termVersion);
}
