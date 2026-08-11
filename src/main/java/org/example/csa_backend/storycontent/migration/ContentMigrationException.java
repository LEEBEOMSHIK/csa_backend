package org.example.csa_backend.storycontent.migration;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ContentMigrationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final long barrierEpoch;

    private ContentMigrationException(HttpStatus status, String code, long barrierEpoch) {
        super(code);
        this.status = status;
        this.code = code;
        this.barrierEpoch = barrierEpoch;
    }

    public static ContentMigrationException serviceUnavailable(String code, long barrierEpoch) {
        return new ContentMigrationException(HttpStatus.SERVICE_UNAVAILABLE, code, barrierEpoch);
    }

    public static ContentMigrationException conflict(String code, long barrierEpoch) {
        return new ContentMigrationException(HttpStatus.CONFLICT, code, barrierEpoch);
    }
}
