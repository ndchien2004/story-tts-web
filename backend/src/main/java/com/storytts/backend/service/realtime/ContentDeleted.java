package com.storytts.backend.service.realtime;

import java.util.List;

/**
 * Một chương — hoặc cả một truyện — vừa bị gỡ khỏi trang.
 *
 * <h3>Vì sao là sự kiện thứ hai chứ không phải một cờ trên {@link ChapterContentUpdated}</h3>
 * Hai thứ trông giống nhau ở tầng vận chuyển (cùng luồng SSE, cùng
 * {@code AFTER_COMMIT}) nhưng nói hai câu trái ngược nhau với người đang đọc:
 *
 * <pre>
 *   nội dung đổi → "có bản mới, đọc khi nào bạn muốn"   → một dòng, không chặn
 *   nội dung mất → "không còn gì để đọc nữa"            → chặn, và dừng tiếng
 * </pre>
 *
 * Nhét cả hai vào một sự kiện nghĩa là mọi bên nhận phải rẽ nhánh theo một cờ để
 * biết mình đang nghe câu nào, và một bên nhận quên rẽ sẽ mời người đọc "tải nội
 * dung mới" của một chương không còn tồn tại.
 *
 * <h3>Vì sao mang theo một danh sách chương</h3>
 * Xóa một truyện là xóa mọi chương của nó, và người đang đọc ngồi ở <i>một
 * chương</i> chứ không ở truyện. Luồng SSE đánh khóa theo chương — xem
 * {@link ChapterEventStream} — nên bên phát phải nói rõ những chương nào vừa
 * chết. Cách khác là để luồng tự tra chương nào thuộc truyện nào, nhưng lúc nó
 * chạy thì các chương ấy đã bị xóa khỏi cơ sở dữ liệu rồi: sau
 * {@code AFTER_COMMIT} không còn gì để tra.
 *
 * <h3>Phát trong giao dịch, nhận sau khi commit</h3>
 * Cùng quy tắc với {@link ChapterContentUpdated}, và ở đây nó còn nặng hơn: báo
 * "truyện đã bị xóa" cho một lần xóa vừa cuộn ngược sẽ đá mọi người đang đọc ra
 * khỏi một truyện vẫn còn nguyên, và không có gì đưa họ trở lại.
 *
 * @param storyId    truyện chứa những chương này; vẫn còn sống khi
 *                   {@code wholeStory} là false
 * @param chapterIds những chương vừa biến mất — đúng một phần tử khi xóa lẻ một
 *                   chương
 * @param wholeStory cả truyện bị gỡ, không chỉ một chương. Quyết định câu chữ và
 *                   quyết định trang đọc còn chỗ nào để quay về hay không
 * @param refunded   có ít nhất một người được hoàn Xu vì lần gỡ này. Không nói
 *                   <i>ai</i>, và cố ý không: luồng SSE không đòi đăng nhập nên
 *                   nó không biết mình đang nói với ai — xem
 *                   {@link ChapterEventStream.ContentDeletedPayload}
 */
public record ContentDeleted(Long storyId, List<Long> chapterIds, boolean wholeStory,
                             boolean refunded) {

    /** Admin gỡ một chương; truyện chứa nó vẫn còn. */
    public static ContentDeleted chapter(Long chapterId, Long storyId, boolean refunded) {
        return new ContentDeleted(storyId, List.of(chapterId), false, refunded);
    }

    /** Admin gỡ cả truyện, kéo theo từng chương trong danh sách. */
    public static ContentDeleted story(Long storyId, List<Long> chapterIds, boolean refunded) {
        return new ContentDeleted(storyId, List.copyOf(chapterIds), true, refunded);
    }
}
