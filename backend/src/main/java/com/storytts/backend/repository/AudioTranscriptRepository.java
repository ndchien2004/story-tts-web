package com.storytts.backend.repository;

import com.storytts.backend.domain.AudioTranscript;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Đọc/ghi mốc thời gian của một bản audio.
 *
 * <p>Chỉ có tra theo khóa chính, và đó là toàn bộ nhu cầu: mốc thời gian luôn
 * được hỏi tới cùng lúc với một bản audio cụ thể, không bao giờ được duyệt hay
 * lọc. Không có phương thức xóa nào ở đây vì không đường nào cần — khóa ngoại
 * bên cơ sở dữ liệu dọn giúp khi bản audio bị xóa.
 */
public interface AudioTranscriptRepository extends JpaRepository<AudioTranscript, Long> {
}
