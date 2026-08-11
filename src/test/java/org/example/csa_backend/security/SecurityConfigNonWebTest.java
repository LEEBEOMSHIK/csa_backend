package org.example.csa_backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

class SecurityConfigNonWebTest {

    @Test
    void nonWebContextKeepsPasswordEncodingWithoutServletSecurity() throws Exception {
        Class<?> passwordEncoderConfiguration = Class.forName(
            "org.example.csa_backend.security.PasswordEncoderConfiguration"
        );
        new ApplicationContextRunner()
            .withUserConfiguration(SecurityConfig.class, passwordEncoderConfiguration)
            .run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecurityConfig.class);
            assertThat(context).hasSingleBean(PasswordEncoder.class);
            assertThat(context).doesNotHaveBean(SecurityFilterChain.class);
        });
    }
}
