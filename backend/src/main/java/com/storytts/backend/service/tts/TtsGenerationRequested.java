package com.storytts.backend.service.tts;

/**
 * Raised once a PROCESSING audio row has been persisted.
 *
 * Synthesis is triggered by this event rather than called directly, so the work
 * only starts after the surrounding transaction commits and the row is visible
 * to the worker's own transaction.
 *
 * <h3>Vì sao nội dung đi theo sự kiện thay vì được đọc lại</h3>
 * Luồng nền hoàn toàn có thể nhận mỗi {@code audioId} rồi tự tra chương ra để
 * lấy chữ. Nó chỉ có một vấn đề: lần tra ấy xảy ra <i>sau</i> lúc phiên bản được
 * chụp, nên một chương bị sửa trong khoảng giữa sẽ cho ra bản audio đọc nội dung
 * mới nhưng mang nhãn phiên bản cũ — rồi phép so phiên bản lúc kết thúc sẽ kết
 * luận sai về chính bản nó vừa dựng.
 *
 * <p>Chở cả hai theo sự kiện làm cho điều đó không diễn đạt được: {@link #text()}
 * và {@link #contentVersion()} rời {@code TtsService} cùng nhau, trong cùng một
 * lần đọc, và không có đường nào để chúng lệch nhau về sau.
 *
 * @param contentVersion phiên bản nội dung mà {@link #text()} thuộc về; đem so
 *                       lại với phiên bản hiện tại của chương lúc ghi kết quả
 */
public record TtsGenerationRequested(Long audioId, String text, int contentVersion,
                                     String voice, int speed) {
}
