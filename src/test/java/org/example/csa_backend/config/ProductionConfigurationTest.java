package org.example.csa_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    @Test
    void productionSecretsAreInjectedFromEnvironment() throws Exception {
        ClassPathResource resource = new ClassPathResource("application-prod.yaml");
        String yaml = resource.getContentAsString(StandardCharsets.UTF_8);

        assertThat(yaml).contains("username: ${DB_USERNAME}");
        assertThat(yaml).contains("password: ${DB_PASSWORD}");
        assertThat(yaml).contains("secret: ${JWT_SECRET}");
        assertThat(yaml).doesNotContain("username: myuser");
        assertThat(yaml).doesNotContain("password: secret");
        assertThat(yaml).doesNotContain("secret: dGVzdC1zZWNyZXQ");
    }
}
