package com.storytts.backend.repository;

import com.storytts.backend.domain.Favorite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    Optional<Favorite> findByUserIdAndStoryId(Long userId, Long storyId);

    boolean existsByUserIdAndStoryId(Long userId, Long storyId);

    @Query("""
            SELECT f FROM Favorite f
            JOIN FETCH f.story s
            LEFT JOIN FETCH s.author
            LEFT JOIN FETCH s.genre
            WHERE f.user.id = :userId
            ORDER BY f.createdAt DESC
            """)
    Page<Favorite> findByUser(@Param("userId") Long userId, Pageable pageable);

    long countByStoryId(Long storyId);

    void deleteByUserIdAndStoryId(Long userId, Long storyId);
}
