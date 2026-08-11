package org.example.csa_backend.storycontent.migration;

public interface CutoverTransactionHook {

    void afterCanonicalSourceUpdate();
}
