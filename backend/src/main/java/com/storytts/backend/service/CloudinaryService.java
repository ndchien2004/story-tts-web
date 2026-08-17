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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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

    /** {@code .../<cloud_name>/<resource_type>/<action>} — upload hay destroy. */
    private static final String API_URL = "https://api.cloudinary.com/v1_1/%s/%s/%s";

    /**
     * Đường phát của một file đã lưu ở dạng {@code authenticated}.
     *
     * <p>Audio đi vào Cloudinary dưới {@code resource_type=video} — đó là ngăn
     * dành cho mọi thứ có trục thời gian, không riêng hình ảnh động — nên đường
     * phát cũng nằm dưới {@code /video/}.
     */
    private static final String DELIVERY_URL =
            "https://res.cloudinary.com/%s/video/authenticated/s--%s--/%s";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(45);

    /** Thời gian chờ khi tải audio về để phát; rộng hơn vì file lớn hơn ảnh nhiều. */
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(60);

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
        JsonNode result = callApi("image", "upload", readBytes(file), fileName(file), Map.of(
                "folder", folder,
                "public_id", "user-" + userId,
                "overwrite", "true",
                "invalidate", "true"));

        String url = result.path("secure_url").asText(null);
        if (url == null || url.isBlank()) {
            throw new BadRequestException("Cloudinary không trả về đường dẫn ảnh.");
        }
        return url;
    }

    /* ---------------------------------------------------------------- */
    /* Audio                                                             */
    /* ---------------------------------------------------------------- */

    /**
     * Tải một file audio lên và trả về public_id do Cloudinary cấp.
     *
     * <p>Lưu ở dạng {@code authenticated}: file không phát được bằng một đường
     * dẫn đoán ra, mà phải kèm chữ ký dựng từ API secret. Đây <b>không</b> phải
     * lớp kiểm quyền của ứng dụng — quyền nghe một chương vẫn do
     * {@code ChapterAccessService} quyết ở từng request, và chữ ký này chỉ nằm
     * giữa máy chủ với Cloudinary, không bao giờ tới tay trình duyệt.
     *
     * <p>Trả về <b>đường dẫn phát</b> — {@code <public_id>.<định dạng>} — chứ
     * không phải riêng public_id, và đó chính là chuỗi được cất vào cột
     * {@code file_path}. Ghép sẵn ở đây vì hai mẩu ấy không bao giờ dùng rời
     * nhau: chuỗi đem đi ký và chuỗi nằm trong đường dẫn phải giống nhau từng ký
     * tự, nên để chúng thành một giá trị duy nhất là cách chắc chắn nhất để
     * chúng không lệch. Cả hai mẩu đều đọc từ phản hồi chứ không tự đoán, vì
     * Cloudinary có quyền chuẩn hóa lại tên lẫn định dạng.
     */
    public String uploadAudio(byte[] content, String folder, String extension) {
        requireConfigured();
        JsonNode result = callApi("video", "upload", content, "audio" + normaliseExtension(extension), Map.of(
                "folder", properties.folder() + "/" + folder,
                "public_id", UUID.randomUUID().toString(),
                "type", "authenticated"));

        String publicId = result.path("public_id").asText(null);
        String format = result.path("format").asText(null);
        if (publicId == null || publicId.isBlank() || format == null || format.isBlank()) {
            throw new BadRequestException("Cloudinary không trả về public_id hoặc định dạng của file audio.");
        }

        String deliveryPath = publicId + "." + format;
        log.info("Đã tải audio lên Cloudinary: {} ({} byte)", deliveryPath, content.length);
        return deliveryPath;
    }

    /** Gỡ một file audio khỏi Cloudinary; file vốn đã không còn thì không phải lỗi. */
    public void destroyAudio(String deliveryPath) {
        if (!isConfigured() || deliveryPath == null || deliveryPath.isBlank()) {
            return;
        }
        try {
            callApi("video", "destroy", null, null, Map.of(
                    "public_id", stripFormat(deliveryPath),
                    "type", "authenticated",
                    "invalidate", "true"));
        } catch (RuntimeException ex) {
            // Không ném tiếp: chỗ gọi luôn là một lượt dọn dẹp, và làm hỏng
            // đường đang chạy vì dọn không xong là đổi một vết rác lấy một lỗi.
            log.warn("Không xóa được {} trên Cloudinary: {}", deliveryPath, ex.getMessage());
        }
    }

    /**
     * Đường phát đã ký của một file {@code authenticated}.
     *
     * <p>Chữ ký là tám ký tự đầu của SHA-1 (mã hóa base64 an toàn cho URL) của
     * chuỗi {@code <đường dẫn phát> + api_secret}. Khác với chữ ký của API upload
     * ở bên dưới, chỗ này dùng base64 chứ không phải hex — hai chỗ cùng một hàm
     * băm nhưng khác cách mã hóa, và nhầm lẫn giữa chúng là lý do phổ biến nhất
     * khiến Cloudinary trả về 401.
     *
     * <p>Không kèm số version vào đường dẫn, nên cũng không kèm nó vào chuỗi ký:
     * mỗi lần tải lên là một UUID mới và không có gì bị ghi đè, nên không có
     * phiên bản cũ nào để mà trỏ nhầm.
     */
    public String signedAudioUrl(String deliveryPath) {
        requireConfigured();
        return DELIVERY_URL.formatted(
                properties.cloudName(), deliverySignature(deliveryPath), deliveryPath);
    }

    private String deliverySignature(String deliveryPath) {
        byte[] digest = sha1(deliveryPath + properties.apiSecret());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, 8);
    }

    /** {@code folder/uuid.mp3} → {@code folder/uuid}; public_id không mang định dạng. */
    private static String stripFormat(String deliveryPath) {
        int dot = deliveryPath.lastIndexOf('.');
        return dot > 0 ? deliveryPath.substring(0, dot) : deliveryPath;
    }

    private static String normaliseExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".mp3";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }

    /**
     * Tải một file audio về, có thể chỉ một khoảng byte.
     *
     * <p>Việc gọi mạng nằm ở đây chứ không ở nơi lưu trữ, để API secret và đường
     * dẫn đã ký không rời khỏi lớp này. Bên gọi chỉ đưa khóa và khoảng byte, rồi
     * nhận về phản hồi thô — nó chịu trách nhiệm đóng thân phản hồi.
     *
     * @param rangeHeader giá trị header {@code Range}, hay null để lấy trọn file
     */
    public HttpResponse<InputStream> fetchAudio(String deliveryPath, String rangeHeader) {
        requireConfigured();
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(signedAudioUrl(deliveryPath)))
                .timeout(STREAM_TIMEOUT)
                .GET();
        if (rangeHeader != null) {
            builder.header("Range", rangeHeader);
        }

        try {
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tải được audio từ Cloudinary: " + deliveryPath, ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Việc tải audio bị gián đoạn.", ex);
        }
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

    /**
     * Ký tham số, dựng body multipart rồi trả về phản hồi đã phân tích.
     *
     * <p>{@code content} để null thì không có phần file nào được gửi — đó là dạng
     * của những lệnh chỉ thao tác trên một asset đã có, như {@code destroy}.
     *
     * <p>Thân request được gửi thành nhiều mảng byte nối nhau thay vì gộp lại một
     * mảng duy nhất. Với ảnh đại diện vài MB thì hai cách như nhau, nhưng một
     * chương audio có thể tới vài chục MB, mà ứng dụng chạy với heap 224MB: gộp
     * lại là giữ nội dung hai lần trong bộ nhớ cùng lúc, không vì lý do gì.
     */
    private JsonNode callApi(String resourceType, String action,
                             byte[] content, String fileName, Map<String, String> params) {
        // TreeMap để tham số luôn theo thứ tự alphabet — đúng thứ tự Cloudinary ký.
        Map<String, String> signed = new TreeMap<>(params);
        signed.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));

        String signature = sign(signed);
        String boundary = "----storytts" + UUID.randomUUID();

        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        signed.forEach((key, value) -> writeField(prefix, boundary, key, value));
        writeField(prefix, boundary, "api_key", properties.apiKey());
        writeField(prefix, boundary, "signature", signature);

        List<byte[]> parts = new ArrayList<>();
        if (content == null) {
            write(prefix, "--" + boundary + "--\r\n");
            parts.add(prefix.toByteArray());
        } else {
            writeFileHeader(prefix, boundary, fileName);
            parts.add(prefix.toByteArray());
            parts.add(content);

            ByteArrayOutputStream suffix = new ByteArrayOutputStream();
            write(suffix, "\r\n--" + boundary + "--\r\n");
            parts.add(suffix.toByteArray());
        }

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL.formatted(properties.cloudName(), resourceType, action)))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArrays(parts))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new BadRequestException("Không gọi được Cloudinary: " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BadRequestException("Việc tải file lên bị gián đoạn.");
        }

        if (response.statusCode() >= 300) {
            log.warn("Cloudinary trả về {} cho {}/{}: {}",
                    response.statusCode(), resourceType, action, response.body());
            throw new BadRequestException(
                    "Cloudinary từ chối yêu cầu này (mã " + response.statusCode() + ").");
        }

        try {
            return objectMapper.readTree(response.body());
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được phản hồi từ Cloudinary.");
        }
    }

    /**
     * Chữ ký của API upload: SHA-1 dạng <b>hex</b>.
     *
     * <p>Khác với chữ ký của đường phát ở {@link #deliverySignature(String)},
     * vốn là base64 và chỉ lấy tám ký tự. Cùng một hàm băm, hai cách mã hóa,
     * hai mục đích — viết tách hẳn ra để không ai dùng nhầm cái này cho cái kia.
     */
    private String sign(Map<String, String> params) {
        StringBuilder toSign = new StringBuilder();
        params.forEach((key, value) -> {
            if (!toSign.isEmpty()) {
                toSign.append('&');
            }
            toSign.append(key).append('=').append(value);
        });
        toSign.append(properties.apiSecret());

        byte[] digest = sha1(toSign.toString());
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) {
            hex.append("%02x".formatted(b));
        }
        return hex.toString();
    }

    private static byte[] sha1(String value) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM thiếu thuật toán SHA-1", ex);
        }
    }

    private static void writeField(ByteArrayOutputStream body, String boundary, String name, String value) {
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        write(body, value + "\r\n");
    }

    /** Phần đầu của trường file; nội dung được nối vào sau dưới dạng mảng byte riêng. */
    private static void writeFileHeader(ByteArrayOutputStream body, String boundary, String fileName) {
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        write(body, "Content-Type: application/octet-stream\r\n\r\n");
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
