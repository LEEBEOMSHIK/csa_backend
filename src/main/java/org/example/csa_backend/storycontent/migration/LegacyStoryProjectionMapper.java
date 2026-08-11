package org.example.csa_backend.storycontent.migration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.storycontent.ContentVersionStatus;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.StoryOrigin;
import org.example.csa_backend.storycontent.StoryVisibility;
import org.springframework.stereotype.Component;

@Component
public class LegacyStoryProjectionMapper {

    private final ContractChecksum checksum;

    public LegacyStoryProjectionMapper(ContractChecksum checksum) {
        this.checksum = checksum;
    }

    public LegacyProjection project(AiFairytale legacy) {
        return projectAi(new AiSource(
            requiredId(legacy.getId()),
            legacy.getTitle(),
            legacy.getSettings(),
            legacy.getGenre(),
            legacy.getTheme(),
            legacy.getChapterCount(),
            legacy.getVoiceType(),
            legacy.getLanguage(),
            legacy.getFormat(),
            legacy.getStatus(),
            legacy.isShared(),
            legacy.getVideoUrl(),
            legacy.getOwner() == null ? null : legacy.getOwner().getId(),
            legacy.getPages().stream()
            .filter(page -> "N".equals(page.getDelYn()))
            .sorted(Comparator.comparingInt(AiFairytalePage::getPageIndex))
            .map(page -> new AiPageSource(
                page.getId(), page.getPageIndex(), page.getText(), page.getImageUrl(), page.getAudioUrl()))
            .toList(),
            new LegacyContractMetadata(
                legacy.getCreDt() == null
                    ? null
                    : legacy.getCreDt().toString(),
                null, null, null, null, null, null, null, null, null, null, null, null,
                legacy.isShared(), legacy.getVoiceType(), legacy.getSettings(), legacy.getGenre(),
                legacy.getTheme(), legacy.getChapterCount()
            )
        ));
    }

    public LegacyProjection projectAi(AiSource legacy) {
        String status = required(legacy.status(), "AI_STATUS_REQUIRED").toUpperCase();
        String format = "video".equalsIgnoreCase(legacy.format()) ? "video" : "slide";
        String locale = normalizedLocale(legacy.language());
        List<AiPageSource> pages = legacy.pages().stream()
            .sorted(Comparator.comparingInt(AiPageSource::pageIndex))
            .toList();
        boolean hasPages = !pages.isEmpty();
        boolean completed = "COMPLETED".equals(status);
        boolean video = "video".equals(format);
        boolean failedVideoWithPages = video && "FAILED".equals(status) && hasPages;
        String voiceType = fallback(legacy.voiceType(), "narrator");

        List<LegacyProjection.JobProjection> jobs = aiJobs(status, video, hasPages);
        boolean adapterSlide = completed || failedVideoWithPages;
        List<LegacyProjection.SceneProjection> scenes = adapterSlide
            ? aiScenes(pages, locale, voiceType)
            : List.of();
        Long ownerId = legacy.ownerUserId();
        boolean ownerMissing = ownerId == null;
        StoryVisibility visibility = ownerMissing
            ? StoryVisibility.ARCHIVED
            : legacy.shared() ? StoryVisibility.SHARED : StoryVisibility.OWNER_PRIVATE;
        boolean publishable = completed && hasPages && (!video || notBlank(legacy.videoUrl())) && !ownerMissing;
        String exceptionCode = ownerMissing ? "AI_OWNER_REQUIRED"
            : completed && !hasPages ? "AI_PAGES_REQUIRED"
            : completed && video && !notBlank(legacy.videoUrl()) ? "AI_VIDEO_REQUIRED"
            : null;

        Map<String, List<String>> voices = new LinkedHashMap<>();
        Map<String, String> defaults = new LinkedHashMap<>();
        if (adapterSlide) {
            boolean completeVoice = pages.stream().allMatch(page -> notBlank(page.audioUrl()));
            voices.put(locale, completeVoice ? List.of(voiceType) : List.of());
            if (completeVoice) {
                defaults.put(locale, voiceType);
            }
        } else {
            voices.put(locale, List.of());
        }
        LegacyProjection.VideoProjection videoProjection = completed && video && notBlank(legacy.videoUrl())
            ? new LegacyProjection.VideoProjection(locale, voiceType, legacy.videoUrl())
            : null;

        List<String> categories = new ArrayList<>();
        if (notBlank(legacy.genre())) {
            categories.add(legacy.genre());
        }
        if (notBlank(legacy.theme())) {
            categories.add(legacy.theme());
        }
        categories = categories.stream().distinct().sorted().toList();
        String title = required(legacy.title(), "AI_TITLE_REQUIRED");
        return new LegacyProjection(
            LegacyType.AI,
            requiredId(legacy.id()),
            StoryOrigin.AI_GENERATED,
            Long.toString(legacy.id()),
            ownerId,
            visibility,
            title,
            title,
            legacy.settings(),
            legacy.settings(),
            categories,
            publishable ? ContentVersionStatus.PUBLISHED : ContentVersionStatus.DRAFT,
            publishable,
            format,
            status,
            locale,
            exceptionCode,
            voices,
            defaults,
            scenes,
            jobs,
            videoProjection,
            checksum.ofParts(aiHashParts(legacy, pages, ownerId)),
            legacy.metadata()
        );
    }

    public LegacyProjection projectCurated(CuratedSource source) {
        List<CuratedPageSource> pages = source.pages().stream()
            .sorted(Comparator.comparingInt(CuratedPageSource::pageIndex))
            .toList();
        if (pages.isEmpty()) {
            return new LegacyProjection(
                LegacyType.CURATED,
                source.id(),
                StoryOrigin.CURATED,
                Long.toString(source.id()),
                null,
                StoryVisibility.ARCHIVED,
                required(source.titleKo(), "CURATED_TITLE_KO_REQUIRED"),
                fallback(source.titleJa(), source.titleKo()),
                source.descriptionKo(),
                source.descriptionJa(),
                source.categoryKeys().stream().sorted().toList(),
                ContentVersionStatus.DRAFT,
                false,
                "slide",
                "INCOMPLETE_PAGES",
                "ko,ja",
                "CURATED_PAGES_REQUIRED",
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                null,
                checksum.ofParts(curatedHashParts(source, pages)),
                source.metadata()
            );
        }

        Map<String, List<String>> availableVoices = completeCuratedVoices(pages);
        Map<String, String> defaults = new LinkedHashMap<>();
        availableVoices.forEach((locale, voices) -> {
            if (!voices.isEmpty()) {
                defaults.put(locale, voices.get(0));
            }
        });
        boolean publishable = !availableVoices.getOrDefault("ko", List.of()).isEmpty()
            && !availableVoices.getOrDefault("ja", List.of()).isEmpty();

        long durationMs = Math.max(1_000L,
            source.durationMin() * 60_000L / Math.max(1, pages.size()));
        List<LegacyProjection.SceneProjection> scenes = pages.stream()
            .map(page -> new LegacyProjection.SceneProjection(
                "page-" + page.pageIndex(),
                page.pageIndex(),
                durationMs,
                1024,
                1024,
                orderedText(page.textKo(), page.textJa()),
                page.imageUrl(),
                page.audios().stream()
                    .sorted(Comparator.comparing(CuratedAudioSource::locale)
                        .thenComparing(CuratedAudioSource::voiceType))
                    .map(audio -> new LegacyProjection.AudioProjection(
                        audio.voiceType(), audio.locale(), audio.audioUrl()))
                    .toList(),
                source.metadata().curatedCharacterSupportedOrDefault()
                    ? page.characterPlacement()
                    : null
            ))
            .toList();

        List<String> hashParts = curatedHashParts(source, pages);
        return new LegacyProjection(
            LegacyType.CURATED,
            source.id(),
            StoryOrigin.CURATED,
            Long.toString(source.id()),
            null,
            StoryVisibility.PUBLISHED,
            required(source.titleKo(), "CURATED_TITLE_KO_REQUIRED"),
            fallback(source.titleJa(), source.titleKo()),
            source.descriptionKo(),
            source.descriptionJa(),
            source.categoryKeys().stream().sorted().toList(),
            publishable ? ContentVersionStatus.PUBLISHED : ContentVersionStatus.DRAFT,
            publishable,
            "slide",
            publishable ? "COMPLETED" : "INCOMPLETE_VOICE_MATRIX",
            "ko,ja",
            publishable ? null : "CURATED_VOICE_MATRIX_INCOMPLETE",
            availableVoices,
            defaults,
            scenes,
            List.of(),
            null,
            checksum.ofParts(hashParts),
            source.metadata()
        );
    }

    private Map<String, String> orderedText(String ko, String ja) {
        Map<String, String> text = new LinkedHashMap<>();
        text.put("ko", required(ko, "CURATED_TEXT_KO_REQUIRED"));
        text.put("ja", required(ja, "CURATED_TEXT_JA_REQUIRED"));
        return text;
    }

    private List<LegacyProjection.JobProjection> aiJobs(String status, boolean video, boolean hasPages) {
        if ("COMPLETED".equals(status)) {
            List<LegacyProjection.JobProjection> jobs = new ArrayList<>();
            jobs.add(new LegacyProjection.JobProjection("CONTENT_GENERATION", "SUCCEEDED", null));
            if (video) {
                jobs.add(new LegacyProjection.JobProjection("VIDEO_RENDER", "SUCCEEDED", null));
            }
            return jobs;
        }
        if (video && "FAILED".equals(status) && hasPages) {
            return List.of(
                new LegacyProjection.JobProjection("CONTENT_GENERATION", "SUCCEEDED", null),
                new LegacyProjection.JobProjection("VIDEO_RENDER", "FAILED", "LEGACY_VIDEO_RENDER_FAILED")
            );
        }
        return switch (status) {
            case "PENDING" -> List.of(
                new LegacyProjection.JobProjection("CONTENT_GENERATION", "QUEUED", null));
            case "GENERATING" -> List.of(
                new LegacyProjection.JobProjection("CONTENT_GENERATION", "RUNNING", null));
            case "FAILED" -> List.of(
                new LegacyProjection.JobProjection("CONTENT_GENERATION", "FAILED", "LEGACY_CONTENT_GENERATION_FAILED"));
            default -> throw new LegacyImportException("UNSUPPORTED_LEGACY_STATUS", status);
        };
    }

    private List<LegacyProjection.SceneProjection> aiScenes(
        List<AiPageSource> pages,
        String locale,
        String voiceType
    ) {
        return pages.stream().map(page -> {
            Map<String, String> text = new LinkedHashMap<>();
            text.put(locale, required(page.text(), "AI_PAGE_TEXT_REQUIRED"));
            List<LegacyProjection.AudioProjection> audios = notBlank(page.audioUrl())
                ? List.of(new LegacyProjection.AudioProjection(
                    voiceType, locale, page.audioUrl()))
                : List.of();
            return new LegacyProjection.SceneProjection(
                "page-" + page.pageIndex(),
                page.pageIndex(),
                5_000L,
                1024,
                1024,
                text,
                page.imageUrl(),
                audios,
                null
            );
        }).toList();
    }

    private List<String> aiHashParts(AiSource legacy, List<AiPageSource> pages, Long ownerId) {
        List<String> parts = new ArrayList<>();
        parts.add("AI");
        parts.add(Long.toString(requiredId(legacy.id())));
        parts.add(legacy.title());
        parts.add(legacy.settings());
        parts.add(legacy.genre());
        parts.add(legacy.theme());
        parts.add(Integer.toString(legacy.chapterCount()));
        parts.add(legacy.voiceType());
        parts.add(legacy.language());
        parts.add(legacy.format());
        parts.add(legacy.status());
        parts.add(Boolean.toString(legacy.shared()));
        parts.add(legacy.videoUrl());
        parts.add(ownerId == null ? null : ownerId.toString());
        parts.addAll(legacy.metadata().hashParts());
        for (AiPageSource page : pages) {
            parts.add(Long.toString(page.id() == null ? 0L : page.id()));
            parts.add(Integer.toString(page.pageIndex()));
            parts.add(page.text());
            parts.add(page.imageUrl());
            parts.add(page.audioUrl());
        }
        return parts;
    }

    private String normalizedLocale(String language) {
        return "ja".equalsIgnoreCase(language) ? "ja" : "ko";
    }

    private long requiredId(Long id) {
        if (id == null || id <= 0) {
            throw new LegacyImportException("LEGACY_ID_REQUIRED", String.valueOf(id));
        }
        return id;
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, List<String>> completeCuratedVoices(List<CuratedPageSource> pages) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        result.put("ko", completeVoices(pages, "ko"));
        result.put("ja", completeVoices(pages, "ja"));
        return result;
    }

    private List<String> completeVoices(List<CuratedPageSource> pages, String locale) {
        Set<String> intersection = null;
        for (CuratedPageSource page : pages) {
            Set<String> voices = new LinkedHashSet<>();
            page.audios().stream()
                .filter(audio -> locale.equals(audio.locale()))
                .map(CuratedAudioSource::voiceType)
                .filter(value -> value != null && !value.isBlank())
                .forEach(voices::add);
            if (intersection == null) {
                intersection = voices;
            } else {
                intersection.retainAll(voices);
            }
        }
        if (intersection == null) {
            return List.of();
        }
        return intersection.stream().sorted().toList();
    }

    private List<String> curatedHashParts(CuratedSource source, List<CuratedPageSource> pages) {
        List<String> parts = new ArrayList<>();
        parts.add("CURATED");
        parts.add(Long.toString(source.id()));
        parts.add(source.titleKo());
        parts.add(source.titleJa());
        parts.add(source.descriptionKo());
        parts.add(source.descriptionJa());
        parts.add(Integer.toString(source.durationMin()));
        parts.add(source.contentVersion());
        parts.addAll(source.metadata().hashParts());
        source.categoryKeys().stream().sorted().forEach(parts::add);
        for (CuratedPageSource page : pages) {
            parts.add(Integer.toString(page.pageIndex()));
            parts.add(page.imageUrl());
            parts.add(page.textKo());
            parts.add(page.textJa());
            LegacyProjection.CharacterPlacement placement = page.characterPlacement();
            parts.add(placement == null ? null : placement.toString());
            page.audios().stream()
                .sorted(Comparator.comparing(CuratedAudioSource::locale)
                    .thenComparing(CuratedAudioSource::voiceType)
                    .thenComparing(CuratedAudioSource::audioUrl))
                .forEach(audio -> {
                    parts.add(audio.locale());
                    parts.add(audio.voiceType());
                    parts.add(audio.audioUrl());
                });
        }
        return parts;
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) {
            throw new LegacyImportException(code, null);
        }
        return value;
    }

    private String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public record CuratedSource(
        long id,
        String titleKo,
        String titleJa,
        String descriptionKo,
        String descriptionJa,
        int durationMin,
        String contentVersion,
        List<String> categoryKeys,
        List<CuratedPageSource> pages,
        LegacyContractMetadata metadata
    ) {
        public CuratedSource {
            categoryKeys = List.copyOf(categoryKeys);
            pages = List.copyOf(pages);
            metadata = metadata == null ? LegacyContractMetadata.empty() : metadata;
        }

        public CuratedSource(
            long id,
            String titleKo,
            String titleJa,
            String descriptionKo,
            String descriptionJa,
            int durationMin,
            String contentVersion,
            List<String> categoryKeys,
            List<CuratedPageSource> pages
        ) {
            this(
                id, titleKo, titleJa, descriptionKo, descriptionJa, durationMin,
                contentVersion, categoryKeys, pages, LegacyContractMetadata.empty()
            );
        }
    }

    public record CuratedPageSource(
        long id,
        int pageIndex,
        String imageUrl,
        String textKo,
        String textJa,
        LegacyProjection.CharacterPlacement characterPlacement,
        List<CuratedAudioSource> audios
    ) {
        public CuratedPageSource {
            audios = List.copyOf(audios);
        }
    }

    public record CuratedAudioSource(String voiceType, String locale, String audioUrl) {
    }

    public record AiSource(
        long id,
        String title,
        String settings,
        String genre,
        String theme,
        int chapterCount,
        String voiceType,
        String language,
        String format,
        String status,
        boolean shared,
        String videoUrl,
        Long ownerUserId,
        List<AiPageSource> pages,
        LegacyContractMetadata metadata
    ) {
        public AiSource {
            pages = List.copyOf(pages);
            metadata = metadata == null ? LegacyContractMetadata.empty() : metadata;
        }

        public AiSource(
            long id,
            String title,
            String settings,
            String genre,
            String theme,
            int chapterCount,
            String voiceType,
            String language,
            String format,
            String status,
            boolean shared,
            String videoUrl,
            Long ownerUserId,
            List<AiPageSource> pages
        ) {
            this(
                id, title, settings, genre, theme, chapterCount, voiceType, language, format,
                status, shared, videoUrl, ownerUserId, pages, LegacyContractMetadata.empty()
            );
        }
    }

    public record AiPageSource(Long id, int pageIndex, String text, String imageUrl, String audioUrl) {
    }
}
