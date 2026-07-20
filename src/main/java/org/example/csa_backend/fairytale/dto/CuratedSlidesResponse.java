package org.example.csa_backend.fairytale.dto;

import org.example.csa_backend.fairytale.CuratedFairytaleAudio;
import org.example.csa_backend.fairytale.CuratedFairytalePage;
import org.example.csa_backend.fairytale.FairytaleDetail;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CuratedSlidesResponse(
        Long fairytaleId,
        String contentVersion,
        boolean characterSupported,
        String characterRenderMode,
        List<Page> pages
) {
    public static CuratedSlidesResponse from(FairytaleDetail detail, List<CuratedFairytalePage> pages) {
        boolean characterSupported = detail.getFairytale().isCharacterSupported();
        return new CuratedSlidesResponse(
                detail.getFairytale().getId(),
                detail.getContentVersion(),
                characterSupported,
                "LOCAL_OVERLAY",
                pages.stream().map(page -> Page.from(page, characterSupported)).toList()
        );
    }

    public record Page(
            int pageIndex,
            String imageUrl,
            LocalizedText text,
            Map<String, Map<String, String>> audioUrls,
            CharacterPlacement characterPlacement
    ) {
        private static Page from(CuratedFairytalePage page, boolean characterSupported) {
            return new Page(
                    page.getPageIndex(),
                    page.getImageUrl(),
                    new LocalizedText(page.getTextKo(), page.getTextJa()),
                    audioUrls(page.getAudios()),
                    characterSupported ? CharacterPlacement.from(page) : null
            );
        }

        private static Map<String, Map<String, String>> audioUrls(List<CuratedFairytaleAudio> audios) {
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            for (CuratedFairytaleAudio audio : audios) {
                result.computeIfAbsent(audio.getVoiceType(), ignored -> new LinkedHashMap<>())
                        .put(audio.getLocale(), audio.getAudioUrl());
            }
            return result;
        }
    }

    public record LocalizedText(String ko, String ja) {
    }

    public record CharacterPlacement(
            double x,
            double y,
            double width,
            double height,
            int zIndex,
            String pose,
            boolean flipX
    ) {
        private static CharacterPlacement from(CuratedFairytalePage page) {
            if (page.getPlacementX() == null || page.getPlacementY() == null
                    || page.getPlacementWidth() == null || page.getPlacementHeight() == null
                    || page.getPlacementZIndex() == null || page.getPlacementPose() == null
                    || page.getPlacementFlipX() == null) {
                return null;
            }
            return new CharacterPlacement(
                    page.getPlacementX(),
                    page.getPlacementY(),
                    page.getPlacementWidth(),
                    page.getPlacementHeight(),
                    page.getPlacementZIndex(),
                    page.getPlacementPose(),
                    page.getPlacementFlipX()
            );
        }
    }
}
