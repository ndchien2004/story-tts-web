package com.storytts.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.config.CloudinaryProperties;
import com.storytts.backend.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Tải ảnh lên Cloudinary.
 *
 * <p>Gọi thẳng REST API bằng {@link HttpClient} thay vì thêm SDK: cả việc tải lên
 * chỉ là một request multipart có chữ ký, không đáng để kéo thêm một thư viện và
 * chuỗi phụ thuộc của nó vào dự án. Cách này cũng giống hệt hai client TTS đang có.
 *
 * <p>Chữ ký: sắp xếp tham số theo alphabet, nối thành {@code k=v&k=v}, ghép API
 * secret vào cuối rồi băm SHA-1 — đúng như tài liệu Cloudinary mô tả. API secret
 * chỉ nằm trong chữ ký, không bao giờ được gửi đi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    private static final String UPLOAD_URL = "https://api.cloudinary.com/v1_1/%s/image/upload";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    private final CloudinaryProperties properties;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    /**
     * Tải ảnh đại diện của một người dùng lên và trả về URL https.
     *
     * <p>Mỗi người một {@code public_id} cố định và bật {@code overwrite}, nên đổi ảnh
     * là ghi đè chứ không để lại rác trên Cloudinary. URL trả về có kèm số version
     * nên trình duyệt vẫn thấy ảnh mới ngay, không cần chờ CDN hết hạn cache.
     */
    public String uploadAvatar(MultipartFile file, Long userId) {
        requireConfigured();
        validateImage(file);

        String folder = properties.folder() + "/avatars";
        return upload(readBytes(file), fileName(file), Map.of(
                "folder", folder,
                "public_id", "user-" + userId,
                "overwrite", "true",
                "invalidate", "true"));
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new BadRequestException(
                    "Máy chủ chưa cấu hình Cloudinary. Vui lòng điền CLOUDINARY_CLOUD_NAME, "
                            + "CLOUDINARY_API_KEY và CLOUDINARY_API_SECRET trong file .env rồi khởi động lại.");
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Chưa chọn ảnh để tải lên.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new BadRequestException("Chỉ chấp nhận file ảnh (JPG, PNG, WEBP…).");
        }
        if (file.getSize() > properties.maxAvatarBytes()) {
            throw new BadRequestException(
                    "Ảnh vượt quá %d MB, vui lòng chọn ảnh nhỏ hơn."
                            .formatted(properties.maxAvatarBytes() / (1024 * 1024)));
        }
    }

    /** Ký tham số, dựng body multipart rồi đọc {@code secure_url} trong phản hồi. */
    private String upload(byte[] content, String fileName, Map<String, String> params) {
        // TreeMap để tham số luôn theo thứ tự alphabet — đúng thứ tự Cloudinary ký.
        Map<String, String> signed = new TreeMap<>(params);
        signed.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));

        String signature = sign(signed);
        String boundary = "----storytts" + UUID.randomUUID();

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        signed.forEach((key, value) -> writeField(body, boundary, key, value));
        writeField(body, boundary, "api_key", properties.apiKey());
        writeField(body, boundary, "signature", signature);
        writeFile(body, boundary, fileName, content);
        write(body, "--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(UPLOAD_URL.formatted(properties.cloudName())))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new BadRequestException("Không gọi được Cloudinary: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Việc tải ảnh bị gián đoạn.");
        }

        if (response.statusCode() >= 300) {
            log.warn("Cloudinary trả về {}: {}", response.statusCode(), response.body());
            throw new BadRequestException("Cloudinary từ chối ảnh này (mã " + response.statusCode() + ").");
        }

        try {
            JsonNode json = objectMapper.readTree(response.body());
            String url = json.path("secure_url").asText(null);
            if (url == null || url.isBlank()) {
                throw new BadRequestException("Cloudinary không trả về đường dẫn ảnh.");
            }
            log.info("Đã tải ảnh lên Cloudinary: {}", json.path("public_id").asText());
            return url;
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được phản hồi từ Cloudinary.");
        }
    }

    private String sign(Map<String, String> params) {
        StringBuilder toSign = new StringBuilder();
        params.forEach((key, value) -> {
            if (!toSign.isEmpty()) {
                toSign.append('&');
            }
            toSign.append(key).append('=').append(value);
        });
        toSign.append(properties.apiSecret());

        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(toSign.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append("%02x".formatted(b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM thiếu thuật toán SHA-1", ex);
        }
    }

    private static void writeField(ByteArrayOutputStream body, String boundary, String name, String value) {
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(body, value + "\r\n");
    }

    private static void writeFile(ByteArrayOutputStream body, String boundary, String fileName, byte[] content) {
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        write(body, "Content-Type: application/octet-stream\r\n\r\n");
        body.writeBytes(content);
        write(body, "\r\n");
    }

    private static void write(ByteArrayOutputStream body, String text) {
        body.writeBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được file tải lên", ex);
        }
    }

    /** Tên file chỉ để log phía Cloudinary; public_id mới là thứ quyết định đường dẫn. */
    private static String fileName(MultipartFile file) {
        String original = file.getOriginalFilename();
        return original == null || original.isBlank() ? "upload" : original;
    }
}
