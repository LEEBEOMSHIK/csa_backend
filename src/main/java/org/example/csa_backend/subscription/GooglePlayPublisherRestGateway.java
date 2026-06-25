package org.example.csa_backend.subscription;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("prod")
class GooglePlayPublisherRestGateway implements GooglePlaySubscriptionGateway {

    private final RestClient restClient;
    private final GooglePlayAccessTokenProvider accessTokenProvider;

    GooglePlayPublisherRestGateway(GooglePlayProperties properties,
                                   RestClient.Builder restClientBuilder,
                                   GooglePlayAccessTokenProvider accessTokenProvider) {
        this.restClient = restClientBuilder.baseUrl(properties.getPublisherBaseUrl()).build();
        this.accessTokenProvider = accessTokenProvider;
    }

    @Override
    public GooglePlaySubscriptionPurchase getSubscription(String packageName, String purchaseToken) {
        GooglePlaySubscriptionPurchase purchase;
        try {
            purchase = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/androidpublisher/v3/applications/{packageName}/purchases/subscriptionsv2/tokens/{purchaseToken}")
                            .build(packageName, purchaseToken))
                    .header("Authorization", "Bearer " + accessTokenProvider.getAccessToken())
                    .retrieve()
                    .body(GooglePlaySubscriptionPurchase.class);
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 구독 정보 조회에 실패했습니다.");
        }
        if (purchase == null) {
            throw new BusinessException(ErrorCode.EXTERNAL_API_ERROR, "Google Play 구독 정보를 조회하지 못했습니다.");
        }
        return purchase;
    }
}
