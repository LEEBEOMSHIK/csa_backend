package org.example.csa_backend.storycontent.migration;

public class LegacyImportException extends RuntimeException {

    private final String code;

    public LegacyImportException(String code, String detail) {
        super(code + (detail == null || detail.isBlank() ? "" : ": " + detail));
        this.code = code;
    }

    public LegacyImportException(String code, String detail, Throwable cause) {
        super(code + (detail == null || detail.isBlank() ? "" : ": " + detail), cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
