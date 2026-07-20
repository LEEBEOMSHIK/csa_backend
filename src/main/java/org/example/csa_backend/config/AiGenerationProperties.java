package org.example.csa_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "features.ai-generation")
public class AiGenerationProperties {

    private boolean enabled;
}
