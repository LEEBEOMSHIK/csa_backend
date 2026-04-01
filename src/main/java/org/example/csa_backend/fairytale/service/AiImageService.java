package org.example.csa_backend.fairytale.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.config.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public byte[] generateImage(String pageText, boolean useCharacter, String language) {
        String apiKey = aiProperties.getDalle().getApiKey();
        String baseUrl = aiProperties.getDalle().getBaseUrl();

        String prompt = buildImagePrompt(pageText, useCharacter, language);

        Map<String, Object> requestBody = Map.of(
                "model", "dall-e-3",
                "prompt", prompt,
                "n", 1,
                "size", "1024x1024",
                "quality", "standard"
        );

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("Authorization", "Bearer " + apiKey)
                    .defaultHeader("content-type", "application/json")
                    .build();

            String responseBody = client.post()
                    .uri("/v1/images/generations")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(responseBody);
            String imageUrl = root.path("data").get(0).path("url").asText();

            return downloadBytes(imageUrl);
        } catch (Exception e) {
            log.error("DALL-E 이미지 생성 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    private String buildImagePrompt(String pageText, boolean useCharacter, String language) {
        String stylePrefix = "Children's picture book illustration, soft watercolor style, warm colors, cute and friendly, safe for children. ";
        String characterNote = useCharacter ? "Main character is an adorable child. " : "";
        String content = "Scene: " + pageText;
        return stylePrefix + characterNote + content;
    }

    private byte[] downloadBytes(String url) throws Exception {
        RestClient client = RestClient.create();
        return client.get()
                .uri(URI.create(url))
                .retrieve()
                .body(byte[].class);
    }
}
