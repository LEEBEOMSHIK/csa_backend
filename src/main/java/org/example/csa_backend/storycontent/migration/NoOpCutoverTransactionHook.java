package org.example.csa_backend.storycontent.migration;

import org.springframework.stereotype.Component;

@Component
class NoOpCutoverTransactionHook implements CutoverTransactionHook {

    @Override
    public void afterCanonicalSourceUpdate() {
    }
}
