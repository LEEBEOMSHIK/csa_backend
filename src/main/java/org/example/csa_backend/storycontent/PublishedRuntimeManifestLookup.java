package org.example.csa_backend.storycontent;

import java.util.Optional;

public interface PublishedRuntimeManifestLookup {

    Optional<PublishedRuntimeManifestSource> findPublished(Long versionId);
}
