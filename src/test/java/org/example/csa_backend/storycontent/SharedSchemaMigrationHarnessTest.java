package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("shared-schema-migrate")
class SharedSchemaMigrationHarnessTest {

    @Test
    void migratesTheSharedLocalDatabaseThroughV16() {
        String url = System.getProperty("csa.sharedDbUrl", "jdbc:postgresql://localhost:15432/csa");
        String username = System.getProperty("csa.sharedDbUsername", "myuser");
        String password = System.getProperty("csa.sharedDbPassword", "secret");
        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .schemas("front")
            .defaultSchema("front")
            .locations("classpath:db/migration")
            .load();

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");
    }
}
