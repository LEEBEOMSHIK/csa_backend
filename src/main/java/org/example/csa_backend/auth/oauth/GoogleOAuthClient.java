package org.example.csa_backend.auth.oauth;

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
            throw new IllegalArgumentException("Invalid Google access token");
        }

        return new GoogleUserInfo(
                (String) response.get("sub"),
                (String) response.get("email"),
                (String) response.get("name")
        );
    }
}
