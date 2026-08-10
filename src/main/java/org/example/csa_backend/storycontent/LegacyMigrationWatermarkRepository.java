package org.example.csa_backend.storycontent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LegacyMigrationWatermarkRepository extends JpaRepository<LegacyMigrationWatermark, String> {
}
