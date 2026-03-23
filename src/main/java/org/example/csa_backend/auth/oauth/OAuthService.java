package org.example.csa_backend.auth.oauth;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.auth.dto.TokenResponse;
import org.example.csa_backend.jwt.JwtProvider;
import org.example.csa_backend.user.RefreshToken;
import org.example.csa_backend.user.RefreshTokenRepository;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OAuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final GoogleOAuthClient googleOAuthClient;

    @Transactional
    public TokenResponse processOAuth(String provider, OAuthRequest request) {
        if (!"google".equals(provider)) {
            throw new IllegalArgumentException("Unsupported provider: " + provider);
        }

        GoogleUserInfo userInfo = googleOAuthClient.getUserInfo(request.accessToken());

        User user = userRepository.findByProviderAndProviderId("google", userInfo.sub())
                .orElseGet(() -> userRepository.save(
                        new User(userInfo.email(), "google", userInfo.sub(), userInfo.name(), request.locale())
                ));

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String rawRefreshToken = jwtProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user, rawRefreshToken);

        return new TokenResponse(accessToken, rawRefreshToken);
    }

    private void saveRefreshToken(User user, String rawToken) {
        LocalDateTime expiresAt = LocalDateTime.now().plusDays(7);
        String hash = hashToken(rawToken);
        refreshTokenRepository.findByUser(user).ifPresentOrElse(
                stored -> {
                    stored.setTokenHash(hash);
                    stored.setExpiresAt(expiresAt);
                },
                () -> refreshTokenRepository.save(new RefreshToken(user, hash, expiresAt))
        );
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
