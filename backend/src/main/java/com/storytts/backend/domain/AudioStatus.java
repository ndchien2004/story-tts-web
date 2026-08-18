package com.storytts.backend.domain;

/**
 * Trạng thái xử lý của một bản ghi audio.
 *
 * <p>Chuyển trạng thái hợp lệ — không có đường nào khác:
 *
 * <pre>
 *   PROCESSING ──► READY      dựng xong, và phiên bản vẫn khớp chương
 *   PROCESSING ──► STALE      dựng xong, nhưng chương đã đi tiếp trong lúc đó
 *   PROCESSING ──► FAILED     nhà cung cấp lỗi, hoặc máy chủ khởi động lại giữa chừng
 *   READY      ──► STALE      Admin sửa nội dung chương
 *   READY      ──► FAILED     file đã biến mất khỏi nơi lưu trữ
 *   STALE      ──► (xóa)      hết hạn lưu giữ
 * </pre>
 *
 * <p>Không có đường quay lại: {@code STALE ──► READY} không tồn tại, kể cả khi
 * Admin sửa chương rồi hoàn tác về đúng nội dung cũ. Phiên bản là một con số
 * tăng, không phải một dấu vân tay, nên nội dung quay lại thì phiên bản vẫn đi
 * tiếp — và một bản audio mới sẽ được dựng. Đó là cái giá của việc để phiên bản
 * nghiệp vụ làm source of truth, và nó rẻ hơn nhiều so với việc phải chứng minh
 * "nội dung này giống hệt nội dung ba lần sửa trước".
 */
public enum AudioStatus {

    /** Đã xếp hàng hoặc đang gọi nhà cung cấp. */
    PROCESSING,

    /** Dựng xong, đọc đúng phiên bản nội dung hiện tại của chương — phát được. */
    READY,

    /**
     * Dựng xong và file vẫn nghe được, nhưng nó đọc theo một phiên bản nội dung
     * đã cũ.
     *
     * <p>Khác FAILED ở chỗ có thật một file dùng được. Bản như vậy <b>không</b>
     * bao giờ được trả về như audio hiện tại của chương, nhưng cũng không bị cắt
     * giữa chừng của người đang nghe dở — xem {@code AudioService.resolveForStreaming}.
     */
    STALE,

    /** Không dựng được, hoặc file đã biến mất. Không có gì để phát. */
    FAILED
}
