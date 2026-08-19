package com.storytts.backend.repository;

import com.storytts.backend.domain.ChapterEntitlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ChapterEntitlementRepository extends JpaRepository<ChapterEntitlement, Long> {

    /** Câu hỏi của mọi lần mở một chương: người này đã mua nó chưa. */
    boolean existsByUserIdAndChapterId(Long userId, Long chapterId);

    /**
     * Trong danh sách chương này, người ấy đã mở những chương nào.
     *
     * <p>Một câu cho cả trang chi tiết truyện, thay vì một câu cho mỗi dòng —
     * danh sách chương của một truyện dài có thể lên tới vài trăm dòng.
     */
    @Query("""
            SELECT e.chapter.id FROM ChapterEntitlement e
            WHERE e.user.id = :userId AND e.chapter.id IN :chapterIds
            """)
    List<Long> findChapterIdsOwnedBy(@Param("userId") Long userId,
                                     @Param("chapterIds") Collection<Long> chapterIds);

    long countByChapterId(Long chapterId);

    /**
     * Những quyền đã <b>trả tiền</b> cho một chương — đầu vào của việc hoàn Xu
     * khi chương bị xóa.
     *
     * <p>{@code coinsSpent > 0} loại ra quyền do quản trị viên cấp: không có
     * đồng nào đi vào thì cũng không có đồng nào để trả lại, và một dòng sổ cái
     * hoàn 0 Xu chỉ làm rối trang lịch sử giao dịch.
     *
     * <p>{@code JOIN FETCH} cả người lẫn chương vì bên gọi cần id người nhận và
     * tên chương để ghi vào sổ; không có nó thì mỗi dòng sinh thêm hai truy vấn,
     * đúng lúc danh sách có thể dài hàng trăm dòng.
     */
    @Query("""
            SELECT e FROM ChapterEntitlement e
            JOIN FETCH e.user
            JOIN FETCH e.chapter
            WHERE e.chapter.id = :chapterId AND e.coinsSpent > 0
            """)
    List<ChapterEntitlement> findPaidByChapter(@Param("chapterId") Long chapterId);

    /** Như trên, nhưng cho mọi chương của một truyện — dùng khi xóa cả truyện. */
    @Query("""
            SELECT e FROM ChapterEntitlement e
            JOIN FETCH e.user
            JOIN FETCH e.chapter
            WHERE e.chapter.story.id = :storyId AND e.coinsSpent > 0
            """)
    List<ChapterEntitlement> findPaidByStory(@Param("storyId") Long storyId);

    /** Xóa truyện kéo theo chương; quyền trỏ tới chương ấy phải đi trước. */
    void deleteByChapterId(Long chapterId);
}
