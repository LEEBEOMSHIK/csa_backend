package org.example.csa_backend.auth;

import org.example.csa_backend.auth.dto.LoginRequest;
import org.example.csa_backend.auth.dto.TokenResponse;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 정지(SUSPENDED)된 계정은 로그인/토큰 재발급 단계에서부터 거부되어야 한다.
 * 보호된 API 자체는 JwtAuthenticationFilter가 이미 매 요청 재검증하지만,
 * 로그인/refresh 단계도 의미상 동일하게 막아야 한다.
 */
@SpringBootTest
class AuthServiceSuspendedAccountTest {

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void loginIsRejectedForSuspendedUser() {
        User user = userRepository.save(
                new User("suspended-login@example.com", passwordEncoder.encode("password123!")));
        user.suspend();
        userRepository.save(user);

        LoginRequest request = new LoginRequest("suspended-login@example.com", "password123!");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }

    @Test
    void refreshIsRejectedAfterUserIsSuspended() {
        User user = userRepository.save(
                new User("suspended-refresh@example.com", passwordEncoder.encode("password123!")));

        TokenResponse tokens = authService.login(
                new LoginRequest("suspended-refresh@example.com", "password123!"));

        User managedUser = userRepository.findById(user.getId()).orElseThrow();
        managedUser.suspend();
        userRepository.save(managedUser);

        assertThatThrownBy(() -> authService.refresh(tokens.refreshToken()))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
