package org.example.csa_backend.storycontent;

import java.util.Map;
import org.example.csa_backend.storycontent.migration.ContentMigrationException;
import org.example.csa_backend.storycontent.dto.StoryContentErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class StoryContentExceptionHandler {

    @ExceptionHandler(StoryRuntimeException.class)
    public ResponseEntity<StoryContentErrorResponse> handleStoryRuntime(StoryRuntimeException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
            new StoryContentErrorResponse(false, exception.getCode(), exception.getMessage(), null)
        );
    }

    @ExceptionHandler(ContentMigrationException.class)
    public ResponseEntity<StoryContentErrorResponse> handleContentMigration(ContentMigrationException exception) {
        return ResponseEntity.status(exception.getStatus()).body(
            new StoryContentErrorResponse(
                false,
                exception.getCode(),
                exception.getMessage(),
                Map.of("barrierEpoch", exception.getBarrierEpoch())
            )
        );
    }
}
