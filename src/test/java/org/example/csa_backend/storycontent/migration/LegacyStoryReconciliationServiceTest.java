package org.example.csa_backend.storycontent.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.LongStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.example.csa_backend.storycontent.ContentVersion;
import org.example.csa_backend.storycontent.ContentVersionRepository;
import org.example.csa_backend.storycontent.ContentVersionStatus;
import org.example.csa_backend.storycontent.LegacyShadowMismatchRepository;
import org.example.csa_backend.storycontent.LegacyStoryLink;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.Story;
import org.example.csa_backend.storycontent.StoryRepository;
import org.example.csa_backend.storycontent.StoryVisibility;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class LegacyStoryReconciliationServiceTest {

    @Test
    void completeReportRequiresEveryLegacyTypeIdAndCurrentSourceHash() {
        LegacyStoryImportService importer = mock(LegacyStoryImportService.class);
        LegacyStoryLinkRepository links = mock(LegacyStoryLinkRepository.class);
        stubSources(importer, LegacyType.CURATED, List.of(source(7L, hash("a"))));
        stubSources(importer, LegacyType.AI, List.of(source(7L, hash("b"))));
        stubLinks(links, LegacyType.CURATED, List.of(link(LegacyType.CURATED, 7L, hash("a"))));
        stubLinks(links, LegacyType.AI, List.of(link(LegacyType.AI, 7L, hash("b"))));
        LegacyShadowMismatchRepository mismatches = mock(LegacyShadowMismatchRepository.class);
        when(mismatches.countByResolvedAtIsNull()).thenReturn(0L);
        LegacyStoryReconciliationService service = new LegacyStoryReconciliationService(
            importer, links, mismatches, new ContractChecksum());

        ReconciliationReport report = service.reconcileAll();

        assertThat(report.complete()).isTrue();
        assertThat(report.legacyCount()).isEqualTo(2);
        assertThat(report.linkedCount()).isEqualTo(2);
        assertThat(report.missingLinks()).isEmpty();
        assertThat(report.hashMismatches()).isEmpty();
        assertThat(report.checksum()).hasSize(64);
    }

    @Test
    void openShadowMismatchBlocksReconciliationBeforeCutover() {
        LegacyShadowMismatchRepository mismatches = mock(LegacyShadowMismatchRepository.class);
        when(mismatches.countByResolvedAtIsNull()).thenReturn(1L);
        LegacyStoryReconciliationService service = new LegacyStoryReconciliationService(
            mock(LegacyStoryImportService.class),
            mock(LegacyStoryLinkRepository.class),
            mismatches,
            new ContractChecksum()
        );

        assertThatThrownBy(service::reconcileAll)
            .isInstanceOfSatisfying(
                LegacyImportException.class,
                exception -> assertThat(exception.getCode()).isEqualTo("OPEN_SHADOW_MISMATCH")
            );
    }

    @Test
    void idAndHashDriftFailFullReconciliation() {
        LegacyStoryImportService importer = mock(LegacyStoryImportService.class);
        LegacyStoryLinkRepository links = mock(LegacyStoryLinkRepository.class);
        stubSources(importer, LegacyType.CURATED, List.of(
            source(7L, hash("expected-seven")),
            source(8L, hash("expected-eight"))
        ));
        stubSources(importer, LegacyType.AI, List.of());
        stubLinks(links, LegacyType.CURATED, List.of(
            link(LegacyType.CURATED, 7L, hash("wrong-seven"))
        ));
        stubLinks(links, LegacyType.AI, List.of());
        LegacyShadowMismatchRepository mismatches = mock(LegacyShadowMismatchRepository.class);
        when(mismatches.countByResolvedAtIsNull()).thenReturn(0L);
        LegacyStoryReconciliationService service = new LegacyStoryReconciliationService(
            importer, links, mismatches, new ContractChecksum());

        assertThatThrownBy(service::reconcileAll)
            .isInstanceOfSatisfying(
                LegacyImportException.class,
                exception -> {
                    assertThat(exception.getCode()).isEqualTo("LEGACY_RECONCILIATION_FAILED");
                    assertThat(exception.getMessage())
                        .contains("missing=1", "unexpected=0", "hash=1");
                }
            );
    }

    @Test
    void hardDeletedLegacyRowArchivesCanonicalStoryAndVersion() {
        LegacyStoryImportService importer = mock(LegacyStoryImportService.class);
        LegacyStoryLinkRepository links = mock(LegacyStoryLinkRepository.class);
        stubSources(importer, LegacyType.CURATED, List.of());
        stubSources(importer, LegacyType.AI, List.of());
        LegacyStoryLink tombstone = link(LegacyType.CURATED, 9L, hash("deleted-nine"));
        ReflectionTestUtils.setField(tombstone, "storyId", 90L);
        ReflectionTestUtils.setField(tombstone, "contentVersionId", 900L);
        stubLinks(links, LegacyType.CURATED, List.of(tombstone));
        stubLinks(links, LegacyType.AI, List.of());
        LegacyShadowMismatchRepository mismatches = mock(LegacyShadowMismatchRepository.class);
        when(mismatches.countByResolvedAtIsNull()).thenReturn(0L);
        Story story = new Story();
        ReflectionTestUtils.setField(story, "id", 90L);
        ReflectionTestUtils.setField(story, "visibility", StoryVisibility.PUBLISHED);
        ContentVersion version = new ContentVersion();
        ReflectionTestUtils.setField(version, "id", 900L);
        ReflectionTestUtils.setField(version, "storyId", 90L);
        ReflectionTestUtils.setField(version, "status", ContentVersionStatus.PUBLISHED);
        StoryRepository stories = mock(StoryRepository.class);
        ContentVersionRepository versions = mock(ContentVersionRepository.class);
        when(stories.findByIdForUpdate(90L)).thenReturn(Optional.of(story));
        when(versions.findByStoryIdOrderByVersionNoDesc(90L)).thenReturn(List.of(version));
        Instant now = Instant.parse("2026-08-11T04:00:00Z");
        LegacyStoryReconciliationService service = new LegacyStoryReconciliationService(
            importer,
            links,
            mismatches,
            new ContractChecksum(),
            stories,
            versions,
            Clock.fixed(now, ZoneOffset.UTC)
        );

        ReconciliationReport report = service.reconcileAll();

        assertThat(report.complete()).isTrue();
        assertThat(report.unexpectedLinks()).isEmpty();
        assertThat(report.linkedCount()).isZero();
        assertThat(story.getVisibility()).isEqualTo(StoryVisibility.ARCHIVED);
        assertThat(story.getArchivedAt()).isEqualTo(now);
        assertThat(version.getStatus()).isEqualTo(ContentVersionStatus.ARCHIVED);
    }

    @Test
    void reconciliationKeysetPagesSourceAndLinksWithoutUnboundedFindAll() {
        LegacyStoryImportService importer = mock(LegacyStoryImportService.class);
        LegacyStoryLinkRepository links = mock(LegacyStoryLinkRepository.class);
        List<LegacySourceHash> sources = LongStream.rangeClosed(1, 1_001)
            .mapToObj(id -> source(id, hash("source-" + id)))
            .toList();
        List<LegacyStoryLink> linked = sources.stream()
            .map(source -> link(LegacyType.CURATED, source.legacyId(), source.sourceHash()))
            .toList();
        stubSources(importer, LegacyType.CURATED, sources);
        stubSources(importer, LegacyType.AI, List.of());
        stubLinks(links, LegacyType.CURATED, linked);
        stubLinks(links, LegacyType.AI, List.of());
        LegacyShadowMismatchRepository mismatches = mock(LegacyShadowMismatchRepository.class);
        when(mismatches.countByResolvedAtIsNull()).thenReturn(0L);
        LegacyStoryReconciliationService service = new LegacyStoryReconciliationService(
            importer, links, mismatches, new ContractChecksum());

        ReconciliationReport first = service.reconcileAll();
        ReconciliationReport replay = service.reconcileAll();

        assertThat(first.complete()).isTrue();
        assertThat(first.legacyCount()).isEqualTo(1_001);
        assertThat(first.linkedCount()).isEqualTo(1_001);
        assertThat(replay.checksum()).isEqualTo(first.checksum());
        verify(importer, atLeastOnce()).sourceHashPage(LegacyType.CURATED, 1_000L, 1_000);
        verify(links, never()).findAll();
        ArgumentCaptor<Pageable> pageables = ArgumentCaptor.forClass(Pageable.class);
        verify(links, atLeastOnce()).findByLegacyTypeAndLegacyIdGreaterThanOrderByLegacyIdAsc(
            eq(LegacyType.CURATED), anyLong(), pageables.capture());
        assertThat(pageables.getAllValues())
            .allSatisfy(pageable -> assertThat(pageable.getPageSize()).isLessThanOrEqualTo(1_000));
    }

    private void stubSources(
        LegacyStoryImportService importer,
        LegacyType type,
        List<LegacySourceHash> sources
    ) {
        when(importer.sourceHashPage(eq(type), anyLong(), eq(1_000))).thenAnswer(invocation -> {
            long afterLegacyId = invocation.getArgument(1);
            return sources.stream()
                .filter(source -> source.legacyId() > afterLegacyId)
                .limit(1_000)
                .toList();
        });
    }

    private void stubLinks(
        LegacyStoryLinkRepository repository,
        LegacyType type,
        List<LegacyStoryLink> links
    ) {
        when(repository.findByLegacyTypeAndLegacyIdGreaterThanOrderByLegacyIdAsc(
            eq(type), anyLong(), any(Pageable.class))).thenAnswer(invocation -> {
                long afterLegacyId = invocation.getArgument(1);
                Pageable pageable = invocation.getArgument(2);
                return links.stream()
                    .filter(link -> link.getLegacyId() > afterLegacyId)
                    .limit(pageable.getPageSize())
                    .toList();
            });
    }

    private LegacySourceHash source(long id, String hash) {
        return new LegacySourceHash(id, hash);
    }

    private LegacyStoryLink link(LegacyType type, long id, String hash) {
        LegacyStoryLink link = new LegacyStoryLink();
        ReflectionTestUtils.setField(link, "legacyType", type);
        ReflectionTestUtils.setField(link, "legacyId", id);
        ReflectionTestUtils.setField(link, "sourceHash", hash);
        return link;
    }

    private String hash(String value) {
        return new ContractChecksum().ofParts(List.of(value));
    }
}
