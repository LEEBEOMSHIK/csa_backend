package org.example.csa_backend.storycontent;

import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

final class SafePublishedMediaResourceResolver extends PathResourceResolver {

    private final Path promotedRoot;

    SafePublishedMediaResourceResolver(Path promotedRoot) {
        this.promotedRoot = promotedRoot.toAbsolutePath().normalize();
    }

    @Override
    protected Resource getResource(String resourcePath, Resource location) {
        try {
            Path target = promotedRoot.resolve(resourcePath).normalize();
            Path safeTarget = PublishedMediaPathGuard.requireReadableFile(promotedRoot, target);
            return new FileSystemResource(safeTarget);
        } catch (IOException | InvalidPathException exception) {
            return null;
        }
    }
}
