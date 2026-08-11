package org.example.csa_backend.storycontent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

final class PublishedMediaPathGuard {

    private PublishedMediaPathGuard() {
    }

    static Path requireReadableFile(Path configuredRoot, Path candidate) throws IOException {
        Path root = configuredRoot.toAbsolutePath().normalize();
        Path target = candidate.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Published media path escapes configured root");
        }
        ensureNoLinkOrReparseComponents(target);
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Published media object is unavailable");
        }
        Path realRoot = root.toRealPath();
        Path realTarget = target.toRealPath();
        if (!realTarget.startsWith(realRoot)) {
            throw new IOException("Published media real path escapes configured root");
        }
        return target;
    }

    static Path requireSafeWriteTarget(Path configuredRoot, Path candidate) throws IOException {
        Path root = configuredRoot.toAbsolutePath().normalize();
        Path target = candidate.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Published media path escapes configured root");
        }
        ensureNoLinkOrReparseComponents(target);
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Published media root is not a directory");
            }
            Path existing = nearestExisting(target);
            Path realRoot = root.toRealPath();
            Path realExisting = existing.toRealPath();
            if (!realExisting.startsWith(realRoot)) {
                throw new IOException("Published media real path escapes configured root");
            }
        }
        return target;
    }

    private static void ensureNoLinkOrReparseComponents(Path target) throws IOException {
        Path current = target.getRoot();
        if (current == null) {
            throw new IOException("Published media path has no filesystem root");
        }
        for (Path segment : current.relativize(target)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && isLinkOrReparsePoint(current)) {
                throw new IOException("Published media path contains a link or reparse point");
            }
        }
    }

    private static Path nearestExisting(Path target) throws IOException {
        Path current = target;
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.getParent();
        }
        if (current == null) {
            throw new IOException("Published media path has no existing ancestor");
        }
        return current;
    }

    private static boolean isLinkOrReparsePoint(Path path) throws IOException {
        if (Files.isSymbolicLink(path)) {
            return true;
        }
        BasicFileAttributes attributes = Files.readAttributes(
            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS
        );
        return attributes.isOther() || !path.toRealPath().equals(path.toAbsolutePath().normalize());
    }
}
