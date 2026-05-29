package org.example.csa_backend.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.auth.dto.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth/oauth")
@RequiredArgsConstructor
public class OAuthController {

    private final OAuthService oAuthService;

    @PostMapping("/{provider}")
    public ResponseEntity<TokenResponse> oauthLogin(
            @PathVariable String provider,
            @RequestBody OAuthRequest request) {
        return ResponseEntity.ok(oAuthService.processOAuth(provider, request));
    }
}
