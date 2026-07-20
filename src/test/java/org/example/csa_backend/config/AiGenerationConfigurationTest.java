package org.example.csa_backend.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AiGenerationConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(AiGenerationConfiguration.class);

    @Test
    void enabledFeatureFailsWhenRequiredAiSettingsAreMissing() {
        contextRunner.withPropertyValues("features.ai-generation.enabled=true")
                .run(context -> assertThat(context.getStartupFailure()).isNotNull());
    }
}
