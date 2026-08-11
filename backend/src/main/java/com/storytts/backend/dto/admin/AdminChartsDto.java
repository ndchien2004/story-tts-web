package com.storytts.backend.dto.admin;

import java.util.List;

/**
 * Dữ liệu cho ba biểu đồ ở trang thống kê (mục 4.7 của đề bài).
 *
 * <p>Trả gộp trong một lần gọi vì ba biểu đồ luôn xuất hiện cùng nhau trên một màn hình;
 * tách thành ba endpoint chỉ thêm ba vòng gọi mạng mà không ai dùng riêng lẻ.
 *
 * @param perDay       lượt đọc/nghe theo từng ngày, đã điền đủ cả những ngày không có lượt nào
 * @param topStories   truyện được xem nhiều nhất, tính trên tổng lượt xem tích lũy
 * @param accessLevels tỷ lệ chương theo mức khóa
 */
public record AdminChartsDto(
        List<DailyPoint> perDay,
        List<StoryViews> topStories,
        List<AccessLevelSlice> accessLevels
) {

    /**
     * @param date định dạng ISO yyyy-MM-dd, đã quy về múi giờ của máy chủ
     */
    public record DailyPoint(String date, long read, long listen) {
    }

    public record StoryViews(Long storyId, String title, long viewCount) {
    }

    public record AccessLevelSlice(String accessLevel, String label, long chapters) {
    }
}
