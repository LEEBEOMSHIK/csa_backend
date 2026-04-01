package org.example.csa_backend.fairytale.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.config.AiProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiTextService {

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public record GeneratedFairytale(String title, List<GeneratedPage> pages) {}
    public record GeneratedPage(int pageIndex, String text) {}

    public GeneratedFairytale generate(List<String> settings, String genre, String theme,
                                       int chapterCount, boolean useCharacter, String language) {
        String prompt = buildPrompt(settings, genre, theme, chapterCount, useCharacter, language);

        String apiKey = aiProperties.getClaude().getApiKey();
        String model = aiProperties.getClaude().getModel();
        String baseUrl = aiProperties.getClaude().getBaseUrl();

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", 4096,
                "messages", List.of(
                        Map.of("role", "user", "content", prompt)
                )
        );

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(baseUrl)
                    .defaultHeader("x-api-key", apiKey)
                    .defaultHeader("anthropic-version", "2023-06-01")
                    .defaultHeader("content-type", "application/json")
                    .build();

            String responseBody = client.post()
                    .uri("/v1/messages")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            return parseClaudeResponse(responseBody);
        } catch (Exception e) {
            log.error("Claude API 호출 실패: {}", e.getMessage(), e);
            throw new RuntimeException("동화 텍스트 생성에 실패했습니다.", e);
        }
    }

    private String buildPrompt(List<String> settings, String genre, String theme,
                                int chapterCount, boolean useCharacter, String language) {
        String settingsLabel = translateSettings(settings, language);
        String genreLabel = translateGenre(genre, language);
        String themeLabel = translateTheme(theme, language);
        String langLabel = "ko".equals(language) ? "한국어" : "日本語";

        StringBuilder sb = new StringBuilder();
        sb.append("당신은 어린이를 위한 동화 작가입니다. 다음 조건으로 어린이 동화를 JSON 형식으로 작성해주세요.\n\n");
        sb.append("# 필수 조건\n");
        sb.append("- 어린이용 동화 스타일로 제작합니다 (대상 연령: 3~8세)\n");
        sb.append("- 어린이가 이해하기 쉬운 단어와 짧은 문장을 사용합니다\n");
        sb.append("- 교육적이고 안전한 내용만 포함합니다\n");
        sb.append("- 각 페이지는 3~5문장 분량으로 작성합니다\n");

        if (chapterCount == 3) {
            sb.append("- 영아(3~5세) 대상이므로 의성어·의태어를 최대한 활용합니다 ");
            sb.append("(예: 퐁당퐁당, 반짝반짝, 콩닥콩닥, 깡충깡충, 쑥쑥, 뒤뚱뒤뚱, 촐랑촐랑)\n");
        }

        sb.append("\n# 동화 설정\n");
        sb.append("- 배경/세계관: ").append(settingsLabel).append("\n");
        sb.append("- 장르: ").append(genreLabel).append("\n");
        sb.append("- 이야기 주제: ").append(themeLabel).append("\n");
        sb.append("- 챕터(페이지) 수: ").append(chapterCount).append("개\n");
        sb.append("- 주인공: ").append(useCharacter ? "사용자 캐릭터(귀여운 어린이)" : "AI가 창의적으로 생성").append("\n");
        sb.append("- 출력 언어: ").append(langLabel).append("\n");
        sb.append("\n# 출력 형식\n");
        sb.append("반드시 아래 JSON만 출력하세요. 마크다운 코드블록, 설명 텍스트 등 다른 내용은 절대 포함하지 마세요.\n");
        sb.append("{\n");
        sb.append("  \"title\": \"동화 제목\",\n");
        sb.append("  \"pages\": [\n");
        sb.append("    { \"pageIndex\": 1, \"text\": \"첫 번째 페이지 내용\" },\n");
        sb.append("    { \"pageIndex\": 2, \"text\": \"두 번째 페이지 내용\" }\n");
        sb.append("  ]\n");
        sb.append("}");

        return sb.toString();
    }

    private GeneratedFairytale parseClaudeResponse(String responseBody) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("content").get(0).path("text").asText();

        text = text.strip();
        if (text.startsWith("```")) {
            int start = text.indexOf('\n') + 1;
            int end = text.lastIndexOf("```");
            if (end > start) {
                text = text.substring(start, end).strip();
            }
        }

        JsonNode json = objectMapper.readTree(text);
        String title = json.path("title").asText("동화");

        List<GeneratedPage> pages = new ArrayList<>();
        for (JsonNode page : json.path("pages")) {
            pages.add(new GeneratedPage(
                    page.path("pageIndex").asInt(),
                    page.path("text").asText()
            ));
        }

        return new GeneratedFairytale(title, pages);
    }

    private String translateSettings(List<String> settings, String lang) {
        List<String> labels = settings.stream().map(s -> translateSetting(s, lang)).toList();
        return String.join(", ", labels);
    }

    private String translateSetting(String key, String lang) {
        if ("ko".equals(lang)) {
            return switch (key) {
                case "adventure" -> "모험";
                case "family" -> "가족";
                case "fantasy" -> "판타지";
                case "friendship" -> "우정";
                case "animal" -> "동물";
                case "sea" -> "바다";
                case "space" -> "우주";
                case "magic" -> "마법";
                case "forest" -> "숲·자연";
                case "kingdom" -> "왕국·성";
                case "school" -> "학교";
                case "city" -> "도시·마을";
                default -> key;
            };
        } else {
            return switch (key) {
                case "adventure" -> "冒険";
                case "family" -> "家族";
                case "fantasy" -> "ファンタジー";
                case "friendship" -> "友情";
                case "animal" -> "どうぶつ";
                case "sea" -> "海";
                case "space" -> "宇宙";
                case "magic" -> "魔法";
                case "forest" -> "森・自然";
                case "kingdom" -> "王国・お城";
                case "school" -> "学校";
                case "city" -> "まち";
                default -> key;
            };
        }
    }

    private String translateGenre(String key, String lang) {
        if ("ko".equals(lang)) {
            return switch (key) {
                case "classic" -> "클래식 동화";
                case "folklore" -> "전래동화";
                case "comedy" -> "코미디·웃음";
                case "mystery" -> "미스터리";
                case "scifi" -> "SF·미래";
                case "musical" -> "뮤지컬·노래";
                case "quiz" -> "수수께끼·퀴즈";
                case "daily" -> "일상·생활동화";
                case "dream" -> "꿈·상상";
                case "horror" -> "공포·으스스";
                default -> key;
            };
        } else {
            return switch (key) {
                case "classic" -> "クラシック童話";
                case "folklore" -> "昔話・民話";
                case "comedy" -> "コメディ";
                case "mystery" -> "ミステリー";
                case "scifi" -> "SF・未来";
                case "musical" -> "ミュージカル";
                case "quiz" -> "なぞなぞ";
                case "daily" -> "生活童話";
                case "dream" -> "夢・空想";
                case "horror" -> "こわい話";
                default -> key;
            };
        }
    }

    private String translateTheme(String key, String lang) {
        if ("ko".equals(lang)) {
            return switch (key) {
                case "moral" -> "교훈·도덕";
                case "friendship" -> "우정·사이좋기";
                case "family_love" -> "가족 사랑";
                case "courage" -> "용기·도전";
                case "growth" -> "성장·자립";
                case "sharing" -> "나눔·배려";
                case "self_expression" -> "자기 표현";
                case "environment" -> "환경·자연 사랑";
                case "gratitude" -> "감사·고마움";
                case "problem_solving" -> "문제 해결";
                case "curiosity" -> "호기심·발견";
                case "forgiveness" -> "용서·화해";
                default -> key;
            };
        } else {
            return switch (key) {
                case "moral" -> "教訓・道徳";
                case "friendship" -> "友だち・なかよし";
                case "family_love" -> "家族愛";
                case "courage" -> "勇気・チャレンジ";
                case "growth" -> "成長・自立";
                case "sharing" -> "おもいやり";
                case "self_expression" -> "自己表現";
                case "environment" -> "自然・環境";
                case "gratitude" -> "感謝";
                case "problem_solving" -> "問題解決";
                case "curiosity" -> "好奇心・発見";
                case "forgiveness" -> "ゆるし・仲直り";
                default -> key;
            };
        }
    }
}
