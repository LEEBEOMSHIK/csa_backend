package org.example.csa_backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

class TrackedRuntimeConfigurationTest {

    private static final String TEST_DB_PASSWORD = "test-db-password-not-for-runtime";
    private static final String TEST_USER_JWT_SECRET = "test-user-jwt-secret-not-for-runtime-0123456789";

    @Test
    void localProfileLoadsCompleteRuntimeContractFromTrackedApplicationYaml() {
        Path repository = Path.of("").toAbsolutePath().normalize();

        run(
            repository,
            new String[] {
                "DB_PASSWORD=" + TEST_DB_PASSWORD,
                "CSA_USER_JWT_SECRET=" + TEST_USER_JWT_SECRET
            },
            context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(RuntimeContract.class)).isEqualTo(new RuntimeContract(
                    "jdbc:postgresql://localhost:15432/csa",
                    "org.postgresql.Driver",
                    "myuser",
                    TEST_DB_PASSWORD,
                    "front",
                    "validate",
                    "front",
                    true,
                    "front",
                    "front",
                    true,
                    TEST_USER_JWT_SECRET,
                    86_400_000L,
                    604_800_000L,
                    18_080,
                    "local",
                    "generated-fairytales",
                    "http://localhost:18080",
                    "local",
                    "uploads",
                    "",
                    "",
                    "http://localhost:18080/uploads",
                    "ffprobe",
                    5_000,
                    "localhost",
                    13_310,
                    5_000,
                    5_000
                ));
            }
        );

        assertThat(Files.exists(repository.resolve("src/main/resources/application-local.yaml"))).isFalse();
    }

    @Test
    void localProfileRequiresDatabasePasswordWithoutTrackedFallback() {
        run(Path.of("").toAbsolutePath().normalize(), new String[] {
            "CSA_USER_JWT_SECRET=" + TEST_USER_JWT_SECRET
        }, context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("${DB_PASSWORD}");
        });
    }

    @Test
    void localProfileRequiresUserJwtSecretWithoutTrackedFallback() {
        run(Path.of("").toAbsolutePath().normalize(), new String[] {
            "DB_PASSWORD=" + TEST_DB_PASSWORD
        }, context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("${CSA_USER_JWT_SECRET}");
        });
    }

    @Test
    void composeFullProfileSeparatesBackendFromDefaultInfrastructureModel() throws Exception {
        ComposeResult defaultModel = composeServices();
        assertThat(defaultModel.exitCode()).as(defaultModel.output()).isZero();
        assertThat(defaultModel.services()).containsExactlyInAnyOrder("postgres", "clamav");

        ComposeResult fullModel = composeServices("--profile", "full");
        assertThat(fullModel.exitCode()).as(fullModel.output()).isZero();
        assertThat(fullModel.services()).containsExactlyInAnyOrder("postgres", "clamav", "backend");
    }

    private ComposeResult composeServices(String... options) throws IOException, InterruptedException {
        Path repository = Path.of("").toAbsolutePath().normalize();
        List<String> command = new ArrayList<>(List.of(
            "docker",
            "compose",
            "--file",
            repository.resolve("compose.yaml").toString()
        ));
        command.addAll(Arrays.asList(options));
        command.addAll(List.of("config", "--services"));

        ProcessBuilder processBuilder = new ProcessBuilder(command)
            .directory(repository.toFile())
            .redirectErrorStream(true);
        processBuilder.environment().remove("COMPOSE_PROFILES");
        processBuilder.environment().put("DB_PASSWORD", TEST_DB_PASSWORD);
        processBuilder.environment().put("CSA_USER_JWT_SECRET", TEST_USER_JWT_SECRET);

        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();
        List<String> services = output.lines().filter(line -> !line.isBlank()).toList();
        return new ComposeResult(exitCode, services, output);
    }

    private void run(
        Path repository,
        String[] environment,
        ContextConsumer<org.springframework.boot.test.context.assertj.AssertableApplicationContext> assertions
    ) {
        String location = repository.resolve("src/main/resources/application.yaml").toUri().toString();
        new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.config.location=" + location, "spring.profiles.active=local")
            .withPropertyValues(environment)
            .withUserConfiguration(RuntimeContractConfiguration.class)
            .run(assertions);
    }

    @Configuration(proxyBeanMethods = false)
    static class RuntimeContractConfiguration {

        @Bean
        RuntimeContract runtimeContract(Environment environment) {
            return new RuntimeContract(
                environment.getRequiredProperty("spring.datasource.url"),
                environment.getRequiredProperty("spring.datasource.driver-class-name"),
                environment.getRequiredProperty("spring.datasource.username"),
                environment.getRequiredProperty("spring.datasource.password"),
                environment.getRequiredProperty("spring.datasource.hikari.schema"),
                environment.getRequiredProperty("spring.jpa.hibernate.ddl-auto"),
                environment.getRequiredProperty("spring.jpa.properties.hibernate.default_schema"),
                environment.getRequiredProperty("spring.flyway.enabled", Boolean.class),
                environment.getRequiredProperty("spring.flyway.schemas"),
                environment.getRequiredProperty("spring.flyway.default-schema"),
                environment.getRequiredProperty("spring.flyway.create-schemas", Boolean.class),
                environment.getRequiredProperty("jwt.secret"),
                environment.getRequiredProperty("jwt.access-expiration", Long.class),
                environment.getRequiredProperty("jwt.refresh-expiration", Long.class),
                environment.getRequiredProperty("server.port", Integer.class),
                environment.getRequiredProperty("storage.mode"),
                environment.getRequiredProperty("storage.local-base-path"),
                environment.getRequiredProperty("storage.server-base-url"),
                environment.getRequiredProperty("csa.media.storage-mode"),
                environment.getRequiredProperty("csa.media.storage-root"),
                environment.getRequiredProperty("csa.media.bucket"),
                environment.getRequiredProperty("csa.media.prefix"),
                environment.getRequiredProperty("csa.media.public-base-url"),
                environment.getRequiredProperty("csa.media.ffprobe-path"),
                environment.getRequiredProperty("csa.media.probe-timeout-ms", Integer.class),
                environment.getRequiredProperty("csa.media.clamav.host"),
                environment.getRequiredProperty("csa.media.clamav.port", Integer.class),
                environment.getRequiredProperty("csa.media.clamav.connect-timeout-ms", Integer.class),
                environment.getRequiredProperty("csa.media.clamav.read-timeout-ms", Integer.class)
            );
        }
    }

    record RuntimeContract(
        String datasourceUrl,
        String datasourceDriver,
        String datasourceUsername,
        String datasourcePassword,
        String datasourceSchema,
        String ddlAuto,
        String defaultSchema,
        boolean flywayEnabled,
        String flywaySchemas,
        String flywayDefaultSchema,
        boolean flywayCreateSchemas,
        String jwtSecret,
        long jwtAccessExpiration,
        long jwtRefreshExpiration,
        int serverPort,
        String legacyStorageMode,
        String legacyStorageRoot,
        String legacyServerBaseUrl,
        String mediaStorageMode,
        String mediaStorageRoot,
        String mediaBucket,
        String mediaPrefix,
        String mediaPublicBaseUrl,
        String ffprobePath,
        int probeTimeoutMs,
        String clamavHost,
        int clamavPort,
        int clamavConnectTimeoutMs,
        int clamavReadTimeoutMs
    ) {
    }

    record ComposeResult(int exitCode, List<String> services, String output) {
    }
}
