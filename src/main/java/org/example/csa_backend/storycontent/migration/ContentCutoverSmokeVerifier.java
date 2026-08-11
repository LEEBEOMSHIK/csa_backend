package org.example.csa_backend.storycontent.migration;

public interface ContentCutoverSmokeVerifier {

    SmokeResult verify(long epoch);
}
