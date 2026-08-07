package com.storytts.backend.domain;

/**
 * Trạng thái xử lý của một bản ghi audio.
 * Phục vụ chế độ tạo TTS bất đồng bộ cho chương dài:
 * frontend hiển thị "Đang tạo audio…" khi trạng thái là PROCESSING.
 */
public enum AudioStatus {
    PROCESSING,
    READY,
    FAILED
}
