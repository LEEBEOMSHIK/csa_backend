package org.example.csa_backend.auth.oauth;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
public class GoogleOAuthClient {

    private static final String USERINFO_URI = "https://www.googleapis.com/oauth2/v3/userinfo";

    private final RestClient restClient = RestClient.create();

    public GoogleUserInfo getUserInfo(String accessToken) {
        Map<?, ?> response = restClient.get()
                .uri(USERINFO_URI)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("sub") == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Google 액세스 토큰입니다.");
        }

        return new GoogleUserInfo(
                (String) response.get("sub"),
                (String) response.get("email"),
                (String) response.get("name")
        );
    }
}
