package org.example.csa_backend.setting;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.setting.dto.UpdateSubscriptionRequest;
import org.example.csa_backend.setting.dto.UserSettingsDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@Profile("!prod")
public class DevSubscriptionController {

    private final UserSettingsService userSettingsService;

    @PostMapping("/settings/subscription")
    public ResponseEntity<UserSettingsDto> updateSubscription(@RequestBody UpdateSubscriptionRequest request,
                                                              Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(userSettingsService.updateSubscriptionTier(userId, request.tier()));
    }
}
