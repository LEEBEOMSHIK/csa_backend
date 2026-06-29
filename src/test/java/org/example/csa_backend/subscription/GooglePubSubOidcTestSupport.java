package org.example.csa_backend.subscription;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;

/**
 * Builds an RSA keypair and signs RS256 OIDC JWTs offline so the Pub/Sub OIDC verifier can be
 * exercised without a real Google signing key or a network call to the certs endpoint.
 */
final class GooglePubSubOidcTestSupport {

    static final String KID = "test-kid-1";

    private final KeyPair keyPair;

    private GooglePubSubOidcTestSupport(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    static GooglePubSubOidcTestSupport generate() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return new GooglePubSubOidcTestSupport(generator.generateKeyPair());
    }

    RSAPublicKey publicKey() {
        return (RSAPublicKey) keyPair.getPublic();
    }

    /** Signs a token with the given header and claim JSON bodies. */
    String signed(String headerJson, String claimsJson) throws Exception {
        String signingInput = base64Url(headerJson.getBytes(StandardCharsets.UTF_8))
                + "." + base64Url(claimsJson.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(sign(signingInput, keyPair.getPrivate()));
        return signingInput + "." + signature;
    }

    String header(String kid) {
        return "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}";
    }

    String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        sig[0] ^= 0x01;
        return parts[0] + "." + parts[1] + "." + base64Url(sig);
    }

    private static byte[] sign(String signingInput, PrivateKey privateKey) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
