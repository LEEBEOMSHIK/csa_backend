package org.example.csa_backend.setting.dto;

import org.example.csa_backend.setting.UserSettings;

public record UserSettingsDto(
        String locale,
        boolean textNotiEnabled,
        boolean pushNotiEnabled,
        String subscriptionTier
) {
    public static UserSettingsDto from(UserSettings settings) {
        return new UserSettingsDto(
                settings.getLocale(),
                settings.isTextNotiEnabled(),
                settings.isPushNotiEnabled(),
                settings.getSubscriptionTier()
        );
    }
}
