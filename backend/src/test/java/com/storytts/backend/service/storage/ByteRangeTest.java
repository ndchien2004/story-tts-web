package com.storytts.backend.service.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Đọc header {@code Range} và giới hạn khoảng byte.
 *
 * <p>Phần này đáng được kiểm riêng vì nó chạy <b>trước</b> khi chạm tới nơi lưu
 * trữ, và nó phải làm được việc mà không biết file dài bao nhiêu. Đó là điều
 * kiện để một lượt tua không kéo cả chương từ Cloudinary về chỉ để cắt lấy một
 * megabyte.
 */
class ByteRangeTest {

    @Test
    @DisplayName("bytes=N-M đọc ra đúng hai đầu")
    void docKhoangDayDu() {
        ByteRange range = ByteRange.parseFirst("bytes=100-199");

        assertThat(range).isNotNull();
        assertThat(range.start()).isEqualTo(100);
        assertThat(range.end()).isEqualTo(199);
    }

    @Test
    @DisplayName("bytes=N- là khoảng mở, không có điểm cuối")
    void docKhoangMo() {
        ByteRange range = ByteRange.parseFirst("bytes=1024-");

        assertThat(range).isNotNull();
        assertThat(range.start()).isEqualTo(1024);
        assertThat(range.end()).isNull();
    }

    /**
     * Trả null nghĩa là "phát trọn file", không phải là lỗi. RFC 7233 cho phép
     * máy chủ bỏ qua Range, và đó là cách xử lý hiền lành hơn cho những dạng
     * không trình phát nào gửi.
     */
    @ParameterizedTest
    @DisplayName("dạng không hỗ trợ thì trả null để phát trọn file")
    @ValueSource(strings = {
            "bytes=-500",        // đếm ngược từ cuối: cần biết tổng độ dài
            "bytes=0-99,200-299", // nhiều khoảng
            "items=0-99",        // đơn vị khác
            "bytes=abc-def",     // không phải số
            "bytes=200-100",     // ngược
            "",
    })
    void dangKhongHoTroThiTraNull(String header) {
        assertThat(ByteRange.parseFirst(header)).isNull();
    }

    @Test
    @DisplayName("không có header thì trả null")
    void khongCoHeader() {
        assertThat(ByteRange.parseFirst(null)).isNull();
    }

    @Test
    @DisplayName("khoảng mở bị giới hạn lại thành một khoảng có điểm cuối")
    void gioiHanKhoangMo() {
        ByteRange capped = new ByteRange(0, null).cap(1024);

        assertThat(capped.start()).isZero();
        assertThat(capped.end()).isEqualTo(1023);
    }

    @Test
    @DisplayName("khoảng dài quá mức bị cắt ngắn, tính từ điểm bắt đầu của nó")
    void gioiHanKhoangDai() {
        ByteRange capped = new ByteRange(5000, 999_999L).cap(1024);

        assertThat(capped.start()).isEqualTo(5000);
        assertThat(capped.end()).isEqualTo(6023);
    }

    @Test
    @DisplayName("khoảng vốn đã ngắn hơn mức giới hạn thì giữ nguyên")
    void khoangNganThiGiuNguyen() {
        ByteRange range = new ByteRange(10, 20L);

        assertThat(range.cap(1024)).isEqualTo(range);
    }

    @Test
    @DisplayName("dựng lại được thành header để chuyển tiếp lên nơi lưu trữ")
    void dungLaiThanhHeader() {
        assertThat(new ByteRange(0, 1023L).toHeaderValue()).isEqualTo("bytes=0-1023");
        assertThat(new ByteRange(2048, null).toHeaderValue()).isEqualTo("bytes=2048-");
    }
}
