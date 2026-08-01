package org.example.csa_backend.fairytale.service;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.fairytale.AiFairytalePageRepository;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateRequest;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AiFairytaleServiceTest {

    private final AiFairytaleRepository aiFairytaleRepository = mock(AiFairytaleRepository.class);
    private final AiFairytalePageRepository aiFairytalePageRepository = mock(AiFairytalePageRepository.class);
    private final FileStorageService fileStorageService = mock(FileStorageService.class);
    private final AiVideoAssemblyService aiVideoAssemblyService = mock(AiVideoAssemblyService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final AiGenerationProperties aiGenerationProperties = enabledAiGenerationProperties();
    private final AiFairytaleService service = new AiFairytaleService(
            aiFairytaleRepository,
            aiFairytalePageRepository,
            unavailableProvider(),
            unavailableProvider(),
            unavailableProvider(),
            fileStorageService,
            aiVideoAssemblyService,
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
                aiVideoAssemblyService,
                userRepository,
                disabledProperties
        );

        assertThatThrownBy(() -> disabledService.generate(null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.FEATURE_DISABLED);

        verifyNoInteractions(aiFairytaleRepository, aiFairytalePageRepository, fileStorageService,
                aiVideoAssemblyService, userRepository);
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

    @Test
    void getMyFairytaleSlidesServesFailedVideoFormatWithPagesAsSlideFallback() {
        AiFairytale fairytale = videoFairytale(7L, 11L, "FAILED");
        fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                1,
                "영상 조립 전 페이지",
                "https://cdn.example.com/page1.png",
                "https://cdn.example.com/page1.mp3"
        ));
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        FairytaleGenerateResponse response = service.getMyFairytaleSlides(11L, 7L);

        assertThat(response.id()).isEqualTo(7L);
        assertThat(response.videoUrl()).isNull();
        assertThat(response.pages()).hasSize(1);
        assertThat(response.pages().get(0).text()).isEqualTo("영상 조립 전 페이지");
    }

    @Test
    void getMyFairytaleSlidesRejectsFailedSlideFormatEvenWithPages() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        fairytale.updateStatus("FAILED");
        fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                1,
                "다른 이유로 실패한 페이지",
                "https://cdn.example.com/page1.png",
                "https://cdn.example.com/page1.mp3"
        ));
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        assertThatThrownBy(() -> service.getMyFairytaleSlides(11L, 7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void getSharedFairytaleSlidesServesFailedVideoFormatWithPagesAsSlideFallback() {
        AiFairytale fairytale = videoFairytale(7L, 11L, "FAILED");
        fairytale.updateShared(true);
        fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                1,
                "공유된 영상 조립 전 페이지",
                "https://cdn.example.com/shared.png",
                "https://cdn.example.com/shared.mp3"
        ));
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        FairytaleGenerateResponse response = service.getSharedFairytaleSlides(7L);

        assertThat(response.videoUrl()).isNull();
        assertThat(response.pages()).hasSize(1);
    }

    @Test
    void getSharedFairytaleSlidesRejectsFailedSlideFormatEvenWithPages() {
        AiFairytale fairytale = completedFairytale(7L, 11L);
        fairytale.updateShared(true);
        fairytale.updateStatus("FAILED");
        fairytale.getPages().add(new AiFairytalePage(
                fairytale,
                1,
                "다른 이유로 실패한 공유 페이지",
                "https://cdn.example.com/shared.png",
                "https://cdn.example.com/shared.mp3"
        ));
        when(aiFairytaleRepository.findById(7L)).thenReturn(Optional.of(fairytale));

        assertThatThrownBy(() -> service.getSharedFairytaleSlides(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_STATE);
    }

    @Test
    void generateVideoFormatAssemblesVideoAndCompletesWithUrl() {
        AiTextService aiTextService = mock(AiTextService.class);
        AiImageService aiImageService = mock(AiImageService.class);
        AiTtsService aiTtsService = mock(AiTtsService.class);
        AiFairytaleService videoService = new AiFairytaleService(
                aiFairytaleRepository,
                aiFairytalePageRepository,
                providerOf(aiTextService),
                providerOf(aiImageService),
                providerOf(aiTtsService),
                fileStorageService,
                aiVideoAssemblyService,
                userRepository,
                aiGenerationProperties
        );

        byte[] imageBytes = {1, 2, 3};
        byte[] audioBytes = {4, 5};
        when(aiTextService.generate(any(), anyString(), anyString(), anyInt(), anyBoolean(), anyString()))
                .thenReturn(new AiTextService.GeneratedFairytale(
                        "영상 동화",
                        List.of(new AiTextService.GeneratedPage(1, "첫 페이지"))));
        when(aiImageService.generateImage(anyString(), anyBoolean(), anyString())).thenReturn(imageBytes);
        when(aiTtsService.generateTts(anyString(), anyString(), anyString())).thenReturn(audioBytes);
        when(fileStorageService.saveImage(any(), anyInt(), any())).thenReturn("https://cdn.example.com/page_1.png");
        when(fileStorageService.saveAudio(any(), anyInt(), anyString(), anyString(), any()))
                .thenReturn("https://cdn.example.com/page_1.mp3");
        when(aiFairytalePageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        byte[] videoBytes = {9, 9, 9};
        when(aiVideoAssemblyService.assembleVideo(any(), any())).thenReturn(videoBytes);
        when(fileStorageService.saveVideo(any(), any())).thenReturn("https://cdn.example.com/video.mp4");

        FairytaleGenerateResponse response = videoService.generate(videoRequest(), 11L);

        assertThat(response.videoUrl()).isEqualTo("https://cdn.example.com/video.mp4");
        assertThat(response.pages()).hasSize(1);

        ArgumentCaptor<AiFairytale> saved = ArgumentCaptor.forClass(AiFairytale.class);
        verify(aiFairytaleRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getValue().getVideoUrl()).isEqualTo("https://cdn.example.com/video.mp4");

        ArgumentCaptor<List<AiVideoAssemblyService.PageMedia>> pageMediaCaptor = ArgumentCaptor.forClass(List.class);
        verify(aiVideoAssemblyService).assembleVideo(any(), pageMediaCaptor.capture());
        assertThat(pageMediaCaptor.getValue()).hasSize(1);
        assertThat(pageMediaCaptor.getValue().get(0).imageData()).isEqualTo(imageBytes);
        assertThat(pageMediaCaptor.getValue().get(0).audioData()).isEqualTo(audioBytes);
    }

    @Test
    void generateVideoFormatKeepsPagesAndMarksFailedWhenAssemblyThrows() {
        AiTextService aiTextService = mock(AiTextService.class);
        AiImageService aiImageService = mock(AiImageService.class);
        AiTtsService aiTtsService = mock(AiTtsService.class);
        AiFairytaleService videoService = new AiFairytaleService(
                aiFairytaleRepository,
                aiFairytalePageRepository,
                providerOf(aiTextService),
                providerOf(aiImageService),
                providerOf(aiTtsService),
                fileStorageService,
                aiVideoAssemblyService,
                userRepository,
                aiGenerationProperties
        );

        when(aiTextService.generate(any(), anyString(), anyString(), anyInt(), anyBoolean(), anyString()))
                .thenReturn(new AiTextService.GeneratedFairytale(
                        "영상 동화",
                        List.of(new AiTextService.GeneratedPage(1, "첫 페이지"))));
        when(aiImageService.generateImage(anyString(), anyBoolean(), anyString())).thenReturn(new byte[]{1});
        when(aiTtsService.generateTts(anyString(), anyString(), anyString())).thenReturn(new byte[]{2});
        when(fileStorageService.saveImage(any(), anyInt(), any())).thenReturn("https://cdn.example.com/page_1.png");
        when(fileStorageService.saveAudio(any(), anyInt(), anyString(), anyString(), any()))
                .thenReturn("https://cdn.example.com/page_1.mp3");
        when(aiFairytalePageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(aiVideoAssemblyService.assembleVideo(any(), any()))
                .thenThrow(new VideoAssemblyException("ffmpeg 실행 실패"));

        FairytaleGenerateResponse response = videoService.generate(videoRequest(), 11L);

        assertThat(response.videoUrl()).isNull();
        assertThat(response.pages()).hasSize(1);
        assertThat(response.pages().get(0).imageUrl()).isEqualTo("https://cdn.example.com/page_1.png");

        ArgumentCaptor<AiFairytale> saved = ArgumentCaptor.forClass(AiFairytale.class);
        verify(aiFairytaleRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(saved.getValue().getVideoUrl()).isNull();
        assertThat(saved.getValue().getPages()).hasSize(1);
        verify(fileStorageService, never()).saveVideo(any(), any());
    }

    @Test
    void generateSlideFormatNeverInvokesVideoAssembly() {
        AiTextService aiTextService = mock(AiTextService.class);
        AiImageService aiImageService = mock(AiImageService.class);
        AiTtsService aiTtsService = mock(AiTtsService.class);
        AiFairytaleService slideService = new AiFairytaleService(
                aiFairytaleRepository,
                aiFairytalePageRepository,
                providerOf(aiTextService),
                providerOf(aiImageService),
                providerOf(aiTtsService),
                fileStorageService,
                aiVideoAssemblyService,
                userRepository,
                aiGenerationProperties
        );

        when(aiTextService.generate(any(), anyString(), anyString(), anyInt(), anyBoolean(), anyString()))
                .thenReturn(new AiTextService.GeneratedFairytale(
                        "슬라이드 동화",
                        List.of(new AiTextService.GeneratedPage(1, "첫 페이지"))));
        when(aiImageService.generateImage(anyString(), anyBoolean(), anyString())).thenReturn(new byte[]{1});
        when(aiTtsService.generateTts(anyString(), anyString(), anyString())).thenReturn(new byte[]{2});
        when(fileStorageService.saveImage(any(), anyInt(), any())).thenReturn("https://cdn.example.com/page_1.png");
        when(fileStorageService.saveAudio(any(), anyInt(), anyString(), anyString(), any()))
                .thenReturn("https://cdn.example.com/page_1.mp3");
        when(aiFairytalePageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        FairytaleGenerateResponse response = slideService.generate(slideRequest(), 11L);

        assertThat(response.videoUrl()).isNull();
        verifyNoInteractions(aiVideoAssemblyService);
    }

    /// format은 클라이언트가 보낸 값을 그대로 저장해 왔고, 그래서 아직 설치되어 있는
    /// 구버전 앱이 보낸 "IMAGE" 같은 값이 컬럼에 남아 관리자 형식 분포에서 별개 항목으로
    /// 잡혔다. 저장 시점에 slide/video로 정규화되는지 고정한다.
    @Test
    void generateNormalizesUnknownFormatToSlide() {
        assertThat(savedFormatFor("IMAGE")).isEqualTo("slide");
        assertThat(savedFormatFor(null)).isEqualTo("slide");
        assertThat(savedFormatFor("  ")).isEqualTo("slide");
    }

    @Test
    void generateAcceptsVideoFormatRegardlessOfSpelling() {
        assertThat(savedFormatFor("video")).isEqualTo("video");
        assertThat(savedFormatFor("VIDEO")).isEqualTo("video");
        assertThat(savedFormatFor(" Video ")).isEqualTo("video");
    }

    /// 요청한 format으로 생성을 시도한 뒤, 저장된 엔티티에 실제로 들어간 값을 돌려준다.
    /// AI 호출은 스텁하지 않으므로 뒤에서 실패하지만, 정규화는 그 전에 끝나 있다.
    private String savedFormatFor(String requestedFormat) {
        AiFairytaleRepository repository = mock(AiFairytaleRepository.class);
        AiFairytaleService service = new AiFairytaleService(
                repository,
                aiFairytalePageRepository,
                providerOf(mock(AiTextService.class)),
                providerOf(mock(AiImageService.class)),
                providerOf(mock(AiTtsService.class)),
                fileStorageService,
                aiVideoAssemblyService,
                userRepository,
                aiGenerationProperties
        );

        try {
            service.generate(new FairytaleGenerateRequest(
                    List.of("adventure"), "classic", "courage", 1, false, "dad", "ko",
                    requestedFormat), 11L);
        } catch (RuntimeException ignored) {
            // 생성 파이프라인의 실패는 이 테스트의 관심사가 아니다.
        }

        ArgumentCaptor<AiFairytale> saved = ArgumentCaptor.forClass(AiFairytale.class);
        verify(repository, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().get(0).getFormat();
    }

    private FairytaleGenerateRequest videoRequest() {
        return new FairytaleGenerateRequest(
                List.of("adventure"), "classic", "courage", 1, false, "dad", "ko", "video");
    }

    private FairytaleGenerateRequest slideRequest() {
        return new FairytaleGenerateRequest(
                List.of("adventure"), "classic", "courage", 1, false, "dad", "ko", "slide");
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(instance);
        return provider;
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

    private AiFairytale videoFairytale(Long fairytaleId, Long ownerId, String status) {
        AiFairytale fairytale = new AiFairytale(
                "별빛 모험 (영상)",
                "adventure",
                "classic",
                "courage",
                3,
                "dad",
                "ko",
                "video",
                status
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
