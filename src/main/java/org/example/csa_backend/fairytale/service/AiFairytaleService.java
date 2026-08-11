package org.example.csa_backend.fairytale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.config.AiGenerationProperties;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.fairytale.AiFairytalePageRepository;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.example.csa_backend.fairytale.CanonicalAiReadRepository;
import org.example.csa_backend.fairytale.FairytaleDownloadLog;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateRequest;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
import org.example.csa_backend.fairytale.dto.MyFairytaleDto;
import org.example.csa_backend.storycontent.LegacyShadowReadObserver;
import org.example.csa_backend.storycontent.LegacyType;
import org.example.csa_backend.storycontent.ContentReadRouter;
import org.example.csa_backend.storycontent.migration.ContentWriteActivityTracker;
import org.example.csa_backend.storycontent.migration.ContentWriteKind;
import org.example.csa_backend.user.UserRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiFairytaleService {

    private final AiFairytaleRepository aiFairytaleRepository;
    private final AiFairytalePageRepository aiFairytalePageRepository;
    private final ObjectProvider<AiTextService> aiTextServiceProvider;
    private final ObjectProvider<AiImageService> aiImageServiceProvider;
    private final ObjectProvider<AiTtsService> aiTtsServiceProvider;
    private final FileStorageService fileStorageService;
    private final AiVideoAssemblyService aiVideoAssemblyService;
    private final UserRepository userRepository;
    private final AiGenerationProperties aiGenerationProperties;

    private final LegacyShadowReadObserver shadowReadObserver;
    private final ContentWriteActivityTracker writeActivityTracker;
    private final ContentReadRouter contentReadRouter;
    private final CanonicalAiReadRepository canonicalReadRepository;

    @Transactional
    public FairytaleGenerateResponse generate(FairytaleGenerateRequest request, Long userId) {
        return writeActivityTracker.execute(ContentWriteKind.LEGACY_AI, () -> generateLegacy(request, userId));
    }

    private FairytaleGenerateResponse generateLegacy(FairytaleGenerateRequest request, Long userId) {
        if (!aiGenerationProperties.isEnabled()) {
            throw new BusinessException(ErrorCode.FEATURE_DISABLED);
        }

        AiTextService aiTextService = aiTextServiceProvider.getObject();
        AiImageService aiImageService = aiImageServiceProvider.getObject();
        AiTtsService aiTtsService = aiTtsServiceProvider.getObject();
        String settingsStr = String.join(",", request.settings());

        AiFairytale fairytale = new AiFairytale(
                "생성 중...",
                settingsStr,
                request.genre(),
                request.theme(),
                request.chapterCount(),
                request.voiceType(),
                request.language(),
                normalizeFormat(request.format()),
                "GENERATING"
        );
        if (userId != null) {
            userRepository.findById(userId).ifPresent(fairytale::assignOwner);
        }
        aiFairytaleRepository.save(fairytale);

        try {
            AiTextService.GeneratedFairytale generated = aiTextService.generate(
                    request.settings(),
                    request.genre(),
                    request.theme(),
                    request.chapterCount(),
                    request.useCharacter(),
                    request.language()
            );

            fairytale.updateTitle(generated.title());

            boolean isVideoFormat = "video".equals(fairytale.getFormat());
            List<AiFairytalePage> pages = new ArrayList<>();
            List<AiVideoAssemblyService.PageMedia> pageMedia = new ArrayList<>();
            for (AiTextService.GeneratedPage page : generated.pages()) {
                byte[] imageData = aiImageService.generateImage(
                        page.text(), request.useCharacter(), request.language());
                String imageUrl = fileStorageService.saveImage(
                        fairytale.getId(), page.pageIndex(), imageData);

                byte[] audioData = aiTtsService.generateTts(
                        page.text(), request.voiceType(), request.language());
                String audioUrl = fileStorageService.saveAudio(
                        fairytale.getId(), page.pageIndex(),
                        request.voiceType(), request.language(), audioData);

                AiFairytalePage savedPage = new AiFairytalePage(
                        fairytale, page.pageIndex(), page.text(), imageUrl, audioUrl);
                pages.add(aiFairytalePageRepository.save(savedPage));

                if (isVideoFormat) {
                    pageMedia.add(new AiVideoAssemblyService.PageMedia(
                            page.pageIndex(), imageData, audioData));
                }
            }

            fairytale.getPages().addAll(pages);

            if (isVideoFormat) {
                assembleAndAttachVideo(fairytale, pageMedia);
            } else {
                fairytale.updateStatus("COMPLETED");
            }
            aiFairytaleRepository.save(fairytale);

            log.info("AI 동화 생성 완료: id={}, title={}, status={}",
                    fairytale.getId(), fairytale.getTitle(), fairytale.getStatus());
            return FairytaleGenerateResponse.from(fairytale);

        } catch (Exception e) {
            fairytale.updateStatus("FAILED");
            aiFairytaleRepository.save(fairytale);
            log.error("AI 동화 생성 실패: id={}", fairytale.getId(), e);
            throw e;
        }
    }

    /**
     * Assembles the mp4 for a "video" format fairytale from the page images/audio
     * that were already generated above. Text/image/audio generation already
     * succeeded and cost real provider calls by this point, so an assembly
     * failure here does not discard the fairytale or its pages -- it is logged
     * and the fairytale is marked FAILED (videoUrl stays null) so the pages
     * remain in the DB for an admin to inspect/retry later, instead of
     * re-throwing and losing the already-generated content.
     */
    private void assembleAndAttachVideo(AiFairytale fairytale, List<AiVideoAssemblyService.PageMedia> pageMedia) {
        try {
            byte[] videoData = aiVideoAssemblyService.assembleVideo(fairytale.getId(), pageMedia);
            String videoUrl = fileStorageService.saveVideo(fairytale.getId(), videoData);
            if (videoUrl == null) {
                throw new VideoAssemblyException(
                        "영상 파일 업로드 결과 URL이 없습니다: fairytaleId=" + fairytale.getId());
            }
            fairytale.updateVideoUrl(videoUrl);
            fairytale.updateStatus("COMPLETED");
        } catch (VideoAssemblyException e) {
            log.error("AI 동화 영상 합성 실패 (텍스트/이미지/오디오 페이지는 보존): id={}", fairytale.getId(), e);
            fairytale.updateStatus("FAILED");
        }
    }

    @Transactional(readOnly = true)
    public List<MyFairytaleDto> getMyFairytales(Long userId) {
        return contentReadRouter.route(
            () -> getLegacyMyFairytales(userId),
            () -> canonicalReadRepository.getMyFairytales(userId)
        );
    }

    private List<MyFairytaleDto> getLegacyMyFairytales(Long userId) {
        return aiFairytaleRepository.findByOwnerIdOrderByIdDesc(userId).stream()
                .map(MyFairytaleDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MyFairytaleDto> getSharedFairytales() {
        return contentReadRouter.route(
            this::getLegacySharedFairytales,
            canonicalReadRepository::getSharedFairytales
        );
    }

    private List<MyFairytaleDto> getLegacySharedFairytales() {
        return aiFairytaleRepository.findBySharedAndStatusOrderByIdDesc("Y", "COMPLETED").stream()
                .map(MyFairytaleDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public FairytaleGenerateResponse getMyFairytaleSlides(Long userId, Long fairytaleId) {
        return contentReadRouter.route(
            () -> getLegacyMyFairytaleSlides(userId, fairytaleId),
            () -> canonicalReadRepository.getMyFairytaleSlides(userId, fairytaleId)
        );
    }

    private FairytaleGenerateResponse getLegacyMyFairytaleSlides(Long userId, Long fairytaleId) {
        AiFairytale fairytale = getOwnedFairytale(userId, fairytaleId);
        if (!isServableAsSlides(fairytale)) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        FairytaleGenerateResponse response = FairytaleGenerateResponse.from(fairytale);
        observeShadow(fairytaleId);
        return response;
    }

    @Transactional(readOnly = true)
    public FairytaleGenerateResponse getSharedFairytaleSlides(Long fairytaleId) {
        return contentReadRouter.route(
            () -> getLegacySharedFairytaleSlides(fairytaleId),
            () -> canonicalReadRepository.getSharedFairytaleSlides(fairytaleId)
        );
    }

    private FairytaleGenerateResponse getLegacySharedFairytaleSlides(Long fairytaleId) {
        AiFairytale fairytale = aiFairytaleRepository.findById(fairytaleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!fairytale.isShared()) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!isServableAsSlides(fairytale)) {
            throw new BusinessException(ErrorCode.INVALID_STATE);
        }
        FairytaleGenerateResponse response = FairytaleGenerateResponse.from(fairytale);
        observeShadow(fairytaleId);
        return response;
    }

    /**
     * {@code COMPLETED} is always servable. A {@code video} format fairytale that ended up
     * {@code FAILED} only because ffmpeg assembly failed (text/image/TTS generation already
     * succeeded and the user already paid for that) is also servable as a slide fallback --
     * {@code videoUrl} stays null in that response, and the frontend decides how to present it.
     * Any other FAILED case (including a slide-format fairytale, which fails for a different
     * reason entirely) still blocks access.
     */
    private boolean isServableAsSlides(AiFairytale fairytale) {
        if ("COMPLETED".equals(fairytale.getStatus())) {
            return true;
        }
        return "video".equals(fairytale.getFormat())
                && "FAILED".equals(fairytale.getStatus())
                && !fairytale.getPages().isEmpty();
    }

    @Transactional
    public boolean toggleShare(Long userId, Long fairytaleId) {
        return writeActivityTracker.execute(ContentWriteKind.LEGACY_AI, () -> {
            AiFairytale fairytale = getOwnedFairytale(userId, fairytaleId);
            fairytale.updateShared(!fairytale.isShared());
            return fairytale.isShared();
        });
    }

    @Transactional
    public void deleteMyFairytale(Long userId, Long fairytaleId) {
        writeActivityTracker.execute(ContentWriteKind.LEGACY_AI, () -> {
            AiFairytale fairytale = getOwnedFairytale(userId, fairytaleId);
            aiFairytaleRepository.delete(fairytale);
            fileStorageService.deleteFiles(fairytaleId);
            return null;
        });
    }

    /**
     * Normalizes the client-supplied format to the two values the app understands.
     *
     * <p>The raw value used to be stored as-is, which let a caller write "IMAGE" into the column;
     * it then showed up as its own slice in the admin format chart. Every downstream check is
     * {@code "video".equals(format)}, so anything that is not video already behaves as slides —
     * we store that intent instead of the caller's spelling.
     *
     * <p>Deliberately coercing rather than rejecting: the stray values came from older app
     * builds that are still installed, and failing their creation request with a 400 would break
     * a flow that works fine today. {@code FairytaleDownloadLogService} validates strictly
     * instead, because that endpoint was added after the format values were settled.
     */
    private static String normalizeFormat(String format) {
        return FairytaleDownloadLog.FORMAT_VIDEO.equalsIgnoreCase(
                format != null ? format.trim() : null)
                ? FairytaleDownloadLog.FORMAT_VIDEO
                : FairytaleDownloadLog.FORMAT_SLIDE;
    }

    private AiFairytale getOwnedFairytale(Long userId, Long fairytaleId) {
        AiFairytale fairytale = aiFairytaleRepository.findById(fairytaleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!fairytale.isOwnedBy(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return fairytale;
    }

    private void observeShadow(Long fairytaleId) {
        if (fairytaleId == null) {
            return;
        }
        shadowReadObserver.observe(LegacyType.AI, fairytaleId);
    }
}
