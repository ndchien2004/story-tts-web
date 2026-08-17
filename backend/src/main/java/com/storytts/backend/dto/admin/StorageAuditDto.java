package com.storytts.backend.dto.admin;

import java.util.List;

/**
 * Kết quả đối chiếu cơ sở dữ liệu với nơi lưu file.
 *
 * @param storage        nơi lưu đang được dùng, để đọc báo cáo mà không phải đoán
 * @param audioChecked   số bản audio READY đã kiểm
 * @param bgmChecked     số bản nhạc nền đã kiểm
 * @param missingCount   số bản ghi trỏ tới file không còn tồn tại
 * @param missing        chi tiết từng bản ghi ấy
 */
public record StorageAuditDto(
        String storage,
        int audioChecked,
        int bgmChecked,
        int missingCount,
        List<MissingAsset> missing
) {

    /**
     * Một bản ghi có mà file không có.
     *
     * @param kind      AUDIO hay BGM
     * @param id        khóa chính của bản ghi
     * @param chapterId chương chứa bản audio ấy; null với nhạc nền
     * @param key       khóa lưu trữ đang được trỏ tới
     */
    public record MissingAsset(String kind, Long id, Long chapterId, String key) {
    }
}
