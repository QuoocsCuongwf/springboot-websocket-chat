package com.quoocscuongwf.nextchat.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final String DEVELOPMENT_SECRET = "development-only-secret-change-me-before-production-12345";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
            @Value("${app.security.jwt.secret}") String secret,
            @Value("${app.security.jwt.expiration:PT15M}") Duration expiration,
            Environment environment) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes");
        }
        if (DEVELOPMENT_SECRET.equals(secret)
                && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("NEXTCHAT_JWT_SECRET must be configured outside dev and test profiles");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getUsername())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    public String username(String token) {
        return claims(token).getSubject();
    }

    public boolean isValid(String token, UserDetails user) {
        try {
            return user.getUsername().equals(username(token))
                    && claims(token).getExpiration().after(new Date());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public Duration getExpiration() {
        return expiration;
    }
}
