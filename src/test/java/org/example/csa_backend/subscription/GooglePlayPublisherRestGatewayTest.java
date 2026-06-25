package org.example.csa_backend.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlayPublisherRestGatewayTest {

    @Test
    void getSubscriptionCallsAndroidPublisherWithBearerToken() {
        GooglePlayProperties properties = new GooglePlayProperties();
        properties.setPublisherBaseUrl("https://androidpublisher.googleapis.com");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlayAccessTokenProvider tokenProvider = () -> "access-token";
        GooglePlayPublisherRestGateway gateway =
                new GooglePlayPublisherRestGateway(properties, builder, tokenProvider);

        server.expect(requestTo("https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
                        + "com.example.csa_frontend/purchases/subscriptionsv2/tokens/purchase-token"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess("""
                        {
                          "subscriptionState": "SUBSCRIPTION_STATE_ACTIVE",
                          "latestOrderId": "GPA.1234",
                          "lineItems": [
                            {
                              "productId": "premium_monthly",
                              "expiryTime": "2026-07-25T00:00:00Z",
                              "autoRenewingPlan": {
                                "autoRenewEnabled": true
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GooglePlaySubscriptionPurchase purchase =
                gateway.getSubscription("com.example.csa_frontend", "purchase-token");

        assertThat(purchase.subscriptionState()).isEqualTo("SUBSCRIPTION_STATE_ACTIVE");
        assertThat(purchase.lineItems()).hasSize(1);
        assertThat(purchase.lineItems().get(0).productId()).isEqualTo("premium_monthly");
        server.verify();
    }
}
