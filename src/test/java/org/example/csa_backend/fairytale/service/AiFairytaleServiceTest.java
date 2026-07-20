package org.example.csa_backend.fairytale.service;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.fairytale.AiFairytalePageRepository;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiFairytaleServiceTest {

    private final AiFairytaleRepository aiFairytaleRepository = mock(AiFairytaleRepository.class);
    private final AiFairytalePageRepository aiFairytalePageRepository = mock(AiFairytalePageRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiGenerationProperties aiGenerationProperties = enabledAiGenerationProperties();
    private final AiFairytaleService service = new AiFairytaleService(
            aiFairytaleRepository,
            aiFairytalePageRepository,
            unavailableProvider(),
            unavailableProvider(),
            unavailableProvider(),
            fileStorageService,
            userRepository,
            aiGenerationProperties
    );

    @Test
    void generateRejectsRequestsBeforePersistingWhenFeatureIsDisabled() {
        AiGenerationProperties disabledProperties = new AiGenerationProperties();
        AiFairytaleService disabledService = new AiFairytaleService(
                aiFairytaleRepository,
                aiFairytalePageRepository,
                unavailableProvider(),
                unavailableProvider(),
                unavailableProvider(),
                fileStorageService,
                userRepository,
                disabledProperties
        );

        assertThatThrownBy(() -> disabledService.generate(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FEATURE_DISABLED);

        verifyNoInteractions(aiFairytaleRepository, aiFairytalePageRepository, fileStorageService, userRepository);
    }

    @Test
    void getMyFairytaleSlidesReturnsOwnedCompletedPages() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                1,
                "첫 페이지",
                "https://cdn.example.com/page1.png",
                "https://cdn.example.com/page1.mp3"
        ));
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        FairytaleGenerateResponse response = service.getMyFairytaleSlides(11L, 7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("별빛 모험");
        assertThat(response.language()).isEqualTo("ko");
        assertThat(response.voiceType()).isEqualTo("dad");
        assertThat(response.pages()).hasSize(1);
        assertThat(response.pages().get(0).text()).isEqualTo("첫 페이지");
    }

    @Test
    void getMyFairytaleSlidesRejectsOtherUsersFairytale() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        assertThatThrownBy(() -> service.getMyFairytaleSlides(99L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    void getMyFairytaleSlidesRejectsIncompleteFairytale() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        fairytale.updateStatus("GENERATING");
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        assertThatThrownBy(() -> service.getMyFairytaleSlides(11L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void getSharedFairytaleSlidesReturnsSharedCompletedPages() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        fairytale.updateShared(true);
        fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                1,
                "공유 페이지",
                "https://cdn.example.com/shared.png",
                "https://cdn.example.com/shared.mp3"
        ));
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        FairytaleGenerateResponse response = service.getSharedFairytaleSlides(7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.title()).isEqualTo("별빛 모험");
        assertThat(response.pages()).hasSize(1);
        assertThat(response.pages().get(0).text()).isEqualTo("공유 페이지");
    }

    @Test
    void getSharedFairytaleSlidesHidesPrivateFairytale() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        assertThatThrownBy(() -> service.getSharedFairytaleSlides(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void getSharedFairytaleSlidesRejectsIncompleteFairytale() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        fairytale.updateShared(true);
        fairytale.updateStatus("GENERATING");
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        assertThatThrownBy(() -> service.getSharedFairytaleSlides(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    private AiFairytale completedFairytale(Long fairytaleId, Long ownerId) {
        AiFairytale fairytale = new AiFairytale(
                "별빛 모험",
                "adventure",
                "classic",
                "courage",
                3,
                "dad",
                "ko",
                "slide",
                "COMPLETED"
        );
        ReflectionTestUtils.setField(fairytale, "id", fairytaleId);
        User owner = new User("owner@example.com", "password");
        ReflectionTestUtils.setField(owner, "id", ownerId);
        fairytale.assignOwner(owner);
        return fairytale;
    }

    private AiGenerationProperties enabledAiGenerationProperties() {
        AiGenerationProperties properties = new AiGenerationProperties();
        properties.setEnabled(true);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> unavailableProvider() {
        return (ObjectProvider<T>) mock(ObjectProvider.class);
    }
}
