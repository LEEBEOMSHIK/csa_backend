package org.example.csa_backend.subscription;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "store.google.pubsub-oidc")
public class PubSubOidcProperties {

    private String audience = "";
    private String serviceAccountEmail = "";
    private String certsUrl = "https://www.googleapis.com/oauth2/v3/certs";
}
