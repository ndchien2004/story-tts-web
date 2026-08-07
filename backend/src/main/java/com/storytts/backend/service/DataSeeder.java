package com.storytts.backend.service;

import com.storytts.backend.config.AdminProperties;
import com.storytts.backend.domain.*;
import com.storytts.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tạo tài khoản Admin và dữ liệu mẫu ở lần chạy đầu tiên.
 * <p>
 * Bộ dữ liệu cố tình có đủ cả ba mức khóa PUBLIC / MEMBER / VIP để kiểm thử
 * chức năng khóa chương bằng ba tài khoản khác nhau.
 * Toàn bộ nội dung truyện ở đây là văn bản mẫu tự soạn, chỉ dùng để demo và chạy thử TTS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final GenreRepository genreRepository;
    private final AuthorRepository authorRepository;
    private final StoryRepository storyRepository;
    private final ChapterRepository chapterRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @Value("${app.seed.enabled:true}")
    private boolean seedEnabled;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAdmin();
        if (!seedEnabled) {
            return;
        }
        seedDemoUsers();
        seedCatalog();
    }

    // ==================== Tài khoản ====================

    private void seedAdmin() {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }
        User admin = User.builder()
                .username(adminProperties.username())
                .email(adminProperties.email())
                .passwordHash(passwordEncoder.encode(adminProperties.password()))
                .displayName("Quản trị viên")
                .role(Role.ADMIN)
                .vip(true)
                .enabled(true)
                .build();
        userRepository.save(admin);
        log.info("Đã tạo tài khoản Admin đầu tiên: {}", admin.getUsername());
    }

    /** Hai tài khoản demo để đối chiếu quyền: một Member thường và một Member VIP. */
    private void seedDemoUsers() {
        createUserIfMissing("member", "member@storytts.local", "Member@123", "Thành viên thường", false);
        createUserIfMissing("vipuser", "vip@storytts.local", "Vip@123", "Thành viên VIP", true);
    }

    private void createUserIfMissing(String username, String email, String rawPassword,
                                     String displayName, boolean vip) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .displayName(displayName)
                .role(Role.MEMBER)
                .vip(vip)
                .enabled(true)
                .build());
        log.info("Đã tạo tài khoản demo: {} (VIP: {})", username, vip);
    }

    // ==================== Truyện mẫu ====================

    private void seedCatalog() {
        if (storyRepository.count() > 0) {
            return;
        }

        Map<String, Genre> genres = new LinkedHashMap<>();
        for (String[] g : new String[][]{
                {"Kiếm hiệp", "Truyện võ hiệp, giang hồ, ân oán."},
                {"Trinh thám", "Truyện phá án, suy luận, bí ẩn."},
                {"Ngôn tình", "Truyện tình cảm nhẹ nhàng."},
                {"Khoa học viễn tưởng", "Truyện về tương lai và công nghệ."},
                {"Cổ tích", "Truyện dân gian, thiếu nhi."}
        }) {
            genres.put(g[0], genreRepository.save(
                    Genre.builder().name(g[0]).description(g[1]).build()));
        }

        Map<String, Author> authors = new LinkedHashMap<>();
        for (String[] a : new String[][]{
                {"Hoài Nam", "Tác giả mẫu, chuyên viết truyện dài kỳ."},
                {"Lê Minh Anh", "Tác giả mẫu, sở trường truyện trinh thám."},
                {"Trần Bảo Ngọc", "Tác giả mẫu, viết truyện tình cảm."},
                {"Phạm Quốc Việt", "Tác giả mẫu, viết truyện viễn tưởng."}
        }) {
            authors.put(a[0], authorRepository.save(
                    Author.builder().name(a[0]).bio(a[1]).build()));
        }

        createStory("Gió Qua Đèo Vắng", authors.get("Hoài Nam"), genres.get("Kiếm hiệp"),
                StoryStatus.ONGOING,
                "Một thiếu niên rời làng chài lên đường tìm người thầy đã mất tích, "
                        + "mang theo cây đao gãy và một lời hứa chưa kịp giữ.");

        createStory("Căn Phòng Số Bảy", authors.get("Lê Minh Anh"), genres.get("Trinh thám"),
                StoryStatus.COMPLETED,
                "Một vụ mất tích trong khách sạn cũ, nơi mọi nhân chứng đều kể một câu chuyện khác nhau "
                        + "về cùng một đêm mưa.");

        createStory("Mùa Hạ Không Có Ve", authors.get("Trần Bảo Ngọc"), genres.get("Ngôn tình"),
                StoryStatus.ONGOING,
                "Hai người bạn cũ gặp lại nhau ở thành phố mà cả hai từng hẹn sẽ rời đi.");

        createStory("Trạm Cuối Sao Hỏa", authors.get("Phạm Quốc Việt"), genres.get("Khoa học viễn tưởng"),
                StoryStatus.ONGOING,
                "Kỹ sư cuối cùng ở trạm trung chuyển nhận được tín hiệu từ một con tàu đáng lẽ không còn tồn tại.");

        createStory("Chuyện Kể Bên Bếp Lửa", authors.get("Hoài Nam"), genres.get("Cổ tích"),
                StoryStatus.COMPLETED,
                "Tập hợp những mẩu chuyện ngắn được bà kể lại trong những đêm đông.");

        createStory("Người Gác Hải Đăng", authors.get("Lê Minh Anh"), genres.get("Trinh thám"),
                StoryStatus.ONGOING,
                "Ngọn hải đăng vẫn sáng đều mỗi đêm, dù người gác đã nghỉ việc từ ba tháng trước.");

        log.info("Đã tạo {} truyện mẫu với đủ ba mức khóa chương.", storyRepository.count());
    }

    /** Mỗi truyện tạo 4 chương: 2 PUBLIC, 1 MEMBER, 1 VIP — để thấy rõ khác biệt khi đổi tài khoản. */
    private void createStory(String title, Author author, Genre genre,
                             StoryStatus status, String description) {
        Story story = storyRepository.save(Story.builder()
                .title(title)
                .author(author)
                .genre(genre)
                .description(description)
                .status(status)
                .coverImage(null)
                .build());

        List<Chapter> chapters = new ArrayList<>();
        chapters.add(buildChapter(story, 1, "Chương 1: Khởi đầu", AccessLevel.PUBLIC,
                sampleContent(title, "mở đầu")));
        chapters.add(buildChapter(story, 2, "Chương 2: Ngã rẽ", AccessLevel.PUBLIC,
                sampleContent(title, "chuyển biến")));
        chapters.add(buildChapter(story, 3, "Chương 3: Bí mật hé lộ", AccessLevel.MEMBER,
                sampleContent(title, "cao trào")));
        chapters.add(buildChapter(story, 4, "Chương 4: Hồi kết tạm thời", AccessLevel.VIP,
                sampleContent(title, "kết thúc mở")));

        chapterRepository.saveAll(chapters);
    }

    private Chapter buildChapter(Story story, int number, String title,
                                 AccessLevel accessLevel, String content) {
        return Chapter.builder()
                .story(story)
                .chapterNumber(number)
                .title(title)
                .accessLevel(accessLevel)
                .content(content)
                .build();
    }

    /**
     * Sinh nội dung mẫu đủ dài để thử nghiệm TTS nhưng không quá dài gây tốn quota API.
     * Khi dùng thật, Admin sẽ thay bằng nội dung truyện của mình.
     */
    private String sampleContent(String storyTitle, String phase) {
        return """
                Đây là nội dung mẫu thuộc phần %s của truyện "%s".

                Trời vừa hửng sáng thì con đường mòn đã đầy sương. Người đi trước không quay lại,
                người đi sau cũng không lên tiếng gọi. Cả hai đều hiểu rằng chặng đường phía trước
                không còn chỗ cho những câu hỏi dễ trả lời.

                Gió thổi ngang qua triền dốc, cuốn theo mùi đất ẩm và tiếng chim gọi bầy từ phía thung lũng.
                Có những chuyện đã bắt đầu từ rất lâu trước khi ai đó kịp nhận ra nó đang bắt đầu.

                Đoạn văn bản này được dùng để minh họa giao diện đọc truyện và để thử chức năng
                chuyển văn bản thành giọng nói. Bạn có thể bấm nút "Nghe bằng AI" để hệ thống
                gọi dịch vụ Text-to-Speech và tạo ra file âm thanh cho chương này.
                """.formatted(phase, storyTitle);
    }
}
