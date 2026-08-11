package org.example.csa_backend.storycontent;

import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ContentReadRouter {

    private final ContentMigrationControlRepository controlRepository;

    @Transactional(readOnly = true)
    public <T> T route(Supplier<T> legacyRead, Supplier<T> canonicalRead) {
        ContentSource readSource = controlRepository.getSingleton().getReadSource();
        return readSource == ContentSource.CANONICAL ? canonicalRead.get() : legacyRead.get();
    }
}
