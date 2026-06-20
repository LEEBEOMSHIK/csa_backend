package org.example.csa_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@Getter
@Setter
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private String mode = "local";
    private String localBasePath = "C:/project/csa/generated-fairytales";
    private String cdnBaseUrl = "";
    private String serverBaseUrl = "http://localhost:8080";

    @NestedConfigurationProperty
    private S3 s3 = new S3();

    @Getter
    @Setter
    public static class S3 {
        private String endpoint = "";
        private String region = "";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyleAccess = true;
    }
}
