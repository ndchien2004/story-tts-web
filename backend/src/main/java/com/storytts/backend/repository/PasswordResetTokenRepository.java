package com.storytts.backend.repository;

import com.storytts.backend.domain.PasswordResetToken;
import com.storytts.backend.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Vô hiệu mọi liên kết cũ của một người trước khi phát liên kết mới.
     *
     * <p>Không có bước này thì mỗi lần bấm "quên mật khẩu" lại thêm một liên kết
     * còn sống, và cái cũ nhất vẫn mở được tài khoản cho tới lúc hết hạn.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.usedAt = :now WHERE t.user = :user AND t.usedAt IS NULL")
    int invalidateAllFor(@Param("user") User user, @Param("now") Instant now);

    /** Dọn các bản ghi đã hết hạn từ lâu, gọi kèm mỗi lần phát token mới. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
