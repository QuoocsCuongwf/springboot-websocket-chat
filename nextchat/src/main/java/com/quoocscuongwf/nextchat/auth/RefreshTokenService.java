package com.quoocscuongwf.nextchat.auth;

import com.quoocscuongwf.nextchat.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

@Service
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration refreshTokenExpiration;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.security.jwt.refresh-token-expiration:P7D}") Duration refreshTokenExpiration) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public Optional<RefreshToken> findByToken(String rawToken) {
        return refreshTokenRepository.findByTokenHash(hash(rawToken));
    }

    @Transactional
    public RefreshTokenIssue createRefreshToken(User user) {
        refreshTokenRepository.deleteByUser(user);
        return issue(user);
    }

    @Transactional
    public RefreshTokenIssue rotateRefreshToken(String rawToken) {
        RefreshToken token = findByToken(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token"));
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        User user = token.getUser();
        if (!user.isActive() || user.isDeleted()) {
            refreshTokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User account is inactive or deleted");
        }

        refreshTokenRepository.delete(token);
        return issue(user);
    }

    private RefreshTokenIssue issue(User user) {
        String rawToken = generateRawToken();
        RefreshToken refreshToken = new RefreshToken(
                user,
                hash(rawToken),
                Instant.now().plus(refreshTokenExpiration)
        );
        refreshTokenRepository.save(refreshToken);
        return new RefreshTokenIssue(user, rawToken);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    public record RefreshTokenIssue(User user, String rawToken) { }
}
