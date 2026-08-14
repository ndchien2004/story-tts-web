package com.storytts.backend.service.tts;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.storytts.backend.dto.audio.WordTimestampDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Cất mốc thời gian vào một cột, và lấy lại được.
 *
 * <h3>Vì sao là JSON trong một cột chứ không phải một bảng</h3>
 * Một chương hai mươi nghìn ký tự ra khoảng năm nghìn chữ. Đổ ra bảng riêng thì
 * mỗi bản audio là năm nghìn dòng, và không có truy vấn nào cần tới chúng lẻ ra:
 * chỗ duy nhất đọc mảng này là trình duyệt, và nó luôn đọc cả mảng một lần khi
 * mở chương. Dữ liệu chỉ được ghi một lần rồi đọc nguyên khối thì cột JSON đúng
 * là hình dạng của nó — bảng riêng chỉ thêm một triệu dòng và một phép nối.
 *
 * <p>Bản audio bị xóa thì mốc đi theo, vì nó nằm ngay trên dòng ấy. Đó cũng là
 * điều đúng: mốc thời gian không có nghĩa gì nếu tách khỏi file audio sinh ra nó.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TranscriptCodec {

    private static final TypeReference<List<WordTimestampDto>> WORD_LIST = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    /**
     * @return chuỗi JSON để cất vào cột, hoặc null khi không có gì để cất — cột
     *         null là cách nói "bản này không tô sáng được"
     */
    public String encode(List<WordTimestamp> words) {
        if (words == null || words.isEmpty()) {
            return null;
        }

        List<WordTimestampDto> payload = words.stream()
                .map(word -> new WordTimestampDto(
                        word.word(), word.start(), word.end(), word.charStart(), word.charEnd()))
                .toList();

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            // Không đáng để làm hỏng cả bản audio đã tổng hợp xong: mất phần tô
            // sáng thì trang đọc vẫn phát được, mất file audio thì không.
            log.warn("Không ghi được mốc thời gian ({} chữ): {}", words.size(), ex.getMessage());
            return null;
        }
    }

    /**
     * @return danh sách chữ, hoặc rỗng khi cột trống hoặc nội dung đã hỏng
     */
    public List<WordTimestampDto> decode(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<WordTimestampDto> words = objectMapper.readValue(json, WORD_LIST);
            return words == null ? List.of() : words;
        } catch (Exception ex) {
            log.warn("Cột mốc thời gian không đọc được: {}", ex.getMessage());
            return List.of();
        }
    }
}
