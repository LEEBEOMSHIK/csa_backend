package org.example.csa_backend.subscription;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "store.apple")
public class AppleProperties {

    private String bundleId = "";
    private String issuerId = "";
    private String keyId = "";
    private String privateKey = "";
    private String environment = "Production";
    private String serverBaseUrl = "https://api.storekit.itunes.apple.com";
    private String audience = "appstoreconnect-v1";
    private java.util.List<String> rootCertificates = new java.util.ArrayList<>();
}
