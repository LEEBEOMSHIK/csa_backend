package org.example.csa_backend.fairytale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.example.csa_backend.config.AiProperties;
import org.example.csa_backend.fairytale.service.AiImageService;
import org.example.csa_backend.fairytale.service.AiTextService;
import org.example.csa_backend.fairytale.service.AiTtsService;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AiGenerationFeatureDisabledIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void generateReturnsFeatureDisabledWhenAiGenerationIsDisabled() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        mockMvc.perform(post("/fairytale/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "settings": ["adventure"],
                                  "genre": "classic",
                                  "theme": "courage",
                                  "chapterCount": 3,
                                  "useCharacter": false,
                                  "voiceType": "dad",
                                  "language": "ko",
                                  "format": "slide"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("FEATURE_DISABLED"));
    }

    @Test
    void disabledFeatureDoesNotCreateAiConfigurationOrClients() {
        assertThat(applicationContext.getBeansOfType(AiProperties.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AiTextService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AiImageService.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(AiTtsService.class)).isEmpty();
    }
}
