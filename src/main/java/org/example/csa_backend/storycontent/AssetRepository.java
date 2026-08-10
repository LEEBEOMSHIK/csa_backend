package org.example.csa_backend.storycontent;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    List<Asset> findByOwnerVersionIdOrderByIdAsc(Long ownerVersionId);
}
