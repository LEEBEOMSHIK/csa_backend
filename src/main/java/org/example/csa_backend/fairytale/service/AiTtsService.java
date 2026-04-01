package org.example.csa_backend.fairytale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.config.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTtsService {

    private final AiProperties aiProperties;

    public byte[] generateTts(String text, String voiceType, String language) {
        String apiKey = aiProperties.getElevenlabs().getApiKey();
        String baseUrl = aiProperties.getElevenlabs().getBaseUrl();
        String voiceId = aiProperties.getElevenlabs().getVoiceIds()
                .getOrDefault(voiceType, aiProperties.getElevenlabs().getVoiceIds()
                        .getOrDefault("dad", ""));

        if (voiceId.isBlank()) {
            log.warn("ElevenLabs voice ID가 설정되지 않았습니다. voiceType={}", voiceType);
            return null;
        }

        Map<String, Object> requestBody = Map.of(
                "text", text,
                "model_id", "eleven_multilingual_v2",
                "voice_settings", Map.of(
                        "stability", 0.5,
                        "similarity_boost", 0.75
                )
        );

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("xi-api-key", apiKey)
                    .defaultHeader("content-type", "application/json")
                    .defaultHeader("Accept", "audio/mpeg")
                    .build();

            return client.post()
                    .uri("/v1/text-to-speech/" + voiceId)
                    .body(requestBody)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("ElevenLabs TTS 생성 실패: {}", e.getMessage(), e);
            return null;
        }
    }
}
