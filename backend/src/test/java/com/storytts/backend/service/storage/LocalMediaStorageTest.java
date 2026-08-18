package com.storytts.backend.service.storage;

import com.storytts.backend.config.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nơi lưu cục bộ: ghi hai bước, đọc theo khoảng, và dọn file ghi dở.
 *
 * <p>Trọng tâm là những điều trước đây không có ai giữ: tên file thật chỉ xuất
 * hiện khi nội dung đã đầy đủ, và một khoảng byte đọc ra đúng bằng khoảng ấy chứ
 * không lấn sang phần sau.
 */
class LocalMediaStorageTest {

    private static final byte[] NOI_DUNG = "0123456789".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path thuMuc;

    private LocalMediaStorage storage;

    @BeforeEach
    void setUp() {
        storage = new LocalMediaStorage(new StorageProperties(
                StorageProperties.StorageDriver.LOCAL,
                thuMuc.resolve("audio").toString(),
                thuMuc.resolve("bgm").toString(),
                // Hạn lưu giữ bản lỗi thời không liên quan gì tới lớp này; null
                // để bộ khởi tạo rút gọn tự điền mặc định.
                null));
    }

    @Test
    @DisplayName("ghi xong thì đọc lại đúng nội dung, và không còn file tạm nào")
    void ghiRoiDocLai() throws IOException {
        String key = storage.store(NOI_DUNG, MediaKind.AUDIO, ".mp3");

        try (MediaSlice slice = storage.open(key, MediaKind.AUDIO, null)) {
            assertThat(slice.body().readAllBytes()).isEqualTo(NOI_DUNG);
            assertThat(slice.totalLength()).isEqualTo(10);
            assertThat(slice.contentLength()).isEqualTo(10);
            assertThat(slice.partial()).isFalse();
        }
        assertThat(tepTamConLai()).isEmpty();
    }

    @Test
    @DisplayName("audio và nhạc nền nằm ở hai ngăn khác nhau")
    void haiNganTachNhau() {
        String key = storage.store(NOI_DUNG, MediaKind.AUDIO, ".mp3");

        assertThat(storage.exists(key, MediaKind.AUDIO)).isTrue();
        assertThat(storage.exists(key, MediaKind.BGM)).isFalse();
    }

    @Test
    @DisplayName("khoảng byte đọc ra đúng khoảng ấy, không lấn sang phần sau")
    void docDungKhoang() throws IOException {
        String key = storage.store(NOI_DUNG, MediaKind.AUDIO, ".mp3");

        try (MediaSlice slice = storage.open(key, MediaKind.AUDIO, new ByteRange(3, 5L))) {
            assertThat(slice.body().readAllBytes()).isEqualTo("345".getBytes(StandardCharsets.UTF_8));
            assertThat(slice.start()).isEqualTo(3);
            assertThat(slice.end()).isEqualTo(5);
            assertThat(slice.contentLength()).isEqualTo(3);
            assertThat(slice.totalLength()).isEqualTo(10);
            assertThat(slice.partial()).isTrue();
            assertThat(slice.contentRangeHeader()).isEqualTo("bytes 3-5/10");
        }
    }

    @Test
    @DisplayName("khoảng mở chạy tới hết file")
    void khoangMoChayToiHet() throws IOException {
        String key = storage.store(NOI_DUNG, MediaKind.AUDIO, ".mp3");

        try (MediaSlice slice = storage.open(key, MediaKind.AUDIO, new ByteRange(7, null))) {
            assertThat(slice.body().readAllBytes()).isEqualTo("789".getBytes(StandardCharsets.UTF_8));
            assertThat(slice.end()).isEqualTo(9);
        }
    }

    @Test
    @DisplayName("điểm cuối vượt quá cuối file thì bị kẹp lại, không báo lỗi")
    void diemCuoiVuotQuaThiBiKep() throws IOException {
        String key = storage.store(NOI_DUNG, MediaKind.AUDIO, ".mp3");

        try (MediaSlice slice = storage.open(key, MediaKind.AUDIO, new ByteRange(8, 99L))) {
            assertThat(slice.body().readAllBytes()).isEqualTo("89".getBytes(StandardCharsets.UTF_8));
            assertThat(slice.end()).isEqualTo(9);
        }
    }

    @Test
    @DisplayName("điểm bắt đầu nằm ngoài cuối file thì phát trọn file thay vì báo lỗi")
    void batDauNgoaiFileThiPhatTron() throws IOException {
        String key = storage.store(NOI_DUNG, MediaKind.AUDIO, ".mp3");

        try (MediaSlice slice = storage.open(key, MediaKind.AUDIO, new ByteRange(999, null))) {
            assertThat(slice.partial()).isFalse();
            assertThat(slice.contentLength()).isEqualTo(10);
        }
    }

    @Test
    @DisplayName("khóa không còn file thì ném MediaNotFoundException, không phải lỗi vào ra chung chung")
    void khoaMatFile() {
        assertThatThrownBy(() -> storage.open("khong-ton-tai.mp3", MediaKind.AUDIO, null))
                .isInstanceOf(MediaNotFoundException.class);
    }

    /**
     * Khóa đi vào đây từ cột {@code file_path}, nên về nguyên tắc luôn là UUID do
     * chính nơi lưu sinh ra. Phép kiểm vẫn phải có, vì "về nguyên tắc" không phải
     * là một cơ chế phòng vệ.
     */
    @Test
    @DisplayName("khóa trỏ ra ngoài thư mục bị từ chối")
    void khoaTroRaNgoaiBiTuChoi() {
        assertThatThrownBy(() -> storage.open("../../etc/passwd", MediaKind.AUDIO, null))
                .isInstanceOf(com.storytts.backend.exception.BadRequestException.class);
    }

    @Test
    @DisplayName("lưu từ luồng cũng cho ra file đọc lại được")
    void luuTuLuong() throws IOException {
        String key = storage.store(new ByteArrayInputStream(NOI_DUNG), MediaKind.BGM, "mp3");

        try (MediaSlice slice = storage.open(key, MediaKind.BGM, null)) {
            assertThat(slice.body().readAllBytes()).isEqualTo(NOI_DUNG);
        }
        assertThat(key).endsWith(".mp3");
    }

    @Test
    @DisplayName("xóa một khóa không tồn tại không phải là lỗi")
    void xoaKhoaKhongTonTai() {
        storage.delete("khong-co-that.mp3", MediaKind.AUDIO);
        storage.delete(null, MediaKind.AUDIO);
    }

    @Test
    @DisplayName("file ghi dở đủ cũ thì bị dọn, file vừa mới thì được để yên")
    void donFileGhiDo() throws IOException {
        Path ngan = thuMuc.resolve("audio");
        Path cu = Files.writeString(ngan.resolve("cu.mp3.tmp"), "xac cua mot luot ghi da chet");
        Path moi = Files.writeString(ngan.resolve("moi.mp3.tmp"), "mot luot ghi dang chay");
        Files.setLastModifiedTime(cu, java.nio.file.attribute.FileTime.from(
                java.time.Instant.now().minus(Duration.ofHours(2))));

        int daDon = storage.sweepTemporary(Duration.ofHours(1));

        assertThat(daDon).isEqualTo(1);
        assertThat(cu).doesNotExist();
        assertThat(moi).exists();
    }

    private List<Path> tepTamConLai() throws IOException {
        try (var stream = Files.walk(thuMuc)) {
            return stream.filter(path -> path.toString().endsWith(".tmp")).toList();
        }
    }
}
