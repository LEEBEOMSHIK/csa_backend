package org.example.csa_backend;

import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.config.StorageProperties;
import org.example.csa_backend.subscription.AppleProperties;
import org.example.csa_backend.subscription.GooglePlayProperties;
import org.example.csa_backend.subscription.PubSubOidcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AiGenerationProperties.class, StorageProperties.class,
        GooglePlayProperties.class, AppleProperties.class, PubSubOidcProperties.class})
public class CsaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsaBackendApplication.class, args);
    }

}
