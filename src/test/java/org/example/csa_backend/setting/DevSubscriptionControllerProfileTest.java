package org.example.csa_backend.setting;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DevSubscriptionControllerProfileTest {

    // DevSubscriptionController is registered as an annotated class so its class-level
    // @Profile("!prod") is evaluated by the context. MockConfig satisfies its constructor
    // dependency without touching a real datasource or external resources.
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(MockConfig.class, DevSubscriptionController.class);

    @Test
    void devControllerIsNotRegisteredUnderProdProfile() {
        contextRunner
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context).doesNotHaveBean(DevSubscriptionController.class));
    }

    @Test
    void devControllerIsRegisteredUnderNonProdProfile() {
        contextRunner
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("local"))
                .run(context -> assertThat(context).hasSingleBean(DevSubscriptionController.class));
    }

    @Test
    void devControllerIsRegisteredWithNoActiveProfile() {
        contextRunner
                .run(context -> assertThat(context).hasSingleBean(DevSubscriptionController.class));
    }

    @Configuration
    static class MockConfig {

        @Bean
        UserSettingsService userSettingsService() {
            return mock(UserSettingsService.class);
        }
    }
}
