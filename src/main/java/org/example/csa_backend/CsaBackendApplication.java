package org.example.csa_backend;

import org.example.csa_backend.config.AiProperties;
import org.example.csa_backend.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({AiProperties.class, StorageProperties.class})
public class CsaBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(CsaBackendApplication.class, args);
    }

}
