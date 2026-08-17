package com.storytts.backend.service.importer;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Đếm những gì một lượt nhập đã làm, và ghi lại từng chỗ hỏng.
 *
 * <p>Lỗi được <b>thu về đây</b> thay vì được ném lên, và đó là toàn bộ lý do lớp
 * này tồn tại: một tệp JSON sai dấu phẩy ở chương 47 không được phép làm hỏng 46
 * chương đã nhập đúng trước nó, cũng không được phép giấu 50 chương sau nó. Cuối
 * lượt chạy, người vận hành cần một danh sách để sửa, không phải một vết ngăn xếp
 * của chỗ hỏng đầu tiên.
 */
@Getter
public class ImportReport {

    private int booksImported;
    private int chaptersCreated;
    private int chaptersUpdated;
    private int chaptersSkipped;
    private int audioUploaded;
    private int audioSkipped;

    private final List<String> failures = new ArrayList<>();

    public void bookImported() {
        booksImported++;
    }

    public void chapterCreated() {
        chaptersCreated++;
    }

    public void chapterUpdated() {
        chaptersUpdated++;
    }

    public void chapterSkipped() {
        chaptersSkipped++;
    }

    public void audioUploaded() {
        audioUploaded++;
    }

    public void audioSkipped() {
        audioSkipped++;
    }

    public void failed(String where, String why) {
        failures.add(where + ": " + why);
    }

    public boolean hasFailures() {
        return !failures.isEmpty();
    }

    /** Một dòng tóm tắt cho log; danh sách lỗi được in riêng bên dưới nó. */
    public String summary() {
        return ("truyện=%d  chương: tạo=%d cập nhật=%d bỏ qua=%d  "
                + "audio: tải lên=%d bỏ qua=%d  lỗi=%d")
                .formatted(booksImported, chaptersCreated, chaptersUpdated, chaptersSkipped,
                        audioUploaded, audioSkipped, failures.size());
    }
}
