package com.storytts.backend.service.storage;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * Một lát byte đang mở, kèm đủ thông tin để dựng phản hồi HTTP.
 *
 * <p>Là {@link Closeable} vì {@link #body()} có thể là một kết nối mạng còn đang
 * mở tới Cloudinary, không phải lúc nào cũng là file trên đĩa. Bên gọi phải đóng
 * — hoặc giao cho Spring đóng hộ bằng cách bọc trong một {@code Resource}, xem
 * {@link MediaStreamResource}.
 *
 * @param body          luồng byte của riêng lát này, đã ở đúng vị trí bắt đầu
 * @param contentLength số byte trong lát này, tức Content-Length của phản hồi
 * @param totalLength   kích thước trọn vẹn của file, tức mẫu số trong Content-Range
 * @param start         byte đầu tiên của lát, tính từ 0
 * @param end           byte cuối cùng của lát, tính từ 0 và tính cả nó
 * @param contentType   kiểu media, đã có sẵn giá trị mặc định khi nơi lưu không nói
 * @param partial       true thì trả 206 kèm Content-Range, false thì trả 200
 */
public record MediaSlice(
        InputStream body,
        long contentLength,
        long totalLength,
        long start,
        long end,
        String contentType,
        boolean partial
) implements Closeable {

    /** Giá trị header {@code Content-Range} cho lát này. */
    public String contentRangeHeader() {
        return "bytes %d-%d/%d".formatted(start, end, totalLength);
    }

    @Override
    public void close() throws IOException {
        body.close();
    }
}
