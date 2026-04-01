package org.example.csa_backend.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String basePath = storageProperties.getLocalBasePath().replace("\\", "/");
        if (!basePath.endsWith("/")) {
            basePath = basePath + "/";
        }
        registry.addResourceHandler("/files/generated-fairytales/**")
                .addResourceLocations("file:" + basePath);
    }
}
