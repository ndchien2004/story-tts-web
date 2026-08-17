package com.storytts.backend.service;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.BgmTrack;
import com.storytts.backend.dto.admin.StorageAuditDto;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.BgmTrackRepository;
import com.storytts.backend.service.storage.MediaKind;
import com.storytts.backend.service.storage.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Đối chiếu cơ sở dữ liệu với nơi lưu file, theo yêu cầu của quản trị viên.
 *
 * <h3>Vì sao là một lượt chạy tay chứ không phải một lịch chạy nền</h3>
 * Mỗi hàng cần một lượt hỏi nơi lưu trữ, và với Cloudinary thì mỗi lượt hỏi là
 * một vòng mạng. Đặt nó vào lúc khởi động — nghe có vẻ vô hại — thật ra là đặt
 * nó vào <b>vài chục lần mỗi ngày</b>: dịch vụ ngủ sau mười lăm phút vắng khách
 * rồi khởi động lại từ đầu. Một lượt rà soát trăm hàng như thế vừa chậm vừa
 * tiêu credit cho một việc không ai đọc kết quả.
 *
 * <p>Đường tự chữa nằm ở chỗ khác và rẻ hơn nhiều: {@link AudioAssetRepair} sửa
 * đúng một hàng, đúng lúc phát hiện ra, ngay trên đường phát. Chỗ này dành cho
 * khi cần một bức tranh toàn cảnh — sau một lượt phục hồi, hay khi nghi ngờ hai
 * bên đã lệch nhau.
 *
 * <h3>Không tự xóa gì</h3>
 * Báo cáo trước, hành động sau, và hành động cũng chỉ là đánh dấu FAILED để
 * chương dựng lại được. Không có đường nào ở đây xóa file, vì một lượt rà soát
 * hiểu nhầm mà đi xóa thì thứ mất đi là bản audio thật.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageAuditService {

    private final AudioFileRepository audioFileRepository;
    private final BgmTrackRepository bgmTrackRepository;
    private final MediaStorage storage;
    private final AudioAssetRepair audioAssetRepair;

    /** Những bản ghi trỏ tới file không còn tồn tại. */
    @Transactional(readOnly = true)
    public StorageAuditDto audit() {
        List<StorageAuditDto.MissingAsset> missing = new ArrayList<>();

        List<AudioFile> ready = audioFileRepository.findByStatus(AudioStatus.READY);
        for (AudioFile audio : ready) {
            if (audio.getFilePath() != null && !storage.exists(audio.getFilePath(), MediaKind.AUDIO)) {
                missing.add(new StorageAuditDto.MissingAsset(
                        "AUDIO", audio.getId(), audio.getChapter().getId(), audio.getFilePath()));
            }
        }

        List<BgmTrack> tracks = bgmTrackRepository.findAll();
        for (BgmTrack track : tracks) {
            if (track.getFilePath() != null && !storage.exists(track.getFilePath(), MediaKind.BGM)) {
                missing.add(new StorageAuditDto.MissingAsset(
                        "BGM", track.getId(), null, track.getFilePath()));
            }
        }

        return new StorageAuditDto(
                storage.describe(), ready.size(), tracks.size(), missing.size(), missing);
    }

    /**
     * Đánh dấu mọi bản audio mất file là hỏng, để chúng dựng lại được.
     *
     * <p>Chỉ chạm vào {@code audio_files}. Nhạc nền thì không: nó là file quản
     * trị viên tải lên một lần và không có nguồn nào dựng lại, nên "đánh dấu
     * hỏng" chẳng mở ra đường sửa nào — việc cần làm là tải lên lại, và đó là
     * quyết định của người, không phải của một lượt rà soát.
     */
    @Transactional(readOnly = true)
    public StorageAuditDto repairMissingAudio() {
        StorageAuditDto report = audit();
        report.missing().stream()
                .filter(asset -> "AUDIO".equals(asset.kind()))
                .forEach(asset -> audioAssetRepair.markMissing(asset.id(), asset.key()));
        return report;
    }
}
