package org.example.csa_backend.subscription;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "store.google")
public class GooglePlayProperties {

    private String packageName = "";
    private String serviceAccountEmail = "";
    private String serviceAccountPrivateKey = "";
    private String tokenUrl = "https://oauth2.googleapis.com/token";
    private String publisherBaseUrl = "https://androidpublisher.googleapis.com";
    private String scope = "https://www.googleapis.com/auth/androidpublisher";
}
