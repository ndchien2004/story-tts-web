package com.storytts.backend.service;

import com.storytts.backend.domain.BgmTrack;
import com.storytts.backend.dto.bgm.BgmTrackDto;
import com.storytts.backend.dto.bgm.BgmTrackRequest;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.BgmTrackRepository;
import com.storytts.backend.service.storage.MediaNotFoundException;
import com.storytts.backend.service.storage.MediaSlice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Kho nhạc nền dùng chung: quản trị viên tải lên, mọi người nghe cùng chọn.
 *
 * <p>Lý do tính năng này tồn tại: trước đây ô "chọn nhạc nền" của trang đọc chỉ
 * có hai nguồn — một tệp manifest đi kèm bản build, và tệp người nghe tự mở từ
 * máy họ. Nghĩa là trên một máy chủ đã chạy, người vừa mở trang lần đầu luôn
 * thấy ô ấy trống, và cách duy nhất để thêm nhạc là build lại cả frontend.
 *
 * <p>Giới hạn kích thước nhỏ hơn hẳn audio chương, và có lý do: bản nhạc nền
 * được tải trọn về rồi giải mã vào bộ nhớ trình duyệt để lặp cho liền mạch
 * (xem {@code AudioMixerEngine}), nên một file lớn ở đây là một quãng chờ dài
 * trước khi nghe được, nhân với mọi người nghe.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BgmService {

    /** Nhạc nền là bản vài phút lặp lại; quá mức này thì gần như chắc chắn là nhầm file. */
    private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave",
            "audio/ogg", "audio/opus", "audio/mp4", "audio/x-m4a", "audio/aac", "audio/webm");

    private final BgmTrackRepository bgmTrackRepository;
    private final StorageService storageService;

    /* ---------------------------------------------------------------- */
    /* Đọc                                                               */
    /* ---------------------------------------------------------------- */

    /** Những bản người nghe được chọn. Công khai: nhạc nền không khóa theo quyền. */
    @Transactional(readOnly = true)
    public List<BgmTrackDto> listActive() {
        return bgmTrackRepository.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(BgmTrackDto::from)
                .toList();
    }

    /** Cả bản đã tắt — khu quản trị cần thấy chúng để bật lại. */
    @Transactional(readOnly = true)
    public List<BgmTrackDto> listAll() {
        return bgmTrackRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(BgmTrackDto::from)
                .toList();
    }

    /**
     * Mở một bản nhạc để phát.
     *
     * <p>Bản đã tắt vẫn phát được: người nghe có thể đang nghe dở khi quản trị
     * viên gỡ nó khỏi danh sách, và cắt tiếng giữa chương là cái giá cao hơn hẳn
     * so với việc để một bản đã rút phát nốt. Rút hẳn thì xóa.
     */
    @Transactional(readOnly = true)
    public MediaSlice openForStreaming(Long id) {
        BgmTrack track = findEntity(id);
        MediaSlice slice;
        try {
            // Không truyền khoảng byte: trình duyệt tải trọn bản nhạc rồi giải mã
            // vào bộ nhớ để lặp cho liền mạch, nên nó không bao giờ hỏi một khúc
            // giữa. Xem javadoc của BgmController.
            slice = storageService.openBgm(track.getFilePath(), null);
        } catch (MediaNotFoundException ex) {
            throw ResourceNotFoundException.of("bản nhạc nền", id);
        }

        String contentType = track.getContentType() != null
                ? track.getContentType()
                : slice.contentType();
        return new MediaSlice(slice.body(), slice.contentLength(), slice.totalLength(),
                slice.start(), slice.end(), contentType, slice.partial());
    }

    /* ---------------------------------------------------------------- */
    /* Ghi                                                               */
    /* ---------------------------------------------------------------- */

    /**
     * Nhận một bản nhạc nền mới.
     *
     * <p>File xuống đĩa trước mọi câu lệnh SQL, cùng lý do như
     * {@code AudioService.uploadForChapter}: Hibernate chỉ lấy kết nối ở câu lệnh
     * đầu tiên, nên chép file trước là chép ngoài lúc đang cầm kết nối. Ghi cơ sở
     * dữ liệu hỏng thì file vừa ghi được dọn ở khối catch.
     */
    @Transactional
    public BgmTrackDto upload(MultipartFile file, String title, String credit) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn file nhạc để tải lên.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BadRequestException("File nhạc nền vượt quá 20 MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(
                    "Định dạng không được hỗ trợ. Vui lòng dùng file MP3, WAV, OGG hoặc M4A.");
        }

        String name = normaliseTitle(title, file.getOriginalFilename());

        String fileName;
        try {
            fileName = storageService.storeBgm(
                    file.getInputStream(), extensionOf(file.getOriginalFilename()));
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được file tải lên.");
        }

        try {
            BgmTrack track = BgmTrack.builder()
                    .title(name)
                    .credit(blankToNull(credit))
                    .filePath(fileName)
                    .contentType(contentType)
                    .fileSize(file.getSize())
                    .active(true)
                    // Mới lên thì xuống cuối danh sách, chứ không chen lên đầu ô chọn.
                    .sortOrder(nextSortOrder())
                    .build();

            BgmTrack saved = bgmTrackRepository.save(track);
            log.info("Uploaded background music '{}' ({} bytes)", name, file.getSize());
            return BgmTrackDto.from(saved);
        } catch (RuntimeException ex) {
            storageService.deleteBgm(fileName);
            throw ex;
        }
    }

    @Transactional
    public BgmTrackDto update(Long id, BgmTrackRequest request) {
        BgmTrack track = findEntity(id);
        track.setTitle(request.title().trim());
        track.setCredit(blankToNull(request.credit()));
        if (request.active() != null) {
            track.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            track.setSortOrder(request.sortOrder());
        }
        return BgmTrackDto.from(bgmTrackRepository.save(track));
    }

    @Transactional
    public BgmTrackDto setActive(Long id, boolean active) {
        BgmTrack track = findEntity(id);
        track.setActive(active);
        return BgmTrackDto.from(bgmTrackRepository.save(track));
    }

    @Transactional
    public void delete(Long id) {
        BgmTrack track = findEntity(id);
        storageService.deleteBgm(track.getFilePath());
        bgmTrackRepository.delete(track);
    }

    /* ---------------------------------------------------------------- */

    private BgmTrack findEntity(Long id) {
        return bgmTrackRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("bản nhạc nền", id));
    }

    private int nextSortOrder() {
        return bgmTrackRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .mapToInt(track -> track.getSortOrder() == null ? 0 : track.getSortOrder())
                .max()
                .orElse(0) + 1;
    }

    /** Không đặt tên thì tên file thành tên bản nhạc, bỏ phần đuôi. */
    private String normaliseTitle(String title, String originalName) {
        if (title != null && !title.isBlank()) {
            return title.trim().length() > 150 ? title.trim().substring(0, 150) : title.trim();
        }
        String fallback = originalName == null || originalName.isBlank() ? "Nhạc nền" : originalName;
        int dot = fallback.lastIndexOf('.');
        String stem = dot > 0 ? fallback.substring(0, dot) : fallback;
        return stem.length() > 150 ? stem.substring(0, 150) : stem;
    }

    private String extensionOf(String originalName) {
        if (originalName == null) {
            return ".mp3";
        }
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 ? originalName.substring(dot) : ".mp3";
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
