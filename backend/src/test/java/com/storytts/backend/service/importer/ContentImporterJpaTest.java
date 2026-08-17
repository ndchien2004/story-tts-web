package com.storytts.backend.service.importer;

import com.storytts.backend.domain.AccessLevel;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.StoryStatus;
import com.storytts.backend.repository.AuthorRepository;
import com.storytts.backend.repository.ChapterRepository;
import com.storytts.backend.repository.GenreRepository;
import com.storytts.backend.repository.StoryRepository;
import com.storytts.backend.service.StorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Nhập nội dung, trên cơ sở dữ liệu thật.
 *
 * <p>Điều được giữ ở đây là <b>chạy lại không làm hỏng gì</b>. Một lượt nhập
 * hiếm khi trót lọt ngay lần đầu: thiếu một chương, sai một tệp, mất mạng giữa
 * chừng. Nên cách duy nhất dùng được của nó là chạy lại toàn bộ gói và tin rằng
 * phần đã đúng vẫn nguyên. Chỗ ấy chỉ chứng minh được trên cơ sở dữ liệu thật,
 * vì thứ chặn hàng trùng là ràng buộc duy nhất của lược đồ.
 *
 * <p>Điều thứ hai, quan trọng không kém: lượt nhập <b>không được đổi giá Xu hay
 * mức khóa</b> của chương đã tồn tại. Đó là dữ liệu nghiệp vụ có người đã trả
 * tiền cho nó, không phải thứ một lệnh nhập nội dung được phép ghi đè.
 */
@DataJpaTest
@Import({ContentImporter.class, ContentValidator.class})
class ContentImporterJpaTest {

    /** Nơi lưu file không tham gia vào những gì bài này kiểm. */
    @MockitoBean
    private StorageService storageService;

    @Autowired
    private ContentImporter importer;
    @Autowired
    private StoryRepository storyRepository;
    @Autowired
    private ChapterRepository chapterRepository;
    @Autowired
    private AuthorRepository authorRepository;
    @Autowired
    private GenreRepository genreRepository;

    private static ContentPackage.Book sach() {
        return new ContentPackage.Book(
                "Người đưa thư", "Nguyễn Văn A", "Trinh thám",
                "Một câu chuyện thử.", "ONGOING", null);
    }

    private static ContentPackage.Chapter chuong(int so) {
        return new ContentPackage.Chapter(
                so, "Chương " + so, "Nội dung chương " + so,
                "PUBLIC", 0L, null, null);
    }

    @Test
    @DisplayName("nhập lần đầu thì dựng truyện, tác giả và thể loại")
    void nhapLanDau() {
        Long storyId = importer.upsertStory(sach());

        assertThat(storyRepository.findById(storyId)).hasValueSatisfying(story -> {
            assertThat(story.getTitle()).isEqualTo("Người đưa thư");
            assertThat(story.getAuthor().getName()).isEqualTo("Nguyễn Văn A");
            assertThat(story.getGenre().getName()).isEqualTo("Trinh thám");
            assertThat(story.getStatus()).isEqualTo(StoryStatus.ONGOING);
        });
    }

    @Test
    @DisplayName("nhập lại cùng một gói không sinh thêm truyện, tác giả hay thể loại nào")
    void nhapLaiKhongNhanDoi() {
        Long lanMot = importer.upsertStory(sach());
        Long lanHai = importer.upsertStory(sach());

        assertThat(lanHai).isEqualTo(lanMot);
        assertThat(storyRepository.count()).isEqualTo(1);
        assertThat(authorRepository.count()).isEqualTo(1);
        assertThat(genreRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("nhập lại chương thì cập nhật nội dung, không tạo hàng thứ hai")
    void nhapLaiChuongThiCapNhat() {
        Long storyId = importer.upsertStory(sach());

        assertThat(importer.upsertChapter(storyId, chuong(1)))
                .isEqualTo(ContentImporter.ChapterOutcome.CREATED);

        ContentPackage.Chapter suaLai = new ContentPackage.Chapter(
                1, "Chương 1 (đã sửa)", "Nội dung mới", "PUBLIC", 0L, null, null);

        assertThat(importer.upsertChapter(storyId, suaLai))
                .isEqualTo(ContentImporter.ChapterOutcome.UPDATED);

        assertThat(chapterRepository.findByStoryIdOrderByChapterNumberAsc(storyId)).hasSize(1);
        assertThat(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1))
                .hasValueSatisfying(chapter -> {
                    assertThat(chapter.getTitle()).isEqualTo("Chương 1 (đã sửa)");
                    assertThat(chapter.getContent()).isEqualTo("Nội dung mới");
                });
    }

    /**
     * Chỗ này là ranh giới với Task 1/2. Giá Xu và mức khóa của một chương đã
     * tồn tại là thứ quản trị viên đặt và người đọc đã trả tiền theo; một lượt
     * nhập nội dung chạy lại không được phép đưa chúng về giá trị trong tệp.
     */
    @Test
    @DisplayName("nhập lại KHÔNG đổi giá Xu và mức khóa của chương đã có")
    void nhapLaiKhongDoiGiaVaMucKhoa() {
        Long storyId = importer.upsertStory(sach());
        importer.upsertChapter(storyId, chuong(1));

        // Quản trị viên đổi chương này thành VIP, giá 50 Xu.
        Chapter chapter = chapterRepository.findByStoryIdAndChapterNumber(storyId, 1).orElseThrow();
        chapter.setAccessLevel(AccessLevel.VIP);
        chapter.setCoinPrice(50L);
        chapterRepository.saveAndFlush(chapter);

        // Gói nội dung vẫn khai PUBLIC / 0 Xu, và chạy lại lượt nhập.
        importer.upsertChapter(storyId, chuong(1));

        assertThat(chapterRepository.findByStoryIdAndChapterNumber(storyId, 1))
                .hasValueSatisfying(sau -> {
                    assertThat(sau.getAccessLevel()).isEqualTo(AccessLevel.VIP);
                    assertThat(sau.getCoinPrice()).isEqualTo(50L);
                });
    }

    @Test
    @DisplayName("chương mới thì giá Xu và mức khóa trong gói được dùng")
    void chuongMoiThiNhanGiaTuGoi() {
        Long storyId = importer.upsertStory(sach());
        importer.upsertChapter(storyId, new ContentPackage.Chapter(
                7, "Chương 7", "Nội dung", "VIP", 30L, null, null));

        assertThat(chapterRepository.findByStoryIdAndChapterNumber(storyId, 7))
                .hasValueSatisfying(chapter -> {
                    assertThat(chapter.getAccessLevel()).isEqualTo(AccessLevel.VIP);
                    assertThat(chapter.getCoinPrice()).isEqualTo(30L);
                });
    }

    @Test
    @DisplayName("gói thiếu trường bắt buộc thì bị chặn trước khi ghi gì")
    void goiThieuTruongBiChan() {
        assertThatThrownBy(() -> importer.upsertStory(
                new ContentPackage.Book(null, "Tác giả", "Thể loại", null, null, null)))
                .isInstanceOf(ContentValidator.InvalidPackageException.class)
                .hasMessageContaining("title");

        assertThat(storyRepository.count()).isZero();
    }

    @Test
    @DisplayName("số chương không hợp lệ thì bị chặn")
    void soChuongKhongHopLe() {
        Long storyId = importer.upsertStory(sach());

        assertThatThrownBy(() -> importer.upsertChapter(storyId, new ContentPackage.Chapter(
                0, "Chương 0", "Nội dung", null, null, null, null)))
                .isInstanceOf(ContentValidator.InvalidPackageException.class)
                .hasMessageContaining("chapterNumber");
    }

    @Test
    @DisplayName("khai audio mà không khai băm thì bị chặn")
    void khaiAudioMaKhongKhaiBam() {
        Long storyId = importer.upsertStory(sach());

        assertThatThrownBy(() -> importer.upsertChapter(storyId, new ContentPackage.Chapter(
                1, "Chương 1", "Nội dung", null, null, "audio/001.mp3", null)))
                .isInstanceOf(ContentValidator.InvalidPackageException.class)
                .hasMessageContaining("audioSha256");
    }
}
