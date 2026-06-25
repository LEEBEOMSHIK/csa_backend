package org.example.csa_backend.subscription;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GooglePlayServiceAccountAccessTokenProviderTest {

    @Test
    void getAccessTokenPostsSignedAssertionToOauthTokenEndpoint() throws Exception {
        GooglePlayProperties properties = new GooglePlayProperties();
        properties.setServiceAccountEmail("store-verifier@example.iam.gserviceaccount.com");
        properties.setServiceAccountPrivateKey(privateKeyPem());
        properties.setTokenUrl("https://oauth2.googleapis.com/token");
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GooglePlayServiceAccountJwtFactory jwtFactory =
                new GooglePlayServiceAccountJwtFactory(new ObjectMapper());
        GooglePlayServiceAccountAccessTokenProvider provider =
                new GooglePlayServiceAccountAccessTokenProvider(properties, builder, jwtFactory);

        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString(
                        "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer")))
                .andExpect(content().string(containsString("assertion=")))
                .andRespond(withSuccess("""
                        {
                          "access_token": "access-token",
                          "expires_in": 3600,
                          "token_type": "Bearer"
                        }
                        """, MediaType.APPLICATION_JSON));

        String accessToken = provider.getAccessToken();

        assertThat(accessToken).isEqualTo("access-token");
        server.verify();
    }

    private String privateKeyPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(keyPair.getPrivate().getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----";
    }
}
