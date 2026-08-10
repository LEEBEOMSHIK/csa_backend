package org.example.csa_backend.storycontent;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetUploadSessionRepository extends JpaRepository<AssetUploadSession, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AssetUploadSession u where u.id = :id")
    Optional<AssetUploadSession> findByIdForUpdate(@Param("id") UUID id);
}
