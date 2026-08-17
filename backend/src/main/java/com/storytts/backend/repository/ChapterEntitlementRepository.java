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

    /** Xóa truyện kéo theo chương; quyền trỏ tới chương ấy phải đi trước. */
    void deleteByChapterId(Long chapterId);
}
