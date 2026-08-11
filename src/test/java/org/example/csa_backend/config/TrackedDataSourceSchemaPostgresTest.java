package org.example.csa_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres")
@Testcontainers
class TrackedDataSourceSchemaPostgresTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void applicationJdbcTemplateUsesFrontSchemaForUnqualifiedLegacyReads() {
        String location = Path.of("src/main/resources/application.yaml")
            .toAbsolutePath()
            .normalize()
            .toUri()
            .toString();
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                DataSourceAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                JdbcTemplateAutoConfiguration.class
            ))
            .withPropertyValues(
                "spring.config.location=" + location,
                "spring.profiles.active=local",
                "DB_PASSWORD=" + POSTGRES.getPassword(),
                "CSA_USER_JWT_SECRET=test-user-jwt-secret-for-schema-routing-0123456789",
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.datasource.driver-class-name=" + POSTGRES.getDriverClassName()
            )
            .run(context -> {
                assertThat(context).hasNotFailed();
                JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
                assertThat(jdbc.queryForObject("select current_schema()", String.class))
                    .isEqualTo("front");
                assertThat(jdbc.queryForObject("select count(*) from fairytales", Long.class))
                    .isNotNegative();
            });
    }
}
