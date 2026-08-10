package org.example.csa_backend.storycontent;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.example.csa_backend.storycontent.dto.RuntimeCapabilities;
import org.example.csa_backend.storycontent.dto.StoryRuntimeManifestResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
public class StoryRuntimeController {

    private final StoryRuntimeService storyRuntimeService;

    @GetMapping("/{storyId}/runtime")
    public StoryRuntimeManifestResponse getPublishedRuntime(
        @PathVariable Long storyId,
        @RequestParam(required = false) String rendition,
        @RequestParam(required = false) String locale,
        @RequestParam(required = false) String voiceType,
        @RequestParam(required = false) List<Integer> supportedRuntimeSchemaVersions,
        @RequestParam(required = false) List<String> supportedRenditions,
        @RequestParam(required = false) Integer rendererVersion
    ) {
        RuntimeCapabilities capabilities = new RuntimeCapabilities(
            rendition,
            locale,
            voiceType,
            supportedRuntimeSchemaVersions,
            supportedRenditions,
            rendererVersion
        );
        return storyRuntimeService.getPublishedRuntime(storyId, capabilities);
    }
}
