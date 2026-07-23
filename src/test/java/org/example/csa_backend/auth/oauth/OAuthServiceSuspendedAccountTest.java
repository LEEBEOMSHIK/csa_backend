package org.example.csa_backend.auth.oauth;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * SUSPENDED 상태의 기존 유저가 Google 소셜 로그인으로 재로그인을 시도하면 거부되어야 한다.
 */
@SpringBootTest
class OAuthServiceSuspendedAccountTest {

    @Autowired
    private OAuthService oAuthService;

    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private GoogleOAuthClient googleOAuthClient;

    @Test
    void googleLoginIsRejectedForSuspendedExistingUser() {
        User user = userRepository.save(
                new User("suspended-google@example.com", "google", "google-sub-suspended", "정지유저", "ko"));
        user.suspend();
        userRepository.save(user);

        when(googleOAuthClient.getUserInfo(anyString()))
                .thenReturn(new GoogleUserInfo("google-sub-suspended", "suspended-google@example.com", "정지유저"));

        OAuthRequest request = new OAuthRequest("dummy-google-access-token", "ko");

        assertThatThrownBy(() -> oAuthService.processOAuth("google", request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(ErrorCode.UNAUTHORIZED));
    }
}
