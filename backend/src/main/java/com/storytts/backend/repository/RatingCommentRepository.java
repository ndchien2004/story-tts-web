package com.storytts.backend.repository;

import com.storytts.backend.domain.RatingComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingCommentRepository extends JpaRepository<RatingComment, Long> {

    @Query("""
            SELECT rc FROM RatingComment rc
            JOIN FETCH rc.user
            WHERE rc.story.id = :storyId
            ORDER BY rc.createdAt DESC
            """)
    Page<RatingComment> findByStory(@Param("storyId") Long storyId, Pageable pageable);

    @Query("SELECT AVG(rc.rating) FROM RatingComment rc WHERE rc.story.id = :storyId AND rc.rating IS NOT NULL")
    Double averageRating(@Param("storyId") Long storyId);

    @Query("SELECT COUNT(rc) FROM RatingComment rc WHERE rc.story.id = :storyId AND rc.rating IS NOT NULL")
    long countRatings(@Param("storyId") Long storyId);
}
