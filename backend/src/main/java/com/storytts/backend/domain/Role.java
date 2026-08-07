package com.storytts.backend.domain;

/**
 * Vai trò của tài khoản trong hệ thống.
 * Khách (chưa đăng nhập) không có bản ghi trong bảng users nên không có role riêng.
 */
public enum Role {
    MEMBER,
    ADMIN
}
