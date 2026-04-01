package org.example.csa_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String mode = "local";
    private String localBasePath = "C:/project/csa/generated-fairytales";
    private String cdnBaseUrl = "";
    private String serverBaseUrl = "http://localhost:8080";
}
