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

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final RatingCommentRepository ratingCommentRepository;
    private final VipPlanRepository vipPlanRepository;
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
        seedDiscussion(seedReaders());
        seedVipPlans();
    }

    /**
     * Ba gói mở hàng, để trang nâng cấp không trống trơn ở lần chạy đầu.
     *
     * <p>Chỉ chạy khi bảng còn rỗng: giá là thứ Admin sẽ sửa, và không có gì
     * khó chịu bằng việc nó tự quay về mặc định sau mỗi lần khởi động lại.
     */
    private void seedVipPlans() {
        if (vipPlanRepository.count() > 0) {
            return;
        }

        vipPlanRepository.saveAll(List.of(
                VipPlan.builder().name("VIP 1 tháng").months(1).priceVnd(49_000)
                        .description("Mở khóa toàn bộ chương VIP trong 30 ngày.")
                        .sortOrder(1).build(),
                VipPlan.builder().name("VIP 3 tháng").months(3).priceVnd(129_000)
                        .description("Tiết kiệm hơn khoảng 12% so với mua từng tháng.")
                        .sortOrder(2).build(),
                VipPlan.builder().name("VIP 12 tháng").months(12).priceVnd(449_000)
                        .description("Rẻ nhất tính theo tháng, hợp với người đọc đều.")
                        .sortOrder(3).build()));

        log.info("Đã tạo 3 gói VIP mẫu");
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
                .vipGranted(true)
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
                .vipGranted(vip)
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

    // ==================== Cộng đồng người đọc ====================

    /** Mật khẩu chung của nhóm tài khoản độc giả mẫu. */
    private static final String READER_PASSWORD = "Doc@123";

    private static final String[] READER_NAMES = {
            "Nguyễn Minh Khoa", "Trần Thị Hồng Nhung", "Lê Quang Huy", "Phạm Thu Trang",
            "Hoàng Văn Đạt", "Vũ Ngọc Mai", "Đặng Tuấn Kiệt", "Bùi Khánh Linh",
            "Đỗ Thanh Tùng", "Ngô Phương Anh", "Dương Hải Long", "Lý Thùy Dung",
            "Phan Đức Thắng", "Trịnh Bảo Châu", "Cao Minh Nhật", "Đinh Hoài Thu",
            "Lâm Gia Bảo", "Tạ Kim Ngân", "Hồ Trọng Nghĩa", "Mai Diệu Linh",
            "Chu Văn Hiếu", "Nguyễn Lan Hương", "Trần Đăng Khôi", "Lê Bích Ngọc",
            "Phạm Anh Quân", "Võ Thanh Hằng", "Đoàn Nhật Minh", "Trương Mỹ Duyên",
            "Huỳnh Chí Thành", "Nguyễn Hà My", "Lê Xuân Trường", "Phùng Thảo Vy",
            "Nguyễn Bá Tuấn", "Trần Yến Nhi", "Lưu Đình Phong", "Hà Quỳnh Anh",
            "Tô Văn Sơn", "Nguyễn Thị Kim Chi", "Đặng Hữu Phước", "Bạch Tuyết Mai",
            "Kiều Anh Dũng", "Nguyễn Ngọc Hân", "Phạm Hồng Sơn", "Lại Thu Hiền",
            "Đỗ Quốc Cường", "Nguyễn Vân Khánh", "Trần Hoàng Nam", "Ninh Thị Lệ Quyên",
            "Vương Tiến Đạt", "Nguyễn Thùy Linh"
    };

    /**
     * Tạo 50 tài khoản độc giả mẫu và trả về đúng thứ tự đó để phần bình luận
     * bên dưới gán người viết một cách ổn định giữa các lần chạy.
     * <p>
     * Cả 50 dùng chung một mật khẩu nên chỉ băm BCrypt một lần: băm 50 lần làm
     * lần khởi động đầu tiên chậm thêm vài giây mà chẳng đổi lấy được gì.
     */
    private List<User> seedReaders() {
        List<User> readers = new ArrayList<>(READER_NAMES.length);
        Instant now = Instant.now();
        String passwordHash = null;
        int created = 0;

        for (int i = 0; i < READER_NAMES.length; i++) {
            String username = "docgia%02d".formatted(i + 1);
            User reader = userRepository.findByUsername(username).orElse(null);

            if (reader == null) {
                if (passwordHash == null) {
                    passwordHash = passwordEncoder.encode(READER_PASSWORD);
                }
                reader = userRepository.save(User.builder()
                        .username(username)
                        .email(username + "@storytts.local")
                        .passwordHash(passwordHash)
                        .displayName(READER_NAMES[i])
                        .role(Role.MEMBER)
                        // Rải rác vài tài khoản VIP để trang quản trị có cả hai loại.
                        .vipGranted((i + 1) % 7 == 0)
                        .enabled(true)
                        // Ngày tham gia lùi dần về quá khứ, tránh 50 dòng cùng một mốc.
                        .createdAt(now.minus(Duration.ofDays(READER_NAMES.length - i)))
                        .build());
                created++;
            }
            readers.add(reader);
        }

        if (created > 0) {
            log.info("Đã tạo {} tài khoản độc giả mẫu (mật khẩu chung: {}).", created, READER_PASSWORD);
        }
        return readers;
    }

    /* Mỗi dòng là {số sao, nội dung}: bỏ trống số sao nghĩa là chỉ bình luận,
       bỏ trống nội dung nghĩa là chỉ chấm điểm — cả hai đều hợp lệ với API. */
    private static final String[][] LIGHTHOUSE_COMMENTS = {
            {"5", "Mở đầu đúng chất trinh thám: một chi tiết vô lý mà ai trong truyện cũng coi là bình thường. Đèn vẫn sáng đều trong khi người gác đã nghỉ ba tháng — đọc tới đó là không đặt xuống được nữa."},
            {"4", "Không khí truyện rất tốt, đọc vào buổi tối nghe mưa ngoài cửa sổ thì hợp vô cùng. Chỉ tiếc hai chương đầu hơi chậm."},
            {"5", "Nghe bằng giọng AI ở chương 2 thấy tự nhiên hơn mình tưởng, vừa nấu cơm vừa nghe hết luôn."},
            {"3", "Tình tiết ổn nhưng mình đoán ra người đứng sau từ chương 3. Mong tác giả giấu bài kỹ hơn ở phần sau."},
            {"", "Ai đọc tới chương 4 rồi cho mình hỏi cuốn sổ trực ban có được nhắc lại nữa không? Chi tiết đó cứ lởn vởn trong đầu mình mấy hôm nay."},
            {"5", ""},
            {"4", "Văn phong gọn, không lan man. Kiểu truyện mà câu nào cũng có lý do để nằm ở đó."},
            {"5", "Đọc một mạch từ chín giờ tối tới hai giờ sáng. Hôm sau đi làm mắt thâm quầng nhưng không hối hận."},
            {"4", "Đoạn tả biển đêm hay thật sự. Đọc mà nghe được cả tiếng sóng đập vào chân tháp."},
            {"", "Truyện này mà chuyển thành phim ngắn thì hợp lắm. Bối cảnh chỉ một ngọn hải đăng thôi cũng đủ."},
            {"5", "Mình thích cách tác giả không giải thích ngay. Cứ để người đọc tự thấy khó chịu vì những chỗ chưa khớp."},
            {"2", "Thú thật là mình hơi hụt hẫng. Ý tưởng hay nhưng phần giữa bị kéo dài, nhiều đoạn hội thoại lặp ý nhau."},
            {"4", ""},
            {"5", "Nhân vật người gác cũ được xây dựng rất có chiều sâu dù xuất hiện không nhiều. Đó mới là cái tài."},
            {"", "Cho mình hỏi truyện tuần này có ra chương mới không ạ? Mình hóng quá."},
            {"4", "Chương 3 phải khóa cho thành viên là hợp lý, vì đó đúng là chỗ hay nhất tới lúc này."},
            {"5", "Đọc xong chương 4 mình phải lật lại chương 1 để soi. Quả nhiên manh mối đã nằm ngay trước mắt từ đầu."},
            {"3", "Được, nhưng chưa tới. Cảm giác tác giả biết mình muốn kể gì mà chưa quyết được sẽ kể theo giọng nào."},
            {"5", "Truyện Việt viết trinh thám mà giữ được nhịp thế này thì hiếm. Ủng hộ tác giả dài dài."},
            {"4", "Nghe audio lúc chạy xe buýt đi làm, đúng bốn chặng là hết một chương. Rất vừa."},
            {"", "Mình mê mấy chi tiết nhỏ: cuốn nhật ký ghi sai ngày, cái đèn pin để ngược. Đọc kỹ mới thấy."},
            {"5", "Cái kết mở của chương 4 làm mình ngồi thần ra mất mấy phút. Chờ chương sau muốn ốm."},
            {"4", "Giọng nữ đọc chương 1 nghe dễ chịu, chỉ mong chỉnh tốc độ chậm lại chút nữa là chuẩn."},
            {"1", "Không hợp gu mình. Đọc được nửa chương đầu là bỏ, chắc tại mình thích nhịp nhanh hơn."},
            {"5", "Đã thêm vào tủ truyện. Loại truyện đọc lại lần hai vẫn thấy chi tiết mới."},
            {"4", "Phần tả sương mù buổi sớm ám ảnh phết. Đọc buổi trưa nắng mà vẫn thấy lạnh sống lưng."}
    };

    private static final String[][] FIRESIDE_COMMENTS = {
            {"5", "Đọc mà nhớ bà ngoại kinh khủng. Ngày xưa mỗi tối đông bà cũng kể kiểu này, kể tới đâu là cả nhà im tới đó."},
            {"4", "Truyện nhẹ nhàng, hợp đọc cho con trước giờ ngủ. Bé nhà mình đòi nghe lại chương 1 ba tối liền."},
            {"5", "Mình bật giọng đọc AI cho bà nội nghe, bà bảo nghe rõ và chậm vừa phải. Cảm ơn nhóm làm web."},
            {"", "Chương 2 có mẩu chuyện về con trâu và cái giếng làng đúng y bản bà mình từng kể, chỉ khác cái kết. Thú vị thật."},
            {"5", ""},
            {"4", "Văn hiền lành, không lên gân dạy dỗ ai. Truyện thiếu nhi kiểu này bây giờ ít lắm."},
            {"3", "Hay nhưng hơi ngắn. Mỗi mẩu vừa vào guồng thì đã hết, đọc chưa đã."},
            {"5", "Đọc xong tự nhiên muốn về quê. Mùi khói bếp trong truyện tả thật đến mức thấy cay mắt."},
            {"4", "Cho các bé lớp mình nghe trong tiết đọc sách, cả lớp ngồi yên nghe hết buổi. Xin phép lấy làm tư liệu nhé."},
            {"", "Có ai biết mẩu chuyện ông lão đốn củi ở chương 3 lấy từ vùng nào không ạ? Nghe quen mà không nhớ ra."},
            {"5", "Phần dặn dò cuối mỗi mẩu chuyện không lên lớp mà vẫn đọng lại. Mình thích cách viết này."},
            {"4", ""},
            {"5", "Truyện cổ tích mà đọc lúc ba mươi tuổi vẫn thấy hay, chắc vì giờ mình đọc bằng ký ức chứ không chỉ bằng mắt."},
            {"2", "Với người lớn thì hơi đơn giản. Nhưng nếu đọc cho trẻ con thì mình sẽ chấm cao hơn hẳn."},
            {"4", "Nghe audio chương 4 lúc dỗ con ngủ, được nửa chương thì cả hai bố con cùng ngủ mất."},
            {"5", "Cảm ơn tác giả đã chép lại những chuyện kiểu này. Bà mình mất rồi, giờ chẳng còn ai kể nữa."},
            {"", "Đề nghị làm thêm bản có nhạc nền tiếng lửa cháy lách tách thì tuyệt vời luôn."},
            {"4", "Câu chữ dễ đọc, con mình lớp ba tự đọc được hết mà không cần hỏi từ nào."},
            {"5", "Mỗi tối một mẩu, giờ thành thói quen của nhà mình rồi."},
            {"3", "Mấy mẩu đầu hay hơn mấy mẩu sau. Cảm giác về cuối tác giả viết hơi vội."},
            {"5", "Đọc giữa mùa hè mà vẫn tưởng tượng ra được cái rét ngoài sân và chỗ ngồi ấm nhất cạnh bếp."},
            {"4", "Mong có thêm phần hai. Kiểu truyện này càng nhiều mẩu càng quý."},
            {"", "Chương 3 khóa cho thành viên hơi tiếc, vì mình định gửi cho mấy đứa cháu chưa có tài khoản."},
            {"5", "Truyện ru người ta ngủ theo nghĩa đẹp nhất của từ đó. Năm sao."}
    };

    /**
     * Bình luận mẫu cho hai truyện, để phần đánh giá không trống trơn khi demo.
     * <p>
     * Chỉ chạy khi truyện đó chưa có bình luận nào, nên bình luận thật của người
     * dùng sẽ không bao giờ bị trộn thêm dữ liệu giả ở lần khởi động sau.
     */
    private void seedDiscussion(List<User> readers) {
        if (readers.isEmpty()) {
            return;
        }
        // Hai truyện lấy hai nhóm độc giả tách rời nhau, để không ai bình luận
        // hai lần dưới cùng một truyện.
        seedStoryDiscussion("Người Gác Hải Đăng", LIGHTHOUSE_COMMENTS, readers, 0);
        seedStoryDiscussion("Chuyện Kể Bên Bếp Lửa", FIRESIDE_COMMENTS, readers, LIGHTHOUSE_COMMENTS.length);
    }

    private void seedStoryDiscussion(String title, String[][] entries, List<User> readers, int firstReader) {
        Optional<Story> found = storyRepository.findFirstByTitle(title);
        if (found.isEmpty()) {
            log.debug("Bỏ qua bình luận mẫu: không tìm thấy truyện \"{}\".", title);
            return;
        }

        Story story = found.get();

        List<User> writers = new ArrayList<>(entries.length);
        for (int i = 0; i < entries.length; i++) {
            writers.add(readers.get((firstReader + i) % readers.size()));
        }

        // Hỏi riêng nhóm tài khoản mẫu chứ không hỏi cả bảng: bình luận thật của
        // người dùng nằm sẵn dưới truyện không được phép chặn việc tạo dữ liệu
        // mẫu, mà chạy lại lần sau cũng không được nhân đôi nó lên.
        List<Long> writerIds = writers.stream().map(User::getId).distinct().toList();
        if (ratingCommentRepository.countByStoryIdAndUserIdIn(story.getId(), writerIds) > 0) {
            return;
        }

        Instant now = Instant.now();
        List<RatingComment> batch = new ArrayList<>(entries.length);

        for (int i = 0; i < entries.length; i++) {
            String rating = entries[i][0];
            String comment = entries[i][1];

            batch.add(RatingComment.builder()
                    .story(story)
                    .user(writers.get(i))
                    .rating(rating.isEmpty() ? null : Integer.valueOf(rating))
                    .comment(comment.isEmpty() ? null : comment)
                    // Giãn đều về quá khứ, thêm chút lệch để danh sách "mới nhất
                    // trước" trông như tích lũy dần chứ không phải đổ vào một lượt.
                    .createdAt(now.minus(Duration.ofHours(11L * i + (i % 5) * 3L)))
                    .build());
        }

        ratingCommentRepository.saveAll(batch);
        log.info("Đã tạo {} bình luận mẫu cho truyện \"{}\".", batch.size(), title);
    }
}
