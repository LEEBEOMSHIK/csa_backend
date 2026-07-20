package org.example.csa_backend.fairytale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class CuratedSlidesIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FairytaleDetailRepository fairytaleDetailRepository;

    @Autowired
    private CuratedFairytalePageRepository curatedFairytalePageRepository;

    @Test
    @Transactional
    void curatedSlidesIsPublicAndUsesTheManifestJsonContract() throws Exception {
        FairytaleDetail detail = fairytaleDetailRepository.findAll().get(0);
        ReflectionTestUtils.setField(detail, "contentVersion", "2026-07-20.2");
        fairytaleDetailRepository.saveAndFlush(detail);
        curatedFairytalePageRepository.saveAndFlush(new CuratedFairytalePage(
                detail.getFairytale(), 1, "https://cdn.example/curated/2026-07-20.1/page-1.png", "이전 한국어", "以前の日本語",
                "2026-07-20.1", null, null, null, null, null, null, null));
        curatedFairytalePageRepository.saveAndFlush(new CuratedFairytalePage(
                detail.getFairytale(), 1, "https://cdn.example/curated/2026-07-20.2/page-1.png", "한국어", "日本語",
                "2026-07-20.2", null, null, null, null, null, null, null));

        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        mockMvc.perform(get("/fairytale/{id}/curated-slides", detail.getFairytale().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fairytaleId").value(detail.getFairytale().getId()))
                .andExpect(jsonPath("$.contentVersion").value("2026-07-20.2"))
                .andExpect(jsonPath("$.characterSupported").value(false))
                .andExpect(jsonPath("$.characterRenderMode").value("LOCAL_OVERLAY"))
                .andExpect(jsonPath("$.pages[0].pageIndex").value(1))
                .andExpect(jsonPath("$.pages[0].imageUrl").value("https://cdn.example/curated/2026-07-20.2/page-1.png"))
                .andExpect(jsonPath("$.pages[0].text.ko").value("한국어"))
                .andExpect(jsonPath("$.pages[0].text.ja").value("日本語"))
                .andExpect(jsonPath("$.pages[0].characterPlacement").value(nullValue()));
    }

    @Test
    void curatedSlidesReturnsPublicNotFoundEnvelopeWhenNoManifestExists() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        mockMvc.perform(get("/fairytale/999999/curated-slides"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
