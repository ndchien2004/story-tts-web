package com.storytts.backend.service;

import com.storytts.backend.service.storage.ByteRange;
import com.storytts.backend.service.storage.MediaKind;
import com.storytts.backend.service.storage.MediaSlice;
import com.storytts.backend.service.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.Duration;

/**
 * Cửa vào duy nhất tới nơi lưu media, cho toàn bộ tầng nghiệp vụ.
 *
 * <p>Chỉ có khóa lưu trữ được cất vào cơ sở dữ liệu; việc khóa ấy nghĩa là một
 * tên file trên đĩa hay một public_id trên Cloudinary là chuyện của
 * {@link MediaStorage} và của cấu hình, không phải chuyện của nơi gọi. Trước đây
 * lớp này tự ghi đĩa, và chính điều đó khiến "audio nằm ở đâu" trở thành một giả
 * định nằm rải rác khắp mã nguồn thay vì một lựa chọn có thể đổi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final MediaStorage storage;

    /* ---------------------------------------------------------------- */
    /* Ghi                                                               */
    /* ---------------------------------------------------------------- */

    /** Lưu audio đã có sẵn trong bộ nhớ và trả về khóa lưu trữ. */
    public String storeAudio(byte[] content, String extension) {
        return storage.store(content, MediaKind.AUDIO, extension);
    }

    /** Lưu audio từ một luồng và trả về khóa lưu trữ. */
    public String storeAudio(InputStream input, String extension) {
        return storage.store(input, MediaKind.AUDIO, extension);
    }

    /**
     * Lưu một bản nhạc nền.
     *
     * <p>Ngăn riêng chứ không phải một tiền tố trong ngăn audio: audio chương là
     * dữ liệu dựng lại được, nhạc nền thì không. Tách ra nghĩa là một lượt dọn
     * dẹp quét ngăn audio không thể mang kho nhạc đi theo.
     */
    public String storeBgm(InputStream input, String extension) {
        return storage.store(input, MediaKind.BGM, extension);
    }

    /* ---------------------------------------------------------------- */
    /* Đọc                                                               */
    /* ---------------------------------------------------------------- */

    /**
     * Mở một lát audio để phát.
     *
     * @param range khoảng byte trình phát hỏi tới, hay null để lấy trọn file
     * @throws com.storytts.backend.service.storage.MediaNotFoundException
     *         nếu khóa không còn ứng với dữ liệu nào
     */
    public MediaSlice openAudio(String key, ByteRange range) {
        return storage.open(key, MediaKind.AUDIO, range);
    }

    public MediaSlice openBgm(String key, ByteRange range) {
        return storage.open(key, MediaKind.BGM, range);
    }

    /** Có còn dữ liệu sau khóa này không. Đừng gọi trên đường phát — xem {@link MediaStorage#exists}. */
    public boolean audioExists(String key) {
        return storage.exists(key, MediaKind.AUDIO);
    }

    /* ---------------------------------------------------------------- */
    /* Xóa và dọn                                                        */
    /* ---------------------------------------------------------------- */

    /** Xóa tạm được; file vốn đã không còn thì không phải lỗi. */
    public void deleteAudio(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        storage.delete(key, MediaKind.AUDIO);
    }

    public void deleteBgm(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        storage.delete(key, MediaKind.BGM);
    }

    /** Dọn những file ghi dở mà một lượt ghi hỏng để lại. */
    public int sweepTemporary(Duration olderThan) {
        return storage.sweepTemporary(olderThan);
    }
}
