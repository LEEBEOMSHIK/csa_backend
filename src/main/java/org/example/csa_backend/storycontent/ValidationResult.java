package org.example.csa_backend.storycontent;

import java.util.List;

public record ValidationResult(List<ValidationError> errors) {

    public ValidationResult {
        errors = List.copyOf(errors);
    }

    public boolean valid() {
        return errors.isEmpty();
    }
}
