package org.example.csa_backend.subscription;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Set;

/**
 * Builds an EC CA + leaf chain and ES256 JWS tokens so Apple verification can be exercised
 * offline, without a real Apple signing key or a network call.
 */
final class AppleJwsTestSupport {

    final X509Certificate root;
    final X509Certificate leaf;
    private final PrivateKey leafPrivateKey;

    private AppleJwsTestSupport(X509Certificate root, X509Certificate leaf, PrivateKey leafPrivateKey) {
        this.root = root;
        this.leaf = leaf;
        this.leafPrivateKey = leafPrivateKey;
    }

    static AppleJwsTestSupport generate() throws Exception {
        KeyPair rootKey = ecKeyPair();
        KeyPair leafKey = ecKeyPair();
        X509Certificate root = certificate("CN=Apple Test Root", "CN=Apple Test Root",
                rootKey.getPublic(), rootKey.getPrivate(), BigInteger.ONE);
        X509Certificate leaf = certificate("CN=Apple Test Root", "CN=Apple Test Leaf",
                leafKey.getPublic(), rootKey.getPrivate(), BigInteger.TWO);
        return new AppleJwsTestSupport(root, leaf, leafKey.getPrivate());
    }

    Set<TrustAnchor> trustAnchors() {
        return Set.of(new TrustAnchor(root, null));
    }

    String rootBase64() throws Exception {
        return Base64.getEncoder().encodeToString(root.getEncoded());
    }

    String signedJws(String payloadJson) throws Exception {
        String header = "{\"alg\":\"ES256\",\"x5c\":[\""
                + Base64.getEncoder().encodeToString(leaf.getEncoded()) + "\",\""
                + Base64.getEncoder().encodeToString(root.getEncoded()) + "\"]}";
        String encodedHeader = base64Url(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = base64Url(signP1363(signingInput));
        return signingInput + "." + signature;
    }

    String jwsWithBrokenSignature(String payloadJson) throws Exception {
        String jws = signedJws(payloadJson);
        String[] parts = jws.split("\\.");
        byte[] sig = Base64.getUrlDecoder().decode(parts[2]);
        sig[0] ^= 0x01;
        return parts[0] + "." + parts[1] + "." + base64Url(sig);
    }

    private byte[] signP1363(String signingInput) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(leafPrivateKey);
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signature.sign();
    }

    private static String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static KeyPair ecKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private static X509Certificate certificate(String issuer, String subject,
                                               java.security.PublicKey subjectKey,
                                               PrivateKey issuerKey,
                                               BigInteger serial) throws Exception {
        Instant now = Instant.now();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                new X500Name(issuer),
                serial,
                Date.from(now.minusSeconds(60)),
                Date.from(now.plusSeconds(86_400)),
                new X500Name(subject),
                subjectKey);
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withECDSA").build(issuerKey);
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }
}
