package com.storytts.backend.dto.ai;

/**
 * Trạng thái trợ lý AI, để trang đọc biết nên hiện gì trước khi ai bấm gì.
 *
 * <p>Cùng vai trò với {@code TtsReaderStatusDto} và cùng lý do tồn tại: một cái
 * nút chắc chắn sẽ lỗi thì đừng hiện ra. Máy chủ chưa có API key, hoặc quản trị
 * viên đã tắt tính năng — cả hai đều trả {@code enabled=false}, và trang đọc
 * không vẽ gì cả thay vì mời người ta bấm vào một ngõ cụt.
 *
 * @param enabled        tính năng có dùng được trên máy chủ này không
 * @param dailyQuota     hạn mức trong ngày của người đang gọi; {@code null} với
 *                       khách chưa đăng nhập, âm là không giới hạn
 * @param remainingToday còn lại bao nhiêu lượt; {@code null} khi chưa đăng nhập
 *                       hoặc khi không giới hạn
 * @param maxQuestionChars trần độ dài một câu hỏi, để ô nhập tự chặn trước khi gửi
 */
public record AssistantStatusDto(
        boolean enabled,
        Integer dailyQuota,
        Integer remainingToday,
        int maxQuestionChars
) {
}
