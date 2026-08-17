package com.storytts.backend.service;

import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đưa cơ sở dữ liệu về khớp với thực tế khi một bản audio không còn file.
 *
 * <h3>Vấn đề lớp này giải</h3>
 * Hàng trong {@code audio_files} sống trên MySQL đi thuê, còn file audio sống ở
 * một nơi khác. Hai nơi ấy lệch nhau được, và trong quá khứ đã lệch: bản dựng
 * trước khi chuyển sang lưu trữ lâu bền nằm trên hệ tệp tạm thời của Render và
 * đã biến mất, trong khi hàng vẫn ghi {@code status = READY}.
 *
 * <p>Một hàng READY trỏ vào hư không không chỉ làm hỏng lượt nghe. Nó còn <b>chặn
 * đường sửa</b>: bộ nhớ đệm của {@code TtsService} thấy đã có bản READY nên từ
 * chối dựng lại, và chương ấy kẹt vĩnh viễn ở trạng thái có audio mà không nghe
 * được. Đánh dấu FAILED là thứ mở lại đường ấy — người đọc bấm "Nghe bằng AI"
 * lần nữa là có bản mới.
 *
 * <h3>Vì sao sửa lúc phát chứ không rà soát lúc khởi động</h3>
 * Rà soát toàn bộ bảng thì mỗi hàng là một lượt hỏi nơi lưu trữ, tức một vòng
 * mạng. Trên Render gói miễn phí, dịch vụ ngủ sau mười lăm phút vắng khách và
 * khởi động lại từ đầu, nên một lượt rà soát "chỉ lúc khởi động" thật ra là liên
 * tục. Sửa ngay tại chỗ phát hiện thì tốn đúng một lần cho đúng hàng có vấn đề,
 * và tự khỏi dần mà không cần ai chạy gì.
 *
 * <h3>Vì sao là bean riêng với REQUIRES_NEW</h3>
 * Nơi gọi là một giao dịch chỉ đọc. Ghi vào đó không được, mà mở rộng nó thành
 * giao dịch ghi thì mọi lượt phát audio đều phải trả giá cho một tình huống hiếm.
 * Giao dịch riêng cũng là điều đúng về ngữ nghĩa: việc sửa này phải được giữ lại
 * kể cả khi lượt phát rốt cuộc trả về lỗi cho người dùng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AudioAssetRepair {

    static final String MESSAGE =
            "Bản audio này không còn trên máy chủ. Hãy bấm tạo lại để nghe.";

    private final AudioFileRepository audioFileRepository;

    /** Đánh dấu một bản audio là hỏng vì file của nó không còn. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markMissing(Long audioId, String key) {
        audioFileRepository.findById(audioId).ifPresent(audio -> {
            if (audio.getStatus() == AudioStatus.FAILED) {
                return;
            }
            audio.setStatus(AudioStatus.FAILED);
            audio.setErrorMessage(MESSAGE);
            audioFileRepository.save(audio);
            log.warn("Bản audio {} trỏ tới {} nhưng nơi lưu trữ không còn giữ nó; đã đánh dấu FAILED "
                    + "để chương này dựng lại được", audioId, key);
        });
    }
}
