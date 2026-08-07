package com.storytts.backend.domain;

/**
 * Mức truy cập của một chương — do Admin quyết định (mục 3 & 4.2 đề bài).
 * Đây là cơ chế "khóa chương" trọng tâm của đề tài.
 */
public enum AccessLevel {

    /** Ai cũng đọc/nghe được, kể cả khách chưa đăng nhập. */
    PUBLIC("Công khai"),

    /** Yêu cầu đã đăng nhập (Member hoặc VIP hoặc Admin). */
    MEMBER("Yêu cầu đăng nhập"),

    /** Chỉ thành viên VIP (hoặc Admin) mới đọc/nghe được. */
    VIP("Yêu cầu VIP");

    private final String label;

    AccessLevel(String label) {
        this.label = label;
    }

    /** Nhãn tiếng Việt để frontend hiển thị, ví dụ "🔒 Yêu cầu VIP". */
    public String getLabel() {
        return label;
    }
}
