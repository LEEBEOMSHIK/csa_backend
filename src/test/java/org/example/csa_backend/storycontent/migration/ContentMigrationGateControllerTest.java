package org.example.csa_backend.storycontent.migration;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.example.csa_backend.common.exception.GlobalExceptionHandler;
import org.example.csa_backend.fairytale.FairytaleController;
import org.example.csa_backend.fairytale.FairytaleDownloadLogService;
import org.example.csa_backend.fairytale.FairytaleService;
import org.example.csa_backend.fairytale.service.AiFairytaleService;
import org.example.csa_backend.storycontent.StoryContentExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ContentMigrationGateControllerTest {

    @Mock
    private FairytaleService fairytaleService;

    @Mock
    private AiFairytaleService aiFairytaleService;

    @Mock
    private FairytaleDownloadLogService downloadLogService;

    private MockMvc mockMvc;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("12");
        mockMvc = MockMvcBuilders.standaloneSetup(
                new FairytaleController(fairytaleService, aiFairytaleService, downloadLogService)
            )
            .setControllerAdvice(new GlobalExceptionHandler(), new StoryContentExceptionHandler())
            .build();
    }

    @Test
    void frozenLegacyMutationReturnsMigrationContract() throws Exception {
        when(aiFairytaleService.toggleShare(12L, 7L)).thenThrow(
            ContentMigrationException.serviceUnavailable("CONTENT_MIGRATION_FREEZE", 41L)
        );

        mockMvc.perform(post("/fairytale/7/share").principal(authentication))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("CONTENT_MIGRATION_FREEZE"))
            .andExpect(jsonPath("$.data.barrierEpoch").value(41));
    }

    @Test
    void frozenMigrationDoesNotBlockLegacyReads() throws Exception {
        when(aiFairytaleService.getMyFairytales(12L)).thenReturn(List.of());

        mockMvc.perform(get("/fairytale/my").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }
}
