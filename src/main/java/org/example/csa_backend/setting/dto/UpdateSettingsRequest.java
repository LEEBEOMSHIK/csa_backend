package org.example.csa_backend.setting.dto;

public record UpdateSettingsRequest(
        String locale,
        Boolean textNotiEnabled,
        Boolean pushNotiEnabled
) {
}
