package org.example.csa_backend.storycontent.migration;

import org.springframework.stereotype.Component;

@Component
public class ContentServiceIdentity {

    public String value() {
        return "csa_backend";
    }
}
