package org.example.csa_backend.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Claude claude = new Claude();
    private Dalle dalle = new Dalle();
    private Elevenlabs elevenlabs = new Elevenlabs();

    @PostConstruct
    void validateRequiredSettings() {
        requireText(claude.getApiKey(), "CLAUDE_API_KEY");
        requireText(dalle.getApiKey(), "OPENAI_API_KEY");
        requireText(elevenlabs.getApiKey(), "ELEVENLABS_API_KEY");
        requireText(elevenlabs.getVoiceIds().get("dad"), "ELEVENLABS_VOICE_DAD");
        requireText(elevenlabs.getVoiceIds().get("mom"), "ELEVENLABS_VOICE_MOM");
        requireText(elevenlabs.getVoiceIds().get("grandma"), "ELEVENLABS_VOICE_GRANDMA");
        requireText(elevenlabs.getVoiceIds().get("grandpa"), "ELEVENLABS_VOICE_GRANDPA");
    }

    private void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " must be configured when AI generation is enabled.");
        }
    }

    @Getter
    @Setter
    public static class Claude {
        private String apiKey = "";
        private String model = "claude-sonnet-4-6";
        private String baseUrl = "https://api.anthropic.com";
    }

    @Getter
    @Setter
    public static class Dalle {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com";
    }

    @Getter
    @Setter
    public static class Elevenlabs {
        private String apiKey = "";
        private String baseUrl = "https://api.elevenlabs.io";
        private Map<String, String> voiceIds = new HashMap<>();
    }
}
