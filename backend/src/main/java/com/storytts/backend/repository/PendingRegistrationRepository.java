package com.storytts.backend.repository;

import com.storytts.backend.domain.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {

    Optional<PendingRegistration> findByEmail(String email);

    /**
     * Tên đăng nhập đang bị một lượt đăng ký khác giữ chỗ.
     *
     * <p>Kiểm ở bước gửi mã thay vì để tới bước nhập mã: bắt người ta xác thực
     * xong rồi mới báo "tên này có người lấy mất" là cách tệ nhất để nói điều đó.
     */
    @Query("""
            SELECT COUNT(p) > 0 FROM PendingRegistration p
            WHERE p.username = :username AND p.email <> :email AND p.expiresAt > :now
            """)
    boolean usernameHeldByAnother(@Param("username") String username,
                                  @Param("email") String email,
                                  @Param("now") Instant now);

    /** Dọn các lượt đăng ký bỏ dở, gọi kèm mỗi lần có người bắt đầu đăng ký. */
    @Modifying
    @Query("DELETE FROM PendingRegistration p WHERE p.expiresAt < :threshold")
    int deleteExpiredBefore(@Param("threshold") Instant threshold);
}
