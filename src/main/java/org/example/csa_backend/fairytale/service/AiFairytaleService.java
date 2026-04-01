package org.example.csa_backend.fairytale.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.csa_backend.fairytale.AiFairytale;
import org.example.csa_backend.fairytale.AiFairytalePage;
import org.example.csa_backend.fairytale.AiFairytalePageRepository;
import org.example.csa_backend.fairytale.AiFairytaleRepository;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateRequest;
import org.example.csa_backend.fairytale.dto.FairytaleGenerateResponse;
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
    private final AiTextService aiTextService;
    private final AiImageService aiImageService;
    private final AiTtsService aiTtsService;
    private final FileStorageService fileStorageService;

    @Transactional
    public FairytaleGenerateResponse generate(FairytaleGenerateRequest request) {
        String settingsStr = String.join(",", request.settings());

        AiFairytale fairytale = new AiFairytale(
                "생성 중...",
                settingsStr,
                request.genre(),
                request.theme(),
                request.chapterCount(),
                request.voiceType(),
                request.language(),
                request.format() != null ? request.format() : "slide",
                "GENERATING"
        );
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

            List<AiFairytalePage> pages = new ArrayList<>();
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
            }

            fairytale.getPages().addAll(pages);
            fairytale.updateStatus("COMPLETED");
            aiFairytaleRepository.save(fairytale);

            log.info("AI 동화 생성 완료: id={}, title={}", fairytale.getId(), fairytale.getTitle());
            return FairytaleGenerateResponse.from(fairytale);

        } catch (Exception e) {
            fairytale.updateStatus("FAILED");
            aiFairytaleRepository.save(fairytale);
            log.error("AI 동화 생성 실패: id={}", fairytale.getId(), e);
            throw e;
        }
    }
}
