package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.example.csa_backend.storycontent.StoryContentExceptionHandler;
import org.example.csa_backend.storycontent.dto.StoryContentErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class ContentMigrationExceptionHandlerTest {

    @Test
    void migrationFailureRetainsStatusStableCodeAndBarrierEpoch() {
        StoryContentExceptionHandler handler = new StoryContentExceptionHandler();

        ResponseEntity<StoryContentErrorResponse> response = handler.handleContentMigration(
            ContentMigrationException.serviceUnavailable("CONTENT_MIGRATION_FREEZE", 41L)
        );

        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONTENT_MIGRATION_FREEZE");
        assertThat(response.getBody().data()).isEqualTo(Map.of("barrierEpoch", 41L));
    }
}
