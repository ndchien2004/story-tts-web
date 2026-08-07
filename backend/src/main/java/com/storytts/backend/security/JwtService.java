package com.storytts.backend.security;

import com.storytts.backend.config.JwtProperties;
import com.storytts.backend.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Sinh và kiểm tra JWT. */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private static final int MIN_SECRET_BYTES = 32;

    private final JwtProperties properties;
    private SecretKey signingKey;

    @PostConstruct
    void init() {
        byte[] bytes = properties.secret() == null
                ? new byte[0]
                : properties.secret().getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret quá ngắn (%d byte). Thuật toán HS256 cần tối thiểu %d byte. "
                            .formatted(bytes.length, MIN_SECRET_BYTES)
                            + "Hãy đặt biến JWT_SECRET trong file .env bằng một chuỗi ngẫu nhiên dài.");
        }
        this.signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(properties.expirationMs());
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .claim("vip", user.isVip())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public long getExpirationMs() {
        return properties.expirationMs();
    }

    /** Trả về id người dùng nếu token hợp lệ, ngược lại trả null (không ném ra ngoài filter). */
    public Long extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Long.valueOf(claims.getSubject());
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT không hợp lệ: {}", ex.getMessage());
            return null;
        }
    }
}
