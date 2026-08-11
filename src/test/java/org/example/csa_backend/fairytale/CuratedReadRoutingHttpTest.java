package org.example.csa_backend.fairytale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.example.csa_backend.fairytale.dto.FairytaleDetailDto;
import org.example.csa_backend.fairytale.dto.FairytaleDto;
import org.example.csa_backend.fairytale.dto.HomePageDto;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentReadRouter;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CuratedReadRoutingHttpTest {

    @Test
    void publicCuratedHttpReadsCanonicalAfterCutoverAndLegacyAfterRollback() throws Exception {
        CategoryRepository categories = mock(CategoryRepository.class);
        FairytaleRepository fairytales = mock(FairytaleRepository.class);
        FairytaleDetailRepository details = mock(FairytaleDetailRepository.class);
        CuratedFairytalePageRepository pages = mock(CuratedFairytalePageRepository.class);
        LegacyStoryLinkRepository links = mock(LegacyStoryLinkRepository.class);
        CanonicalCuratedReadRepository canonical = mock(CanonicalCuratedReadRepository.class);
        ContentMigrationControlRepository controls = mock(ContentMigrationControlRepository.class);
        ContentMigrationControl control = control();
        when(controls.getSingleton()).thenReturn(control);
        FairytaleService service = new FairytaleService(
            categories,
            fairytales,
            details,
            pages,
            links,
            mock(LegacyShadowReadObserver.class),
            new ContentReadRouter(controls),
            canonical
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FairytaleController(
            service,
            mock(AiFairytaleService.class),
            mock(FairytaleDownloadLogService.class)
        )).build();

        FairytaleDto canonicalSummary = new FairytaleDto(
            7L, "canonical-home", "正本", "description", "説明", 5.0, "#123456", null,
            List.of("forest"), true, 70L
        );
        when(canonical.getHomePage(null))
            .thenReturn(new HomePageDto(List.of(canonicalSummary), List.of(), List.of()));
        when(canonical.getFairytaleDetail(7L)).thenReturn(new FairytaleDetailDto(
            "canonical-author", "正本作者", "5-7", 4, 1, "body", "本文", true,
            "LOCAL_OVERLAY", "canonical-v1"
        ));

        mockMvc.perform(get("/fairytale/home"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.themes[0].title").value("canonical-home"));
        mockMvc.perform(get("/fairytale/7/detail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorKo").value("canonical-author"));

        control.rollbackToLegacy(41L, Instant.parse("2026-08-12T00:00:00Z"));
        Fairytale legacy = new Fairytale(
            "legacy-home", "旧", "description", "説明", 4.0, "#FFFFFF", null,
            "Y", "N", "N"
        );
        ReflectionTestUtils.setField(legacy, "id", 7L);
        FairytaleDetail legacyDetail = new FairytaleDetail(
            legacy, "legacy-author", "旧作者", "3-5", 3, 1, "legacy body", "旧本文"
        );
        when(fairytales.findThemes(null)).thenReturn(List.of(legacy));
        when(fairytales.findNewItems(null)).thenReturn(List.of());
        when(fairytales.findRecommended(null)).thenReturn(List.of());
        when(links.findPublishedStoryId("CURATED", 7L)).thenReturn(Optional.empty());
        when(details.findActiveByFairytaleId(7L)).thenReturn(Optional.of(legacyDetail));

        mockMvc.perform(get("/fairytale/home"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.themes[0].title").value("legacy-home"));
        mockMvc.perform(get("/fairytale/7/detail"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorKo").value("legacy-author"));
    }

    private ContentMigrationControl control() {
        ContentMigrationControl value = new ContentMigrationControl();
        ReflectionTestUtils.setField(value, "singletonId", (short) 1);
        ReflectionTestUtils.setField(value, "state", MigrationState.CUTOVER_PENDING);
        ReflectionTestUtils.setField(value, "readSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(value, "writeSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(value, "barrierEpoch", 41L);
        ReflectionTestUtils.setField(value, "updatedAt", Instant.parse("2026-08-11T00:00:00Z"));
        return value;
    }
}
