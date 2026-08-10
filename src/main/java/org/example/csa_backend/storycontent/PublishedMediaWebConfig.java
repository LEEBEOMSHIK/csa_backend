package org.example.csa_backend.storycontent;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnProperty(prefix = "csa.media", name = "storage-mode", havingValue = "local", matchIfMissing = true)
public class PublishedMediaWebConfig implements WebMvcConfigurer {

    private final PublishedMediaRoute route;
    private final Path storageRoot;

    public PublishedMediaWebConfig(
        PublishedMediaRoute route,
        @Value("${csa.media.storage-root:uploads}") String storageRoot
    ) {
        this.route = route;
        this.storageRoot = Path.of(storageRoot);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path promotedRoot = route.promotedRoot(storageRoot);
        registry.addResourceHandler(route.requestPattern())
            .addResourceLocations(route.resourceLocation(storageRoot))
            .resourceChain(false)
            .addResolver(new SafePublishedMediaResourceResolver(promotedRoot));
    }
}
