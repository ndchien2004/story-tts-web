package com.storytts.backend.service.storage;

import com.storytts.backend.config.StorageProperties;
import com.storytts.backend.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.channels.Channels;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lưu media trên hệ tệp của chính máy đang chạy ứng dụng.
 *
 * <p>Đây là nơi lưu dùng khi lập trình ở máy cá nhân, và là nơi lưu <b>duy nhất
 * đúng</b> khi máy chủ có đĩa của riêng nó. Trên hạ tầng có hệ tệp tạm thời —
 * Render gói miễn phí chẳng hạn — thì đừng dùng bản này; xem
 * {@link CloudinaryMediaStorage}.
 *
 * <h3>Ghi hai bước</h3>
 * Nội dung được ghi vào một file {@code .tmp} <i>cùng thư mục</i> rồi mới đổi tên
 * sang tên thật bằng một lượt {@code ATOMIC_MOVE}. Nhờ vậy tên thật chỉ xuất hiện
 * khi byte cuối cùng đã nằm trên đĩa: tiến trình bị giết giữa chừng để lại một
 * file {@code .tmp} vô hại, chứ không để lại một file MP3 cụt mang tên hợp lệ mà
 * cơ sở dữ liệu tin là dùng được.
 *
 * <p>File tạm nằm cùng thư mục với đích chứ không ở một thư mục {@code temp/}
 * riêng, và đó là điều kiện để {@code ATOMIC_MOVE} chạy được: đổi tên chỉ nguyên
 * tử trong phạm vi một hệ tệp, còn một thư mục tạm cấu hình riêng thì rất dễ rơi
 * sang phân vùng khác và lượt đổi tên lặng lẽ biến thành sao chép.
 */
@Slf4j
public class LocalMediaStorage implements MediaStorage {

    /** Hậu tố của file đang ghi dở. */
    private static final String TEMP_SUFFIX = ".tmp";

    private final Map<MediaKind, Path> roots = new EnumMap<>(MediaKind.class);

    public LocalMediaStorage(StorageProperties properties) {
        roots.put(MediaKind.AUDIO, createDirectory(properties.audioDir(), "app.storage.audio-dir"));
        roots.put(MediaKind.BGM, createDirectory(properties.bgmDir(), "app.storage.bgm-dir"));
    }

    private static Path createDirectory(String configured, String propertyName) {
        // Thiếu cấu hình từng hiện ra dưới dạng NullPointerException từ Paths.get,
        // sâu ba tầng trong lúc dựng bean, không nêu tên thứ gì.
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu cấu hình thư mục lưu trữ (" + propertyName + "). Xem application.properties.");
        }
        try {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            Files.createDirectories(path);
            return path;
        } catch (IOException ex) {
            throw new UncheckedIOException("Không tạo được thư mục lưu trữ: " + configured, ex);
        }
    }

    /* ---------------------------------------------------------------- */
    /* Ghi                                                               */
    /* ---------------------------------------------------------------- */

    @Override
    public String store(byte[] content, MediaKind kind, String extension) {
        return write(kind, extension, temp -> Files.write(temp, content));
    }

    @Override
    public String store(InputStream input, MediaKind kind, String extension) {
        return write(kind, extension, temp -> Files.copy(input, temp, StandardCopyOption.REPLACE_EXISTING));
    }

    /** Ghi vào file tạm rồi đổi tên; hỏng ở bất kỳ đâu cũng không để lại tên thật. */
    private String write(MediaKind kind, String extension, TempWriter writer) {
        Path root = rootOf(kind);
        String fileName = UUID.randomUUID() + normaliseExtension(extension);
        Path target = root.resolve(fileName);
        Path temp = root.resolve(fileName + TEMP_SUFFIX);

        try {
            writer.writeTo(temp);
            move(temp, target);
            return fileName;
        } catch (IOException ex) {
            deleteQuietly(temp);
            throw new UncheckedIOException("Không ghi được file " + fileName, ex);
        } catch (RuntimeException ex) {
            deleteQuietly(temp);
            throw ex;
        }
    }

    /**
     * Đổi tên nguyên tử, và lùi về đổi tên thường nếu hệ tệp không hỗ trợ.
     *
     * <p>Bản lùi không mất tính đúng đắn ở đây: tên đích là một UUID vừa sinh ra
     * nên không có ai khác đang đọc nó, thứ mất đi chỉ là bảo đảm rằng một tiến
     * trình bị giết đúng lúc đổi tên sẽ không để lại file cụt.
     */
    private static void move(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            log.debug("Hệ tệp không hỗ trợ đổi tên nguyên tử, dùng đổi tên thường: {}", ex.getMessage());
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    private interface TempWriter {
        void writeTo(Path temp) throws IOException;
    }

    /* ---------------------------------------------------------------- */
    /* Đọc                                                               */
    /* ---------------------------------------------------------------- */

    @Override
    public MediaSlice open(String key, MediaKind kind, ByteRange range) {
        Path path = resolve(key, kind);
        if (!Files.isReadable(path)) {
            throw new MediaNotFoundException("Không còn file nào ứng với khóa " + key);
        }

        try {
            long total = Files.size(path);
            // File rỗng, hoặc trình phát hỏi một khoảng bắt đầu ngoài cuối file:
            // trả trọn file. RFC 7233 cho phép bỏ qua Range, và đó là cách hiền
            // lành hơn so với 416 cho một tình huống gần như không xảy ra.
            if (range == null || total == 0 || range.start() >= total) {
                return new MediaSlice(
                        new BufferedInputStream(Files.newInputStream(path)),
                        total, total, 0, Math.max(total - 1, 0), null, false);
            }

            long start = range.start();
            long end = range.end() == null ? total - 1 : Math.min(range.end(), total - 1);
            long count = end - start + 1;

            SeekableByteChannel channel = Files.newByteChannel(path);
            channel.position(start);
            InputStream body = new BoundedInputStream(
                    new BufferedInputStream(Channels.newInputStream(channel)), count);

            return new MediaSlice(body, count, total, start, end, null, true);
        } catch (IOException ex) {
            throw new UncheckedIOException("Không đọc được file " + key, ex);
        }
    }

    @Override
    public boolean exists(String key, MediaKind kind) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return Files.isReadable(resolve(key, kind));
    }

    /* ---------------------------------------------------------------- */
    /* Xóa và dọn                                                        */
    /* ---------------------------------------------------------------- */

    @Override
    public void delete(String key, MediaKind kind) {
        if (key == null || key.isBlank()) {
            return;
        }
        deleteQuietly(resolve(key, kind));
    }

    @Override
    public int sweepTemporary(Duration olderThan) {
        Instant cutoff = Instant.now().minus(olderThan);
        int removed = 0;
        for (MediaKind kind : MediaKind.values()) {
            removed += sweep(rootOf(kind), cutoff);
        }
        if (removed > 0) {
            log.info("Đã dọn {} file ghi dở còn sót lại", removed);
        }
        return removed;
    }

    private static int sweep(Path root, Instant cutoff) {
        int removed = 0;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(root, "*" + TEMP_SUFFIX)) {
            for (Path entry : entries) {
                // Một lượt ghi đang chạy cũng có file .tmp; mốc thời gian là thứ
                // phân biệt nó với xác của một lượt ghi đã chết.
                if (Files.getLastModifiedTime(entry).toInstant().isBefore(cutoff)) {
                    Files.deleteIfExists(entry);
                    removed++;
                }
            }
        } catch (IOException ex) {
            log.warn("Không quét được file ghi dở trong {}: {}", root, ex.getMessage());
        }
        return removed;
    }

    @Override
    public String describe() {
        return "hệ tệp cục bộ (" + rootOf(MediaKind.AUDIO) + ")";
    }

    /* ---------------------------------------------------------------- */
    /* Phụ trợ                                                           */
    /* ---------------------------------------------------------------- */

    private Path rootOf(MediaKind kind) {
        return roots.get(kind);
    }

    /**
     * Đưa một khóa về đường dẫn tuyệt đối, và chặn mọi khóa trỏ ra ngoài thư mục.
     *
     * <p>Khóa đi vào đây từ cột {@code file_path}, nên về nguyên tắc nó luôn là
     * một UUID do chính chỗ này sinh ra. Phép kiểm vẫn ở đây vì "về nguyên tắc"
     * không phải là một cơ chế phòng vệ.
     */
    private Path resolve(String key, MediaKind kind) {
        Path root = rootOf(kind);
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root)) {
            throw new BadRequestException("Đường dẫn file không hợp lệ.");
        }
        return path;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("Không xóa được {}: {}", path, ex.getMessage());
        }
    }

    private static String normaliseExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".mp3";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }

    /** Cắt luồng đúng {@code remaining} byte, để một lát không đọc lấn sang lát sau. */
    private static final class BoundedInputStream extends InputStream {

        private final InputStream delegate;
        private long remaining;

        BoundedInputStream(InputStream delegate, long limit) {
            this.delegate = delegate;
            this.remaining = limit;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = delegate.read(buffer, offset, (int) Math.min(length, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public int available() throws IOException {
            return (int) Math.min(delegate.available(), remaining);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
