package com.storytts.backend.service.storage;

import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Lưu media trên Cloudinary, và phát lại bằng cách chuyển tiếp qua máy chủ.
 *
 * <h3>Vì sao byte đi vòng qua máy chủ chứ không tới thẳng trình duyệt</h3>
 * Cloudinary có ký đường dẫn, nên về mặt kỹ thuật hoàn toàn có thể đưa thẳng một
 * URL cho trình phát và để CDN gánh băng thông. Nhưng trên gói miễn phí, chữ ký
 * ấy <b>không hết hạn</b> — thời hạn, giới hạn IP và ACL là tính năng của gói
 * Advanced trở lên. Một URL không hết hạn nghĩa là ai cầm được nó thì nghe được
 * mãi mãi, kể cả sau khi VIP hết hạn hay chương bị gỡ.
 *
 * <p>Mà quyền nghe ở đây thay đổi theo thời gian và phải thu hồi được: chương
 * bán bằng Xu, VIP có hạn dùng, bản audio người đọc tự dựng là của riêng họ. Nên
 * người gác cửa vẫn phải là máy chủ, kiểm ở từng request. Đường dẫn đã ký chỉ
 * nằm giữa máy chủ và Cloudinary, không bao giờ ra khỏi tiến trình này.
 *
 * <p>Cái giá là băng thông đi hai chặng. Đó là đánh đổi có ý thức, và là chỗ để
 * nâng cấp nếu sau này lên gói trả phí: khi chữ ký hết hạn được thì đổi
 * {@link #open} thành trả về URL, còn hai lớp kiểm quyền phía trên không đổi.
 *
 * <h3>Range</h3>
 * Khoảng byte trình phát hỏi được chuyển thẳng lên Cloudinary thay vì tự cắt ở
 * đây. Nhờ vậy máy chủ không bao giờ phải giữ trọn một file audio trong bộ nhớ
 * để trả về một khúc giữa của nó — điều đáng kể khi heap chỉ có 224MB và một
 * chương có thể nặng vài chục MB.
 */
@Slf4j
@RequiredArgsConstructor
public class CloudinaryMediaStorage implements MediaStorage {

    private final CloudinaryService cloudinaryService;

    /* ---------------------------------------------------------------- */
    /* Ghi                                                               */
    /* ---------------------------------------------------------------- */

    @Override
    public String store(byte[] content, MediaKind kind, String extension) {
        return cloudinaryService.uploadAudio(content, kind.slug(), extension);
    }

    @Override
    public String store(InputStream input, MediaKind kind, String extension) {
        // Cloudinary nhận file trong một request có chữ ký, nên nội dung phải nằm
        // trọn trong bộ nhớ dù muốn hay không. Trần dung lượng của đường tải lên
        // (xem AudioService và BgmService) chính là thứ giữ cho lượt đọc này
        // không thổi bay heap.
        try {
            return store(input.readAllBytes(), kind, extension);
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được file tải lên.");
        }
    }

    /* ---------------------------------------------------------------- */
    /* Đọc                                                               */
    /* ---------------------------------------------------------------- */

    @Override
    public MediaSlice open(String key, MediaKind kind, ByteRange range) {
        HttpResponse<InputStream> response = cloudinaryService.fetchAudio(
                key, range == null ? null : range.toHeaderValue());

        int status = response.statusCode();
        if (status == 404 || status == 401) {
            // 401 cũng nằm ở đây có chủ ý: Cloudinary trả về nó cho asset đã bị
            // xóa hệt như cho chữ ký sai, và cả hai đều có nghĩa là khóa này
            // không còn phát được nữa.
            closeQuietly(response.body());
            throw new MediaNotFoundException("Cloudinary không còn giữ " + key + " (mã " + status + ")");
        }
        if (status >= 300) {
            closeQuietly(response.body());
            throw new IllegalStateException("Cloudinary trả về mã " + status + " cho " + key);
        }

        String contentType = response.headers().firstValue("content-type").orElse(null);
        if (status == 206) {
            return partialSlice(response, key, contentType);
        }

        long total = response.headers().firstValueAsLong("content-length").orElse(-1);
        if (total < 0) {
            closeQuietly(response.body());
            throw new IllegalStateException("Cloudinary không cho biết dung lượng của " + key);
        }
        return new MediaSlice(response.body(), total, total, 0, Math.max(total - 1, 0), contentType, false);
    }

    /**
     * Dựng lát từ phản hồi 206, lấy số liệu từ chính {@code Content-Range} của Cloudinary.
     *
     * <p>Không tự tính lại từ khoảng đã hỏi: khoảng ấy có thể mở ({@code bytes=N-})
     * hoặc vượt quá cuối file, và bên biết câu trả lời đúng là bên đang giữ file.
     */
    private MediaSlice partialSlice(HttpResponse<InputStream> response, String key, String contentType) {
        String contentRange = response.headers().firstValue("content-range").orElse(null);
        long length = response.headers().firstValueAsLong("content-length").orElse(-1);

        // Dạng: "bytes 0-1048575/8388608"
        if (contentRange == null || !contentRange.startsWith("bytes ")) {
            closeQuietly(response.body());
            throw new IllegalStateException("Cloudinary trả 206 nhưng thiếu Content-Range cho " + key);
        }
        try {
            String spec = contentRange.substring("bytes ".length()).trim();
            int slash = spec.indexOf('/');
            int dash = spec.indexOf('-');
            long start = Long.parseLong(spec.substring(0, dash).trim());
            long end = Long.parseLong(spec.substring(dash + 1, slash).trim());
            long total = Long.parseLong(spec.substring(slash + 1).trim());
            return new MediaSlice(response.body(),
                    length >= 0 ? length : end - start + 1,
                    total, start, end, contentType, true);
        } catch (RuntimeException ex) {
            closeQuietly(response.body());
            throw new IllegalStateException("Không đọc được Content-Range của " + key + ": " + contentRange, ex);
        }
    }

    /**
     * Hỏi Cloudinary xem khóa này còn dữ liệu không.
     *
     * <p>Dùng GET một byte đầu chứ không dùng HEAD: đường phát của Cloudinary trả
     * lời HEAD không nhất quán qua CDN, còn một khoảng một byte thì chắc chắn đi
     * đúng đường mà lượt phát thật sẽ đi.
     */
    @Override
    public boolean exists(String key, MediaKind kind) {
        if (key == null || key.isBlank()) {
            return false;
        }
        try (MediaSlice slice = open(key, kind, new ByteRange(0, 0L))) {
            return true;
        } catch (MediaNotFoundException ex) {
            return false;
        } catch (RuntimeException | IOException ex) {
            // Mạng chập chờn không phải là bằng chứng file đã mất. Trả về true để
            // không có lượt rà soát nào đánh dấu hỏng một bản audio còn nguyên.
            log.warn("Không kiểm tra được {} trên Cloudinary: {}", key, ex.getMessage());
            return true;
        }
    }

    /* ---------------------------------------------------------------- */
    /* Xóa                                                               */
    /* ---------------------------------------------------------------- */

    @Override
    public void delete(String key, MediaKind kind) {
        cloudinaryService.destroyAudio(key);
    }

    /** Không có bước ghi tạm nào ở đây: một request hoặc thành công, hoặc không để lại gì. */
    @Override
    public int sweepTemporary(Duration olderThan) {
        return 0;
    }

    @Override
    public String describe() {
        return "Cloudinary (resource_type=video, type=authenticated)";
    }

    private static void closeQuietly(InputStream stream) {
        try {
            stream.close();
        } catch (IOException ignored) {
            // Đang trên đường xử lý một lỗi khác; lỗi lúc đóng không thêm được gì.
        }
    }
}
