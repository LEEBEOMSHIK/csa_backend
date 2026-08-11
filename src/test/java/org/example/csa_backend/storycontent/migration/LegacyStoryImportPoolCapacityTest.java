package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyStoryImportPoolCapacityTest {

    @Test
    void migrationImportRejectsSingleConnectionPoolBeforeAcquiringSessionLock() {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setMaximumPoolSize(1);
            LegacyStoryImportService service = new LegacyStoryImportService(
                new JdbcTemplate(dataSource),
                mock(LegacyStoryProjectionMapper.class),
                mock(LegacyMediaSnapshotStore.class),
                mock(CanonicalStoryWriter.class),
                new ContractChecksum()
            );

            assertThatThrownBy(() -> service.importCuratedBatch(0, 1))
                .isInstanceOfSatisfying(
                    LegacyImportException.class,
                    exception -> assertThat(exception.getCode())
                        .isEqualTo("LEGACY_IMPORT_POOL_SIZE_TOO_SMALL")
                );
        }
    }
}
