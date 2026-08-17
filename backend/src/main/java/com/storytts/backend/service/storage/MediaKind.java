package com.storytts.backend.service.storage;

/**
 * Loại media được lưu trữ, và là thứ quyết định nó nằm ở đâu.
 *
 * <p>Hai loại này cố ý tách nhau ở mọi tầng lưu trữ — thư mục riêng khi lưu cục
 * bộ, thư mục riêng trên Cloudinary. Audio chương là dữ liệu dựng lại được: xóa
 * đi thì tốn một lượt gọi nhà cung cấp, không mất gì vĩnh viễn. Nhạc nền thì
 * ngược lại, là file quản trị viên tải lên một lần và không có nguồn nào dựng
 * lại được. Một thao tác dọn dẹp nhắm vào audio không bao giờ được phép quét
 * trúng kho nhạc.
 */
public enum MediaKind {

    /** Audio của một chương: TTS dựng ra hoặc admin thu âm sẵn. */
    AUDIO("audio"),

    /** Nhạc nền dùng chung, admin tải lên. */
    BGM("bgm");

    private final String slug;

    MediaKind(String slug) {
        this.slug = slug;
    }

    /** Tên dùng làm thư mục con, giống nhau ở mọi tầng lưu trữ. */
    public String slug() {
        return slug;
    }
}
