package org.example.csa_backend.storycontent;

import org.example.csa_backend.storycontent.dto.StoredRuntimeManifest;

public record VerifiedStoredManifest(StoredRuntimeManifest manifest, String storedBytesSha256) {
}
