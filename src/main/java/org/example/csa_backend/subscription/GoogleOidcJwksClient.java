package org.example.csa_backend.subscription;

import java.security.interfaces.RSAPublicKey;
import java.util.Map;

/**
 * Fetches Google's OIDC signing keys (JWKS). Abstracted so verification logic can be unit-tested
 * with an in-memory key set, without a network call to the certs endpoint.
 */
interface GoogleOidcJwksClient {

    /**
     * Returns the current set of RSA public keys keyed by {@code kid}. Implementations may cache
     * the result; callers force a refresh on an unknown {@code kid} via {@link #fetchKeys(boolean)}.
     */
    Map<String, RSAPublicKey> fetchKeys(boolean forceRefresh);
}
