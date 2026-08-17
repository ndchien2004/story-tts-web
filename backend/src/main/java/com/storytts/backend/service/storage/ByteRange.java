package com.storytts.backend.service.storage;

/**
 * Một khoảng byte mà trình phát hỏi tới, đã tách khỏi cú pháp HTTP.
 *
 * <p>Tồn tại vì hai tầng lưu trữ giải quyết Range theo hai cách không quy về
 * nhau được. Bản cục bộ biết kích thước file ngay lập tức nên nó tự tính được
 * mọi thứ. Bản Cloudinary thì không: hỏi kích thước là thêm một vòng mạng nữa
 * trước khi phát được byte đầu tiên. Nên chỗ này cố ý mô tả khoảng byte theo
 * cách <b>không cần biết tổng độ dài</b> — {@code end} để trống nghĩa là "tới
 * hết file", và đó là dạng chuyển thẳng lên Cloudinary được.
 *
 * <p>{@link org.springframework.http.HttpRange} của Spring không dùng được ở
 * đây đúng vì lý do ngược lại: mọi getter của nó đều đòi tổng độ dài.
 */
public record ByteRange(long start, Long end) {

    public ByteRange {
        if (start < 0) {
            throw new IllegalArgumentException("Vị trí bắt đầu không được âm: " + start);
        }
        if (end != null && end < start) {
            throw new IllegalArgumentException("Khoảng byte ngược: " + start + "-" + end);
        }
    }

    /**
     * Đọc khoảng đầu tiên trong header {@code Range}.
     *
     * <p>Chỉ nhận hai dạng {@code bytes=N-} và {@code bytes=N-M} — đúng những gì
     * thẻ {@code <audio>} của trình duyệt gửi khi tua. Mọi dạng khác (nhiều
     * khoảng, hay khoảng đếm ngược {@code bytes=-N}) trả về null.
     *
     * <p>Trả null <b>không</b> có nghĩa là người gọi sẽ nhận trọn file: bộ điều
     * khiển khi ấy trả 200 kèm cả tài nguyên, và Spring MVC tự nhận ra header
     * {@code Range} rồi cắt lát giúp — đã kiểm bằng tay, {@code bytes=-500} ra
     * đúng 206 với 500 byte cuối. Nghĩa là những dạng ấy vẫn đúng, chỉ kém hiệu
     * quả: khi audio nằm ở nơi lưu trữ từ xa thì cả file được tải về rồi mới bị
     * cắt. Đánh đổi có ý thức, cho những dạng gần như không trình phát nào gửi.
     *
     * @return khoảng đọc được, hay null nếu không có header hoặc không hiểu được
     */
    public static ByteRange parseFirst(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        String value = headerValue.trim();
        if (!value.startsWith("bytes=")) {
            return null;
        }
        String spec = value.substring("bytes=".length()).trim();
        // Nhiều khoảng trong một header: bỏ qua, phát trọn file.
        if (spec.contains(",")) {
            return null;
        }
        int dash = spec.indexOf('-');
        // Không có gạch nối, hoặc gạch nối đứng đầu (dạng đếm ngược từ cuối).
        if (dash <= 0) {
            return null;
        }
        try {
            long start = Long.parseLong(spec.substring(0, dash).trim());
            String tail = spec.substring(dash + 1).trim();
            Long end = tail.isEmpty() ? null : Long.parseLong(tail);
            if (start < 0 || (end != null && end < start)) {
                return null;
            }
            return new ByteRange(start, end);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Giới hạn khoảng này ở {@code maxBytes}, và luôn trả về một khoảng có điểm cuối.
     *
     * <p>Đây là chỗ tổng độ dài không cần thiết: cắt ngắn một khoảng thì không
     * bao giờ vượt ra ngoài file, còn điểm cuối vượt quá cuối file thì cả hai
     * tầng lưu trữ đều tự kẹp lại — bản cục bộ kẹp theo kích thước file, còn
     * Cloudinary kẹp theo đúng ngữ nghĩa Range của HTTP.
     */
    public ByteRange cap(long maxBytes) {
        long cappedEnd = start + maxBytes - 1;
        if (end != null && end <= cappedEnd) {
            return this;
        }
        return new ByteRange(start, cappedEnd);
    }

    /** Dạng header để chuyển tiếp lên máy chủ ở tầng trên. */
    public String toHeaderValue() {
        return "bytes=" + start + "-" + (end == null ? "" : end);
    }
}
