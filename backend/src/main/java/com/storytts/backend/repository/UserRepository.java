package com.storytts.backend.repository;

import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    /** Cho phép đăng nhập bằng username hoặc email. */
    @Query("SELECT u FROM User u WHERE u.username = :login OR u.email = :login")
    Optional<User> findByUsernameOrEmail(@Param("login") String login);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByRole(Role role);

    @Query("""
            SELECT u FROM User u
            WHERE (:keyword IS NULL OR :keyword = ''
                   OR LOWER(u.username) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<User> search(@Param("keyword") String keyword, Pageable pageable);

    /**
     * Số tài khoản đang có quyền VIP.
     *
     * <p>Viết tay thay vì suy ra từ tên phương thức, vì "VIP" nay đến từ hai
     * nguồn: Admin cấp tay, hoặc một gói còn hạn. Đếm mỗi cờ {@code is_vip} sẽ
     * bỏ sót toàn bộ người đã trả tiền.
     */
    @Query("""
            SELECT COUNT(u) FROM User u
            WHERE u.vipGranted = TRUE OR u.vipUntil > CURRENT_TIMESTAMP
            """)
    long countVipUsers();

    long countByEnabledFalse();

    /** Dùng để chặn việc hạ quyền người quản trị cuối cùng. */
    long countByRole(Role role);

    /**
     * Ghi đường dẫn ảnh đại diện mới.
     *
     * <p>Một câu UPDATE thay vì đọc-sửa-lưu, để lần ghi này là một giao dịch ngắn
     * đứng riêng: việc tải ảnh lên Cloudinary xảy ra trước đó và mất tới 45 giây,
     * nên nó phải nằm ngoài mọi giao dịch. Xem {@code UserProfileService}.
     *
     * @return số dòng đã sửa; 0 nghĩa là tài khoản không còn tồn tại
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE User u SET u.avatarUrl = :url WHERE u.id = :id")
    int updateAvatarUrl(@Param("id") Long id, @Param("url") String url);
}
