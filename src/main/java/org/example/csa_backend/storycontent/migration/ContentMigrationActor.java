package org.example.csa_backend.storycontent.migration;

import org.springframework.stereotype.Component;

@Component
public class ContentMigrationActor {

    private static final String VALUE = "csa_backend:content-migration-cli";

    public String value() {
        return VALUE;
    }
}
