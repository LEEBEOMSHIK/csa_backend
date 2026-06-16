package org.example.csa_backend.setting;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.setting.dto.AgreeTermRequest;
import org.example.csa_backend.setting.dto.TermAgreementDto;
import org.example.csa_backend.setting.dto.UpdateSettingsRequest;
import org.example.csa_backend.setting.dto.UserSettingsDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserSettingsController {

    private final UserSettingsService userSettingsService;
    private final TermAgreementService termAgreementService;

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsDto> getSettings(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(userSettingsService.getSettings(userId));
    }

    @PutMapping("/settings")
    public ResponseEntity<UserSettingsDto> updateSettings(@RequestBody UpdateSettingsRequest request,
                                                          Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(userSettingsService.updateSettings(userId, request));
    }

    @GetMapping("/terms")
    public ResponseEntity<List<TermAgreementDto>> getTerms(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return ResponseEntity.ok(termAgreementService.getLatestAgreements(userId));
    }

    @PostMapping("/terms/{type}")
    public ResponseEntity<Void> agreeTerm(@PathVariable String type,
                                          @RequestBody AgreeTermRequest request,
                                          Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        TermType termType = TermType.fromPath(type);
        termAgreementService.agree(userId, termType, request.termVersion());
        return ResponseEntity.ok().build();
    }
}
