package com.storytts.backend.service;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.Role;
import com.storytts.backend.domain.User;
import com.storytts.backend.exception.ChapterLockedException;
import com.storytts.backend.security.AppUserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Kiểm thử lớp khóa chương — chức năng trọng tâm của đề bài.
 *
 * <p>Phần lớn bài test gọi thẳng {@code canAccess(accessLevel, principal)}, bản thuần
 * không đụng tới {@code SecurityContext}. Nhờ vậy toàn bộ bảng phân quyền được kiểm
 * mà không cần dựng Spring, không cần cơ sở dữ liệu, và chạy trong vài mili giây.
 */
class AccessControlServiceTest {

    /** Chỉ {@code requireAccess} mới cần tới nó, để biết người gọi đã đăng nhập hay chưa. */
    private final CurrentUserService currentUserService = Mockito.mock(CurrentUserService.class);

    private final AccessControlService accessControl = new AccessControlService(currentUserService);

    // ==================== Dữ liệu dựng sẵn ====================

    private static Optional<AppUserPrincipal> guest() {
        return Optional.empty();
    }

    private static Optional<AppUserPrincipal> member() {
        return Optional.of(principal(Role.MEMBER, false));
    }

    private static Optional<AppUserPrincipal> vip() {
        return Optional.of(principal(Role.MEMBER, true));
    }

    private static Optional<AppUserPrincipal> admin() {
        return Optional.of(principal(Role.ADMIN, false));
    }

    private static AppUserPrincipal principal(Role role, boolean vip) {
        return new AppUserPrincipal(User.builder()
                .id(1L)
                .username("nguoidung")
                .email("nguoidung@storytts.local")
                .passwordHash("{noop}khong-dung-toi")
                .role(role)
                .vipGranted(vip)
                .enabled(true)
                .build());
    }

    private static Optional<AppUserPrincipal> byName(String kind) {
        return switch (kind) {
            case "guest" -> guest();
            case "member" -> member();
            case "vip" -> vip();
            case "admin" -> admin();
            default -> throw new IllegalArgumentException("Không rõ loại người dùng: " + kind);
        };
    }

    // ==================== Bảng phân quyền ====================

    /**
     * Chính là bảng ở mục 3 của đề bài, viết lại thành bài test.
     *
     * <pre>
     * access_level | Khách | Thành viên | VIP | Admin
     * PUBLIC       |   ✔   |     ✔      |  ✔  |   ✔
     * MEMBER       |   ✘   |     ✔      |  ✔  |   ✔
     * VIP          |   ✘   |     ✘      |  ✔  |   ✔
     * </pre>
     */
    @ParameterizedTest(name = "chương {0} + {1} → được phép: {2}")
    @CsvSource({
            "PUBLIC, guest,  true",
            "PUBLIC, member, true",
            "PUBLIC, vip,    true",
            "PUBLIC, admin,  true",

            "MEMBER, guest,  false",
            "MEMBER, member, true",
            "MEMBER, vip,    true",
            "MEMBER, admin,  true",

            "VIP,    guest,  false",
            "VIP,    member, false",
            "VIP,    vip,    true",
            "VIP,    admin,  true",
    })
    @DisplayName("Bảng phân quyền ở mục 3 của đề bài")
    void bangPhanQuyen(AccessLevel level, String userKind, boolean expected) {
        assertThat(accessControl.canAccess(level, byName(userKind))).isEqualTo(expected);
    }

    @Test
    @DisplayName("Chương không đặt mức khóa được coi là công khai")
    void mucKhoaNullCoiNhuPublic() {
        assertThat(accessControl.canAccess(null, guest())).isTrue();
    }

    @Test
    @DisplayName("Admin đọc được mọi mức khóa, kể cả khi không phải VIP")
    void adminDocDuocTatCa() {
        assertThat(admin().orElseThrow().isVip()).isFalse();

        for (AccessLevel level : AccessLevel.values()) {
            assertThat(accessControl.canAccess(level, admin()))
                    .as("Admin phải đọc được chương %s", level)
                    .isTrue();
        }
    }

    // ==================== requireAccess: chặn cứng ====================

    @Nested
    @DisplayName("requireAccess ném 403 và không để lộ nội dung")
    class RequireAccess {

        @Test
        @DisplayName("Khách mở chương VIP → ChapterLockedException, thông báo nhắc đăng nhập")
        void khachMoChuongVip() {
            Mockito.when(currentUserService.currentPrincipal()).thenReturn(guest());
            Mockito.when(currentUserService.isAuthenticated()).thenReturn(false);

            Chapter chapter = chapter(AccessLevel.VIP, "Nội dung tuyệt mật");

            assertThatThrownBy(() -> accessControl.requireAccess(chapter))
                    .isInstanceOf(ChapterLockedException.class)
                    .satisfies(thrown -> {
                        ChapterLockedException locked = (ChapterLockedException) thrown;
                        assertThat(locked.getRequiredAccessLevel()).isEqualTo(AccessLevel.VIP);
                        assertThat(locked.isAuthenticated()).isFalse();
                    })
                    // Điều quan trọng nhất: nội dung chương không được đi kèm ngoại lệ,
                    // vì thông báo lỗi sẽ được gửi thẳng về trình duyệt.
                    .hasMessageNotContaining("Nội dung tuyệt mật")
                    .hasMessageContaining("đăng nhập");
        }

        @Test
        @DisplayName("Thành viên thường mở chương VIP → thông báo mời nâng cấp")
        void memberMoChuongVip() {
            Mockito.when(currentUserService.currentPrincipal()).thenReturn(member());
            Mockito.when(currentUserService.isAuthenticated()).thenReturn(true);

            assertThatThrownBy(() -> accessControl.requireAccess(chapter(AccessLevel.VIP, "abc")))
                    .isInstanceOf(ChapterLockedException.class)
                    .hasMessageContaining("nâng cấp");
        }

        @ParameterizedTest(name = "VIP mở được chương {0}")
        @EnumSource(AccessLevel.class)
        @DisplayName("Đủ quyền thì đi qua, không ném gì")
        void duQuyenThiDiQua(AccessLevel level) {
            Mockito.when(currentUserService.currentPrincipal()).thenReturn(vip());

            assertThatCode(() -> accessControl.requireAccess(chapter(level, "abc")))
                    .doesNotThrowAnyException();
        }
    }

    private static Chapter chapter(AccessLevel level, String content) {
        return Chapter.builder()
                .id(10L)
                .title("Chương thử nghiệm")
                .chapterNumber(1)
                .accessLevel(level)
                .content(content)
                .build();
    }
}
