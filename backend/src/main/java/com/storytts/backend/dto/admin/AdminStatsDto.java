package com.storytts.backend.dto.admin;

/**
 * Các con số tóm tắt cho trang tổng quan của Admin.
 *
 * <p>Gộp trong một lần gọi thay vì để giao diện tự ghép từ nhiều endpoint: mỗi con
 * số là một câu {@code COUNT} trên chỉ mục, rẻ hơn nhiều so với chi phí của thêm
 * một vòng gọi mạng.
 *
 * @param chaptersWithAudio số chương đã có audio dùng được — mẫu số là {@code chapters}
 * @param audioProcessing   số bản audio đang tạo dở, để biết máy chủ có đang bận không
 * @param audioFailed       số bản audio tạo hỏng, đáng chú ý vì cần tạo lại
 */
public record AdminStatsDto(
        long stories,
        long storiesOngoing,
        long storiesCompleted,

        long chapters,
        long chaptersPublic,
        long chaptersMember,
        long chaptersVip,

        long chaptersWithAudio,
        long audioFromTts,
        long audioUploaded,
        long audioProcessing,
        long audioFailed,

        long users,
        long vipUsers,
        long disabledUsers,
        long admins,

        long comments,
        long favorites,
        long genres,
        long authors
) {
}
