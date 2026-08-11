package com.storytts.backend.dto.admin;

/**
 * Kết quả xếp hàng tạo audio cho một chương trong lệnh chạy hàng loạt.
 *
 * <p>Mỗi chương một dòng, kể cả dòng hỏng. Cả lô mà hỏng một chương thì vẫn phải
 * chạy tiếp những chương còn lại — một chương quá dài hoặc dính giới hạn của nhà
 * cung cấp không được phép làm hỏng cả lượt xử lý.
 *
 * @param queued  đã nhận và đang xử lý nền
 * @param message lý do khi không xếp hàng được, hoặc trạng thái hiện tại khi được
 */
public record BatchTtsResultDto(
        Long chapterId,
        String chapterTitle,
        boolean queued,
        String message
) {
}
