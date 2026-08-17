package com.storytts.backend.service.storage;

import org.springframework.core.io.InputStreamResource;

/**
 * Bọc một {@link MediaSlice} thành {@code Resource} để Spring tự ghi ra và tự đóng.
 *
 * <p>Lý do phải có lớp này thay vì dùng thẳng {@link InputStreamResource}: bản
 * gốc trả về {@code contentLength()} bằng cách <b>đọc hết luồng để đếm</b>. Với
 * một lát audio lấy từ Cloudinary, đó vừa là một lượt tải thừa vừa làm luồng cạn
 * trước khi kịp ghi cho người nghe. Ở đây độ dài đã biết sẵn từ lúc mở lát, nên
 * chỉ việc nói ra.
 */
public class MediaStreamResource extends InputStreamResource {

    private final long length;

    public MediaStreamResource(MediaSlice slice) {
        super(slice.body());
        this.length = slice.contentLength();
    }

    @Override
    public long contentLength() {
        return length;
    }
}
