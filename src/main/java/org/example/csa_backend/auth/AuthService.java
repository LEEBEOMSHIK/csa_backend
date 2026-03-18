package org.example.csa_backend.auth;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.auth.dto.LoginRequest;
import org.example.csa_backend.auth.dto.SignupRequest;
import org.example.csa_backend.auth.dto.TokenResponse;
import org.example.csa_backend.jwt.JwtProvider;
import org.example.csa_backend.user.RefreshToken;
import org.example.csa_backend.user.RefreshTokenRepository;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProvider jwtProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already in use");
        }
        userRepository.save(new User(request.email(), passwordEncoder.encode(request.password())));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email or password");
        }

        String accessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user, refreshToken);

        return new TokenResponse(accessToken, refreshToken);
    }

    @Transactional
    public TokenResponse refresh(String rawRefreshToken) {
        if (!jwtProvider.validateToken(rawRefreshToken)) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        Long userId = Long.parseLong(jwtProvider.extractClaims(rawRefreshToken).getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        RefreshToken stored = refreshTokenRepository.findByUser(user)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));

        if (stored.getExpiresAt().isBefore(LocalDateTime.now())
                || !hashToken(rawRefreshToken).equals(stored.getTokenHash())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token expired or invalid");
        }

        String newAccessToken = jwtProvider.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getId());

        saveRefreshToken(user, newRefreshToken);

        return new TokenResponse(newAccessToken, newRefreshToken);
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
}
