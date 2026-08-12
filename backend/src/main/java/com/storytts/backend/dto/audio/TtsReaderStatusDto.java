package com.storytts.backend.dto.audio;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mọi thứ trang đọc cần để dựng tấm bảng "Nghe bằng AI", gói trong một lời gọi.
 *
 * @param enabled        đã gộp cả ba điều kiện: bật tính năng cho người đọc, bật
 *                       TTS toàn cục, và có ít nhất một nhà cung cấp còn dùng được.
 *                       Gộp lại vì nếu không, nút sẽ hiện trên một máy chủ chưa có
 *                       API key và mỗi lần bấm là một lỗi 502
 * @param maxChars       trần độ dài chương; frontend đếm {@code chapter.content.length}
 *                       để tự vô hiệu hóa nút trước khi gửi đi (cả Java và JS đều
 *                       đếm theo đơn vị UTF-16 nên hai bên ra cùng một số)
 * @param dailyQuota     hạn mức trong ngày theo bậc của người gọi; null nghĩa là
 *                       không áp — Khách (chưa có gì để đếm) và Admin
 * @param remainingToday số lượt còn lại, đã kẹp bởi cả trần chung của hệ thống
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TtsReaderStatusDto(
        boolean enabled,
        int maxChars,
        Integer dailyQuota,
        Integer remainingToday
) {
}
