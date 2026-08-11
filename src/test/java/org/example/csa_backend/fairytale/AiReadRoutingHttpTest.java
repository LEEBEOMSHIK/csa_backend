package org.example.csa_backend.fairytale;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.example.csa_backend.fairytale.service.AiImageService;
import org.example.csa_backend.fairytale.service.AiTextService;
import org.example.csa_backend.fairytale.service.AiTtsService;
import org.example.csa_backend.fairytale.service.AiVideoAssemblyService;
import org.example.csa_backend.fairytale.service.FileStorageService;
import org.example.csa_backend.storycontent.ContentMigrationControl;
import org.example.csa_backend.storycontent.ContentMigrationControlRepository;
import org.example.csa_backend.storycontent.ContentReadRouter;
import org.example.csa_backend.storycontent.ContentSource;
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.example.csa_backend.storycontent.MigrationState;
import org.example.csa_backend.storycontent.migration.ContentMigrationGate;
import org.example.csa_backend.storycontent.migration.ContentWriteActivityTracker;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiReadRoutingHttpTest {

    @Test
    void aiHttpReadsCanonicalAfterCutoverAndLegacyAfterRollback() throws Exception {
        AiFairytaleRepository legacyRepository = mock(AiFairytaleRepository.class);
        CanonicalAiReadRepository canonicalRepository = mock(CanonicalAiReadRepository.class);
        ContentMigrationControlRepository controls = mock(ContentMigrationControlRepository.class);
        ContentMigrationControl control = control();
        when(controls.getSingleton()).thenReturn(control);
        AiFairytaleService aiService = new AiFairytaleService(
            legacyRepository,
            mock(AiFairytalePageRepository.class),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            mock(FileStorageService.class),
            mock(AiVideoAssemblyService.class),
            mock(UserRepository.class),
            new AiGenerationProperties(),
            mock(LegacyShadowReadObserver.class),
            new ContentWriteActivityTracker(mock(ContentMigrationGate.class)),
            new ContentReadRouter(controls),
            canonicalRepository
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FairytaleController(
            mock(FairytaleService.class), aiService, mock(FairytaleDownloadLogService.class)
        )).build();
        MyFairytaleDto canonicalSummary = new MyFairytaleDto(
            9L, "canonical-ai", "slide", "COMPLETED", "ko", true,
            "canonical-image", 1, LocalDateTime.parse("2026-08-11T03:00:00"), 42L
        );
        FairytaleGenerateResponse canonicalSlides = new FairytaleGenerateResponse(
            9L, "canonical-ai", "ko", "mom",
            List.of(new FairytaleGenerateResponse.PageDto(
                1, "canonical body", "canonical-image", "canonical-audio")), null
        );
        when(canonicalRepository.getMyFairytales(42L)).thenReturn(List.of(canonicalSummary));
        when(canonicalRepository.getSharedFairytales()).thenReturn(List.of(canonicalSummary));
        when(canonicalRepository.getMyFairytaleSlides(42L, 9L)).thenReturn(canonicalSlides);
        when(canonicalRepository.getSharedFairytaleSlides(9L)).thenReturn(canonicalSlides);

        mockMvc.perform(get("/fairytale/my").with(user(42L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("canonical-ai"));
        mockMvc.perform(get("/fairytale/shared"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].thumbnailUrl").value("canonical-image"));
        mockMvc.perform(get("/fairytale/9/slides").with(user(42L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages[0].imageUrl").value("canonical-image"));
        mockMvc.perform(get("/fairytale/shared/9/slides"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages[0].audioUrl").value("canonical-audio"));

        control.rollbackToLegacy(41L, Instant.parse("2026-08-12T00:00:00Z"));
        AiFairytale legacy = legacyFairytale();
        when(legacyRepository.findByOwnerIdOrderByIdDesc(42L)).thenReturn(List.of(legacy));
        when(legacyRepository.findBySharedAndStatusOrderByIdDesc("Y", "COMPLETED"))
            .thenReturn(List.of(legacy));
        when(legacyRepository.findById(9L)).thenReturn(Optional.of(legacy));

        mockMvc.perform(get("/fairytale/my").with(user(42L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].title").value("legacy-ai"));
        mockMvc.perform(get("/fairytale/shared"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].thumbnailUrl").value("legacy-image"));
        mockMvc.perform(get("/fairytale/9/slides").with(user(42L)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages[0].imageUrl").value("legacy-image"));
        mockMvc.perform(get("/fairytale/shared/9/slides"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pages[0].audioUrl").value("legacy-audio"));
    }

    private RequestPostProcessor user(long userId) {
        return request -> {
            request.setUserPrincipal(new UsernamePasswordAuthenticationToken(
                Long.toString(userId), "n/a", List.of()
            ));
            return request;
        };
    }

    private AiFairytale legacyFairytale() {
        AiFairytale fairytale = new AiFairytale(
            "legacy-ai", "settings", "genre", "theme", 1, "mom", "ko", "slide", "COMPLETED"
        );
        ReflectionTestUtils.setField(fairytale, "id", 9L);
        User owner = new User("owner@example.com", "password");
        ReflectionTestUtils.setField(owner, "id", 42L);
        fairytale.assignOwner(owner);
        fairytale.updateShared(true);
        fairytale.getPages().add(new AiFairytalePage(
            fairytale, 1, "legacy body", "legacy-image", "legacy-audio"
        ));
        return fairytale;
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
