package com.storytts.backend.domain;

/** Loại lượt truy cập được ghi vào bảng view_events. */
public enum ViewType {
    /** Mở nội dung chương để đọc. */
    READ,
    /** Mở danh sách audio của chương — tính là một lượt nghe. */
    LISTEN
}
