package org.example.csa_backend.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "features.ai-generation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AiProperties.class)
public class AiGenerationConfiguration {
}
