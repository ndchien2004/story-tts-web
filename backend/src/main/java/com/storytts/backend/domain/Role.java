package com.storytts.backend.domain;

/**
 * Vai trò của tài khoản trong hệ thống (mục 3 đề bài).
 * Khách (chưa đăng nhập) không có bản ghi trong bảng users nên không có role riêng.
 */
public enum Role {
    MEMBER,
    ADMIN
}
