package org.example.csa_backend.storycontent;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class StoryRuntimeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private StoryRuntimeException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public static StoryRuntimeException notFound(String code) {
        return new StoryRuntimeException(HttpStatus.NOT_FOUND, code, "Story not found", null);
    }

    public static StoryRuntimeException unavailable(String code) {
        return unavailable(code, null);
    }

    public static StoryRuntimeException unavailable(String code, Throwable cause) {
        return new StoryRuntimeException(
            HttpStatus.SERVICE_UNAVAILABLE, code, "Published manifest is unavailable", cause
        );
    }
}
