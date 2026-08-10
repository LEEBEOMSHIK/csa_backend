package org.example.csa_backend.storycontent;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ContentMigrationControlRepository extends JpaRepository<ContentMigrationControl, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ContentMigrationControl c where c.singletonId = 1")
    ContentMigrationControl getSingletonForUpdate();

    default ContentMigrationControl getSingleton() {
        return findById((short) 1).orElseThrow();
    }
}
