package com.storytts.backend.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.config.GoogleProperties;
import com.storytts.backend.exception.BadRequestException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Kiểm tra ID token mà Google Identity Services phát cho trình duyệt.
 *
 * <p>Chữ ký được đối chiếu ngay tại máy chủ bằng khóa công khai trong bộ khóa
 * JWKS của Google, thay vì gọi ngược lại endpoint {@code tokeninfo} cho mỗi lần
 * đăng nhập — Google xếp endpoint đó vào mục gỡ lỗi và có giới hạn số lần gọi.
 *
 * <p>Khóa được nhớ theo {@code kid}. Google xoay khóa định kỳ, nên gặp
 * {@code kid} lạ thì tải lại bộ khóa đúng một lần rồi mới coi là token hỏng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GoogleIdTokenVerifier {

    /** Google phát token với một trong hai dạng issuer này, cả hai đều hợp lệ. */
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private static final String MESSAGE_INVALID =
            "Phiên đăng nhập Google không hợp lệ hoặc đã hết hạn. Vui lòng thử lại.";

    private final GoogleProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private volatile Map<String, PublicKey> signingKeys = Map.of();

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Trả về tài khoản Google đứng sau ID token.
     *
     * @throws BadRequestException nếu token sai chữ ký, hết hạn, phát cho ứng
     *                             dụng khác, hoặc email chưa được Google xác minh
     */
    public GoogleAccount verify(String idToken) {
        if (!isConfigured()) {
            throw new BadRequestException(
                    "Đăng nhập bằng Google chưa được cấu hình. Vui lòng liên hệ quản trị viên.");
        }

        Claims claims = parse(idToken);

        if (!properties.clientId().equals(claims.getAudience() == null ? null
                : claims.getAudience().stream().findFirst().orElse(null))) {
            log.warn("ID token Google phát cho ứng dụng khác: aud={}", claims.getAudience());
            throw new BadRequestException(MESSAGE_INVALID);
        }
        if (!ISSUERS.contains(claims.getIssuer())) {
            log.warn("ID token Google có issuer lạ: {}", claims.getIssuer());
            throw new BadRequestException(MESSAGE_INVALID);
        }

        String email = claims.get("email", String.class);
        if (email == null || email.isBlank() || !Boolean.TRUE.equals(claims.get("email_verified", Boolean.class))) {
            throw new BadRequestException(
                    "Tài khoản Google này chưa xác minh email nên không dùng để đăng nhập được.");
        }

        return new GoogleAccount(
                claims.getSubject(),
                email,
                claims.get("name", String.class),
                claims.get("picture", String.class));
    }

    /* ------------------------------------------------------------------ */
    /* Chữ ký                                                              */
    /* ------------------------------------------------------------------ */

    /** Hạn dùng và định dạng do jjwt kiểm; ở đây chỉ lo tìm đúng khóa. */
    private Claims parse(String idToken) {
        PublicKey key = keyFor(readKeyId(idToken));
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(idToken).getPayload();
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("ID token Google không qua được bước kiểm chữ ký: {}", ex.getMessage());
            throw new BadRequestException(MESSAGE_INVALID);
        }
    }

    /**
     * Đọc {@code kid} trong phần header của token.
     *
     * <p>Phần này chưa được kiểm chữ ký nên chỉ dùng để chọn khóa, không lấy ra
     * bất cứ thông tin nào về người dùng.
     */
    private String readKeyId(String idToken) {
        int dot = idToken.indexOf('.');
        if (dot <= 0) {
            throw new BadRequestException(MESSAGE_INVALID);
        }
        try {
            JsonNode header = objectMapper.readTree(
                    Base64.getUrlDecoder().decode(idToken.substring(0, dot)));
            String keyId = header.path("kid").asText(null);
            if (keyId == null) {
                throw new BadRequestException(MESSAGE_INVALID);
            }
            return keyId;
        } catch (IllegalArgumentException | java.io.IOException ex) {
            throw new BadRequestException(MESSAGE_INVALID);
        }
    }

    private PublicKey keyFor(String keyId) {
        PublicKey cached = signingKeys.get(keyId);
        if (cached != null) {
            return cached;
        }

        // Chưa biết khóa này: hoặc lần đăng nhập đầu tiên, hoặc Google vừa xoay khóa.
        signingKeys = fetchSigningKeys();
        PublicKey refreshed = signingKeys.get(keyId);
        if (refreshed == null) {
            log.warn("Google không công bố khóa kid={} nữa", keyId);
            throw new BadRequestException(MESSAGE_INVALID);
        }
        return refreshed;
    }

    private Map<String, PublicKey> fetchSigningKeys() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.certsUrl()))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            Map<String, PublicKey> keys = new HashMap<>();
            for (JsonNode jwk : objectMapper.readTree(response.body()).path("keys")) {
                if ("RSA".equals(jwk.path("kty").asText())) {
                    keys.put(jwk.path("kid").asText(), toRsaPublicKey(jwk));
                }
            }
            if (keys.isEmpty()) {
                throw new IllegalStateException("bộ khóa rỗng");
            }
            return Map.copyOf(keys);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Yêu cầu đăng nhập Google bị gián đoạn. Vui lòng thử lại.");
        } catch (Exception ex) {
            log.warn("Không lấy được bộ khóa công khai của Google: {}", ex.getMessage());
            throw new BadRequestException(
                    "Không liên hệ được với Google để xác minh đăng nhập. Vui lòng thử lại.");
        }
    }

    private PublicKey toRsaPublicKey(JsonNode jwk) throws Exception {
        Base64.Decoder decoder = Base64.getUrlDecoder();
        BigInteger modulus = new BigInteger(1, decoder.decode(jwk.path("n").asText()));
        BigInteger exponent = new BigInteger(1, decoder.decode(jwk.path("e").asText()));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(modulus, exponent));
    }

    /**
     * Những gì Google cho biết về người vừa đăng nhập.
     *
     * @param subject id tài khoản Google, không đổi kể cả khi người dùng đổi email
     */
    public record GoogleAccount(String subject, String email, String name, String pictureUrl) {
    }
}
