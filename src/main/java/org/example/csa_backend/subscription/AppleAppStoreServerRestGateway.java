package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("prod")
class AppleAppStoreServerRestGateway implements AppleAppStoreServerGateway {

    private final RestClient restClient;
    private final AppleProperties properties;
    private final AppleAppStoreServerJwtFactory jwtFactory;

    AppleAppStoreServerRestGateway(AppleProperties properties,
                                   RestClient.Builder restClientBuilder,
                                   AppleAppStoreServerJwtFactory jwtFactory) {
        this.restClient = restClientBuilder.baseUrl(properties.getServerBaseUrl()).build();
        this.properties = properties;
        this.jwtFactory = jwtFactory;
    }

    @Override
    public AppleSubscriptionStatusResponse getSubscriptionStatuses(String transactionId) {
        AppleSubscriptionStatusResponse response;
        try {
            response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/inApps/v1/subscriptions/{transactionId}")
                            .build(transactionId))
                    .header("Authorization", "Bearer " + jwtFactory.createToken(properties))
                    .retrieve()
                    .body(AppleSubscriptionStatusResponse.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 구독 정보 조회에 실패했습니다.");
        }
        if (response == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Apple 구독 정보를 조회하지 못했습니다.");
        }
        return response;
    }
}
