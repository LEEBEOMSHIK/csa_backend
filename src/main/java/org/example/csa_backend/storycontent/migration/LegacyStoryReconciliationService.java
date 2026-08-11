package org.example.csa_backend.storycontent.migration;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.example.csa_backend.storycontent.ContentVersion;
import org.example.csa_backend.storycontent.ContentVersionRepository;
import org.example.csa_backend.storycontent.LegacyShadowMismatchRepository;
import org.example.csa_backend.storycontent.LegacyStoryLink;
import org.example.csa_backend.storycontent.LegacyStoryLinkRepository;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.Story;
import org.example.csa_backend.storycontent.StoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LegacyStoryReconciliationService {

    private static final int PAGE_SIZE = 1_000;

    private final LegacyStoryImportService importer;
    private final LegacyStoryLinkRepository linkRepository;
    private final LegacyShadowMismatchRepository mismatchRepository;
    private final ContractChecksum checksum;
    private final StoryRepository storyRepository;
    private final ContentVersionRepository versionRepository;
    private final Clock clock;

    @Autowired
    public LegacyStoryReconciliationService(
        LegacyStoryImportService importer,
        LegacyStoryLinkRepository linkRepository,
        LegacyShadowMismatchRepository mismatchRepository,
        ContractChecksum checksum,
        StoryRepository storyRepository,
        ContentVersionRepository versionRepository
    ) {
        this(
            importer,
            linkRepository,
            mismatchRepository,
            checksum,
            storyRepository,
            versionRepository,
            Clock.systemUTC()
        );
    }

    LegacyStoryReconciliationService(
        LegacyStoryImportService importer,
        LegacyStoryLinkRepository linkRepository,
        LegacyShadowMismatchRepository mismatchRepository,
        ContractChecksum checksum
    ) {
        this(importer, linkRepository, mismatchRepository, checksum, null, null, Clock.systemUTC());
    }

    LegacyStoryReconciliationService(
        LegacyStoryImportService importer,
        LegacyStoryLinkRepository linkRepository,
        LegacyShadowMismatchRepository mismatchRepository,
        ContractChecksum checksum,
        StoryRepository storyRepository,
        ContentVersionRepository versionRepository,
        Clock clock
    ) {
        this.importer = importer;
        this.linkRepository = linkRepository;
        this.mismatchRepository = mismatchRepository;
        this.checksum = checksum;
        this.storyRepository = storyRepository;
        this.versionRepository = versionRepository;
        this.clock = clock;
    }

    @Transactional
    public ReconciliationReport reconcileAll() {
        long openMismatches = mismatchRepository.countByResolvedAtIsNull();
        if (openMismatches > 0) {
            throw new LegacyImportException("OPEN_SHADOW_MISMATCH", Long.toString(openMismatches));
        }

        List<String> missing = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        long legacyCount = 0;
        long linkedCount = 0;
        List<String> checksumParts = new ArrayList<>();
        checksumParts.add("LEGACY_RECONCILIATION_V2");
        for (LegacyType type : LegacyType.values()) {
            TypeReport typeReport = reconcileType(type, missing, unexpected, changed);
            legacyCount += typeReport.legacyCount();
            linkedCount += typeReport.linkedCount();
            checksumParts.add(type.name());
            checksumParts.add(typeReport.legacyChecksum());
            checksumParts.add(typeReport.linkedChecksum());
        }
        missing.sort(String::compareTo);
        unexpected.sort(String::compareTo);
        changed.sort(String::compareTo);

        String reconciliationChecksum = checksum.ofParts(checksumParts);
        boolean complete = missing.isEmpty() && unexpected.isEmpty() && changed.isEmpty();
        ReconciliationReport report = new ReconciliationReport(
            complete,
            Math.toIntExact(legacyCount),
            Math.toIntExact(linkedCount),
            missing,
            unexpected,
            changed,
            openMismatches,
            reconciliationChecksum
        );
        if (!complete) {
            throw new LegacyImportException(
                "LEGACY_RECONCILIATION_FAILED",
                "missing=" + missing.size() + ",unexpected=" + unexpected.size()
                    + ",hash=" + changed.size()
            );
        }
        return report;
    }

    private TypeReport reconcileType(
        LegacyType type,
        List<String> missing,
        List<String> unexpected,
        List<String> changed
    ) {
        SourceCursor sources = new SourceCursor(type);
        LinkCursor links = new LinkCursor(type);
        long legacyCount = 0;
        long linkedCount = 0;
        String legacyChecksum = checksum.ofParts(List.of("LEGACY_SOURCE_V1", type.name()));
        String linkedChecksum = checksum.ofParts(List.of("LEGACY_LINK_V1", type.name()));

        LegacySourceHash source = sources.current();
        LegacyStoryLink link = links.current();
        while (source != null || link != null) {
            if (link == null || (source != null && source.legacyId() < link.getLegacyId())) {
                SourceKey key = new SourceKey(type, source.legacyId());
                legacyChecksum = fold(legacyChecksum, key, source.sourceHash());
                legacyCount++;
                missing.add(key.value());
                sources.advance();
            } else if (source == null || link.getLegacyId() < source.legacyId()) {
                archiveTombstone(link);
                links.advance();
            } else {
                SourceKey key = new SourceKey(type, source.legacyId());
                String linkHash = link.getSourceHash().trim();
                legacyChecksum = fold(legacyChecksum, key, source.sourceHash());
                linkedChecksum = fold(linkedChecksum, key, linkHash);
                legacyCount++;
                linkedCount++;
                if (!source.sourceHash().equals(linkHash)) {
                    changed.add(key.value());
                }
                sources.advance();
                links.advance();
            }
            source = sources.current();
            link = links.current();
        }
        return new TypeReport(legacyCount, linkedCount, legacyChecksum, linkedChecksum);
    }

    private void archiveTombstone(LegacyStoryLink link) {
        if (storyRepository == null || versionRepository == null) {
            throw new LegacyImportException(
                "LEGACY_TOMBSTONE_ARCHIVE_UNAVAILABLE",
                link.getLegacyType() + ":" + link.getLegacyId()
            );
        }
        Long storyId = Objects.requireNonNull(link.getStoryId());
        Long linkedVersionId = Objects.requireNonNull(link.getContentVersionId());
        Story story = storyRepository.findByIdForUpdate(storyId)
            .orElseThrow(() -> tombstoneTargetMissing(link, "story"));
        List<ContentVersion> versions = versionRepository.findByStoryIdOrderByVersionNoDesc(storyId);
        if (versions.stream().noneMatch(version -> linkedVersionId.equals(version.getId()))) {
            throw tombstoneTargetMissing(link, "version");
        }
        versions.forEach(version -> version.archive(clock.instant()));
        story.archive(clock.instant());
    }

    private LegacyImportException tombstoneTargetMissing(LegacyStoryLink link, String target) {
        return new LegacyImportException(
            "LEGACY_TOMBSTONE_TARGET_MISSING",
            link.getLegacyType() + ":" + link.getLegacyId() + ":" + target
        );
    }

    private String fold(String previous, SourceKey key, String sourceHash) {
        return checksum.ofParts(List.of("NEXT", previous, key.value(), sourceHash));
    }

    private final class SourceCursor {
        private final LegacyType type;
        private List<LegacySourceHash> page = List.of();
        private int index;
        private long afterLegacyId;
        private boolean terminalPage;

        private SourceCursor(LegacyType type) {
            this.type = type;
        }

        private LegacySourceHash current() {
            fill();
            return index < page.size() ? page.get(index) : null;
        }

        private void advance() {
            index++;
        }

        private void fill() {
            if (index < page.size() || terminalPage) {
                return;
            }
            page = importer.sourceHashPage(type, afterLegacyId, PAGE_SIZE);
            index = 0;
            if (page.isEmpty()) {
                terminalPage = true;
                return;
            }
            validateSourcePage(type, afterLegacyId, page);
            afterLegacyId = page.get(page.size() - 1).legacyId();
            terminalPage = page.size() < PAGE_SIZE;
        }
    }

    private final class LinkCursor {
        private final LegacyType type;
        private List<LegacyStoryLink> page = List.of();
        private int index;
        private long afterLegacyId;
        private boolean terminalPage;

        private LinkCursor(LegacyType type) {
            this.type = type;
        }

        private LegacyStoryLink current() {
            fill();
            return index < page.size() ? page.get(index) : null;
        }

        private void advance() {
            index++;
        }

        private void fill() {
            if (index < page.size() || terminalPage) {
                return;
            }
            page = linkRepository.findByLegacyTypeAndLegacyIdGreaterThanOrderByLegacyIdAsc(
                type,
                afterLegacyId,
                PageRequest.of(0, PAGE_SIZE)
            );
            index = 0;
            if (page.isEmpty()) {
                terminalPage = true;
                return;
            }
            validateLinkPage(type, afterLegacyId, page);
            afterLegacyId = page.get(page.size() - 1).getLegacyId();
            terminalPage = page.size() < PAGE_SIZE;
        }
    }

    private void validateSourcePage(
        LegacyType type,
        long afterLegacyId,
        List<LegacySourceHash> page
    ) {
        long previous = afterLegacyId;
        for (LegacySourceHash source : page) {
            if (source.legacyId() <= previous) {
                throw invalidCursor(type, previous, source.legacyId());
            }
            previous = source.legacyId();
        }
    }

    private void validateLinkPage(
        LegacyType type,
        long afterLegacyId,
        List<LegacyStoryLink> page
    ) {
        long previous = afterLegacyId;
        for (LegacyStoryLink link : page) {
            if (link.getLegacyId() == null || link.getLegacyId() <= previous) {
                throw invalidCursor(type, previous, link.getLegacyId());
            }
            previous = link.getLegacyId();
        }
    }

    private LegacyImportException invalidCursor(LegacyType type, long after, Long actual) {
        return new LegacyImportException(
            "LEGACY_RECONCILIATION_CURSOR_INVALID",
            type.name() + ":after=" + after + ",actual=" + actual
        );
    }

    private record TypeReport(
        long legacyCount,
        long linkedCount,
        String legacyChecksum,
        String linkedChecksum
    ) {
    }

    private record SourceKey(LegacyType type, long id) {
        String value() {
            return type.name() + ":" + id;
        }
    }
}
