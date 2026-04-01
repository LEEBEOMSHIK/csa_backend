package org.example.csa_backend.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private Claude claude = new Claude();
    private Dalle dalle = new Dalle();
    private Elevenlabs elevenlabs = new Elevenlabs();

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
