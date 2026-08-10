package org.example.csa_backend.storycontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.example.csa_backend.common.exception.GlobalExceptionHandler;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class StoryRuntimeControllerTest {

    @Mock
    private StoryRuntimeService service;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new StoryRuntimeController(service))
            .setControllerAdvice(new GlobalExceptionHandler(), new StoryContentExceptionHandler())
            .build();
    }

    @Test
    void publishedStoryRuntimeIsFlatAndBindsAllCapabilities() throws Exception {
        StoryRuntimeManifestResponse response = objectMapper.readValue(
            fixture("story-runtime-v1-uploaded-video.json"), StoryRuntimeManifestResponse.class
        );
        when(service.getPublishedRuntime(eq(7L), any())).thenReturn(response);

        mockMvc.perform(get("/stories/7/runtime")
                .param("rendition", "VIDEO")
                .param("locale", "ja")
                .param("voiceType", "mom")
                .param("supportedRuntimeSchemaVersions", "1", "2")
                .param("supportedRenditions", "SLIDE", "VIDEO")
                .param("rendererVersion", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.storyId").value(101))
            .andExpect(jsonPath("$.contentVersionId").value(17))
            .andExpect(jsonPath("$.rendition").value("VIDEO"))
            .andExpect(jsonPath("$.manifestChecksum")
                .value("8471576d67265780f33e381ada86dcb40640397e4721b06d1fdcf00ddec533c0"))
            .andExpect(jsonPath("$.audioVariants.length()").value(3))
            .andExpect(jsonPath("$.manifest").doesNotExist());

        ArgumentCaptor<RuntimeCapabilities> captor = ArgumentCaptor.forClass(RuntimeCapabilities.class);
        org.mockito.Mockito.verify(service).getPublishedRuntime(eq(7L), captor.capture());
        RuntimeCapabilities capabilities = captor.getValue();
        assertThat(capabilities.rendition()).isEqualTo("VIDEO");
        assertThat(capabilities.locale()).isEqualTo("ja");
        assertThat(capabilities.voiceType()).isEqualTo("mom");
        assertThat(capabilities.supportedRuntimeSchemaVersions()).containsExactly(1, 2);
        assertThat(capabilities.supportedRenditions()).containsExactly("SLIDE", "VIDEO");
        assertThat(capabilities.rendererVersion()).isEqualTo(1);
    }

    @Test
    void privateOrUnknownStorySerializesStable404Code() throws Exception {
        when(service.getPublishedRuntime(eq(8L), any()))
            .thenThrow(StoryRuntimeException.notFound("STORY_NOT_FOUND"));

        mockMvc.perform(get("/stories/8/runtime").param("locale", "ko"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("STORY_NOT_FOUND"));
    }

    @Test
    void brokenPublishedManifestSerializesStable503Code() throws Exception {
        when(service.getPublishedRuntime(eq(7L), any()))
            .thenThrow(StoryRuntimeException.unavailable("PUBLISHED_MANIFEST_UNAVAILABLE"));

        mockMvc.perform(get("/stories/7/runtime").param("locale", "ko"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("PUBLISHED_MANIFEST_UNAVAILABLE"));
    }

    private String fixture(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/contracts", name), StandardCharsets.UTF_8).trim();
    }
}
