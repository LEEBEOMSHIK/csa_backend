package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
@Profile("prod")
class GooglePlayServiceAccountAccessTokenProvider implements GooglePlayAccessTokenProvider {

    private static final String JWT_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer";

    private final GooglePlayProperties properties;
    private final RestClient restClient;
    private final GooglePlayServiceAccountJwtFactory jwtFactory;

    GooglePlayServiceAccountAccessTokenProvider(GooglePlayProperties properties,
                                                RestClient.Builder restClientBuilder,
                                                GooglePlayServiceAccountJwtFactory jwtFactory) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
        this.jwtFactory = jwtFactory;
    }

    @Override
    public String getAccessToken() {
        String assertion = jwtFactory.createAssertion(properties);
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", JWT_GRANT_TYPE);
        form.add("assertion", assertion);

        Map<?, ?> response;
        try {
            response = restClient.post()
                    .uri(properties.getTokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 액세스 토큰 발급에 실패했습니다.");
        }
        Object accessToken = response == null ? null : response.get("access_token");
        if (!(accessToken instanceof String token) || token.isBlank()) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 액세스 토큰을 발급받지 못했습니다.");
        }
        return token;
    }
}
