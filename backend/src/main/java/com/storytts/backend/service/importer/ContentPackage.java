package com.storytts.backend.service.importer;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Hình dạng của một gói nội dung trên đĩa.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} có chủ ý: gói nội dung
 * do người soạn tay, và một trường thừa (ghi chú, dấu vết của công cụ nào đó)
 * không phải lý do để từ chối cả cuốn truyện. Trường <i>thiếu</i> thì khác — đó
 * là việc của {@link ContentValidator}.
 */
public final class ContentPackage {

    private ContentPackage() {
    }

    /**
     * {@code book.json} — phần mô tả chung của một truyện.
     *
     * @param title       tên truyện, và cũng là thứ nhận diện nó khi nhập lại
     * @param author      tên tác giả; chưa có thì được tạo mới
     * @param genre       tên thể loại; chưa có thì được tạo mới
     * @param description giới thiệu, có thể bỏ trống
     * @param status      ONGOING hoặc COMPLETED; bỏ trống thì mặc định ONGOING
     * @param coverImage  URL ảnh bìa, có thể bỏ trống
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Book(
            String title,
            String author,
            String genre,
            String description,
            String status,
            String coverImage
    ) {
    }

    /**
     * Một tệp trong {@code chapters/} — một chương.
     *
     * @param chapterNumber số thứ tự chương, cùng với truyện thì là danh tính của nó
     * @param title         tên chương
     * @param content       toàn văn chương
     * @param accessLevel   PUBLIC / MEMBER / VIP; <b>chỉ có tác dụng khi tạo mới</b>
     * @param coinPrice     giá bằng Xu; <b>chỉ có tác dụng khi tạo mới</b>
     * @param audio         đường dẫn tương đối tới file audio trong gói, có thể bỏ trống
     * @param audioSha256   băm SHA-256 của file ấy; bắt buộc nếu có {@code audio}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chapter(
            Integer chapterNumber,
            String title,
            String content,
            String accessLevel,
            Long coinPrice,
            String audio,
            String audioSha256
    ) {
    }
}
