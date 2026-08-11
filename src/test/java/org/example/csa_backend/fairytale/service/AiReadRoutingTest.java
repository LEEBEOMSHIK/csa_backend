package org.example.csa_backend.fairytale.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.fairytale.AiFairytalePageRepository;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.example.csa_backend.fairytale.CanonicalAiReadRepository;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class AiReadRoutingTest {

    private final AiFairytaleRepository legacyRepository = mock(AiFairytaleRepository.class);
    private final AiFairytalePageRepository pageRepository = mock(AiFairytalePageRepository.class);
    private final LegacyShadowReadObserver shadowObserver = mock(LegacyShadowReadObserver.class);
    private final CanonicalAiReadRepository canonicalRepository = mock(CanonicalAiReadRepository.class);
    private final ContentMigrationControlRepository controlRepository =
        mock(ContentMigrationControlRepository.class);
    private ContentMigrationControl control;
    private AiFairytaleService service;

    @BeforeEach
    void setUp() {
        control = control(MigrationState.OPEN, ContentSource.LEGACY, 40L);
        when(controlRepository.getSingleton()).thenReturn(control);
        service = service();
    }

    @Test
    void canonicalSourceRoutesAllAiReadsWithoutLegacyContentTableReads() {
        ReflectionTestUtils.setField(control, "readSource", ContentSource.CANONICAL);
        MyFairytaleDto summary = new MyFairytaleDto(
            9L, "canonical", "slide", "COMPLETED", "ko", true,
            "canonical-image", 1, LocalDateTime.parse("2026-08-11T03:00:00"), 42L
        );
        FairytaleGenerateResponse slides = new FairytaleGenerateResponse(
            9L, "canonical", "ko", "mom",
            List.of(new FairytaleGenerateResponse.PageDto(
                1, "canonical body", "canonical-image", "canonical-audio")),
            null
        );
        when(canonicalRepository.getMyFairytales(42L)).thenReturn(List.of(summary));
        when(canonicalRepository.getSharedFairytales()).thenReturn(List.of(summary));
        when(canonicalRepository.getMyFairytaleSlides(42L, 9L)).thenReturn(slides);
        when(canonicalRepository.getSharedFairytaleSlides(9L)).thenReturn(slides);

        assertThat(service.getMyFairytales(42L)).containsExactly(summary);
        assertThat(service.getSharedFairytales()).containsExactly(summary);
        assertThat(service.getMyFairytaleSlides(42L, 9L)).isSameAs(slides);
        assertThat(service.getSharedFairytaleSlides(9L)).isSameAs(slides);

        verify(controlRepository, times(4)).getSingleton();
        verifyNoInteractions(legacyRepository, shadowObserver);
    }

    @Test
    void rollbackRoutesAllAiReadsToLegacyWithoutCanonicalReads() {
        ReflectionTestUtils.setField(control, "state", MigrationState.CUTOVER_PENDING);
        ReflectionTestUtils.setField(control, "readSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(control, "writeSource", ContentSource.CANONICAL);
        ReflectionTestUtils.setField(control, "barrierEpoch", 41L);
        control.rollbackToLegacy(41L, Instant.parse("2026-08-12T00:00:00Z"));
        AiFairytale legacy = legacyFairytale();
        when(legacyRepository.findByOwnerIdOrderByIdDesc(42L)).thenReturn(List.of(legacy));
        when(legacyRepository.findBySharedAndStatusOrderByIdDesc("Y", "COMPLETED"))
            .thenReturn(List.of(legacy));
        when(legacyRepository.findById(9L)).thenReturn(java.util.Optional.of(legacy));

        assertThat(service.getMyFairytales(42L)).singleElement()
            .extracting(MyFairytaleDto::title).isEqualTo("legacy");
        assertThat(service.getSharedFairytales()).singleElement()
            .extracting(MyFairytaleDto::title).isEqualTo("legacy");
        assertThat(service.getMyFairytaleSlides(42L, 9L).pages()).singleElement()
            .extracting(FairytaleGenerateResponse.PageDto::imageUrl).isEqualTo("legacy-image");
        assertThat(service.getSharedFairytaleSlides(9L).pages()).singleElement()
            .extracting(FairytaleGenerateResponse.PageDto::audioUrl).isEqualTo("legacy-audio");

        verify(controlRepository, times(4)).getSingleton();
        verifyNoInteractions(canonicalRepository);
    }

    private AiFairytaleService service() {
        return new AiFairytaleService(
            legacyRepository,
            pageRepository,
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class),
            mock(FileStorageService.class),
            mock(AiVideoAssemblyService.class),
            mock(UserRepository.class),
            new AiGenerationProperties(),
            shadowObserver,
            new ContentWriteActivityTracker(mock(ContentMigrationGate.class)),
            new ContentReadRouter(controlRepository),
            canonicalRepository
        );
    }

    private AiFairytale legacyFairytale() {
        AiFairytale fairytale = new AiFairytale(
            "legacy", "settings", "genre", "theme", 1, "mom", "ko", "slide", "COMPLETED"
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

    private ContentMigrationControl control(MigrationState state, ContentSource source, long epoch) {
        ContentMigrationControl value = new ContentMigrationControl();
        ReflectionTestUtils.setField(value, "singletonId", (short) 1);
        ReflectionTestUtils.setField(value, "state", state);
        ReflectionTestUtils.setField(value, "readSource", source);
        ReflectionTestUtils.setField(value, "writeSource", source);
        ReflectionTestUtils.setField(value, "barrierEpoch", epoch);
        ReflectionTestUtils.setField(value, "updatedAt", Instant.parse("2026-08-11T00:00:00Z"));
        return value;
    }
}
