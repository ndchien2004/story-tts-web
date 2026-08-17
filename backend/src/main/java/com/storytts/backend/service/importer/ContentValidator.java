package com.storytts.backend.service.importer;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.StoryStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Kiểm gói nội dung <b>trước</b> khi chạm tới cơ sở dữ liệu hay nơi lưu file.
 *
 * <p>Thứ tự ấy là điều đáng giá nhất ở lớp này. Một chương thiếu tiêu đề hay
 * trỏ tới file audio không tồn tại thì phải bị chặn khi chưa có gì được ghi
 * xuống — chứ không phải sau khi bản audio đã nằm trên Cloudinary và tính vào
 * hạn mức, rồi giao dịch cuộn ngược để lại nó ở đó không ai trỏ tới.
 */
@Component
public class ContentValidator {

    /** Chặn cả những đường dẫn dùng {@code ..} để trỏ ra ngoài gói. */
    public Path resolveAudio(Path bookDir, String relative) {
        Path audio = bookDir.resolve(relative).normalize();
        if (!audio.startsWith(bookDir.normalize())) {
            throw new InvalidPackageException(
                    "Đường dẫn audio trỏ ra ngoài thư mục truyện: " + relative);
        }
        if (!Files.isReadable(audio)) {
            throw new InvalidPackageException("Không thấy file audio: " + relative);
        }
        return audio;
    }

    public void validateBook(ContentPackage.Book book) {
        requireText(book.title(), "book.json thiếu 'title'");
        requireText(book.author(), "book.json thiếu 'author'");
        requireText(book.genre(), "book.json thiếu 'genre'");
        if (book.status() != null && !book.status().isBlank()) {
            parseEnum(StoryStatus.class, book.status(), "status");
        }
    }

    public void validateChapter(ContentPackage.Chapter chapter) {
        if (chapter.chapterNumber() == null || chapter.chapterNumber() < 1) {
            throw new InvalidPackageException("'chapterNumber' phải là số nguyên dương");
        }
        requireText(chapter.title(), "chương thiếu 'title'");
        requireText(chapter.content(), "chương thiếu 'content'");

        if (chapter.accessLevel() != null && !chapter.accessLevel().isBlank()) {
            parseEnum(AccessLevel.class, chapter.accessLevel(), "accessLevel");
        }
        if (chapter.coinPrice() != null && chapter.coinPrice() < 0) {
            throw new InvalidPackageException("'coinPrice' không được âm");
        }
        // Có audio mà không có băm thì không có gì để đối chiếu, và một file
        // audio hỏng nửa chừng trông y hệt một file lành cho tới lúc ai đó bấm
        // play. Bắt buộc khai băm là cách duy nhất phát hiện ra sớm.
        if (notBlank(chapter.audio()) && !notBlank(chapter.audioSha256())) {
            throw new InvalidPackageException(
                    "khai 'audio' thì phải khai luôn 'audioSha256' để đối chiếu");
        }
    }

    /**
     * Đối chiếu băm của file audio với thứ gói khai báo.
     *
     * @return chính chuỗi băm ấy, để bên gọi khỏi tính lại
     */
    public String verifyAudio(Path audio, String expectedSha256) {
        String actual = sha256(audio);
        if (!actual.equalsIgnoreCase(expectedSha256.trim())) {
            throw new InvalidPackageException(
                    "SHA-256 của %s không khớp (gói khai %s, thực tế %s)"
                            .formatted(audio.getFileName(), expectedSha256, actual));
        }
        return actual;
    }

    private static String sha256(Path file) {
        try (var stream = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new InvalidPackageException("Không đọc được " + file.getFileName() + ": " + ex.getMessage());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM thiếu thuật toán SHA-256", ex);
        }
    }

    public <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new InvalidPackageException("'%s' không hợp lệ: %s".formatted(field, value));
        }
    }

    private static void requireText(String value, String message) {
        if (!notBlank(value)) {
            throw new InvalidPackageException(message);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    /** Gói sai hình dạng — lỗi của dữ liệu đầu vào, không phải của hệ thống. */
    public static class InvalidPackageException extends RuntimeException {
        public InvalidPackageException(String message) {
            super(message);
        }
    }
}
