package org.example.csa_backend.storycontent.migration;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ContentCutoverTransactions {

    @Transactional
    public <T> T required(Supplier<T> action) {
        return action.get();
    }
}
