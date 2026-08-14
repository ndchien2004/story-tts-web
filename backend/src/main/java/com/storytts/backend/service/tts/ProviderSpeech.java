package com.storytts.backend.service.tts;

import java.util.List;

/**
 * Thứ một nhà cung cấp trả về: audio, và — nếu nhà ấy nói được — mốc thời gian
 * của từng chữ.
 *
 * <p>Danh sách rỗng là một câu trả lời hợp lệ, không phải lỗi. Có nhà cung cấp
 * không hỗ trợ mốc thời gian, và ngay cả nhà có hỗ trợ cũng có thể trả về thiếu.
 * Cả hai trường hợp đều ra một bản audio nghe được — chỉ là trang đọc sẽ không
 * tô sáng theo giọng đọc được, nên nó phải hỏi được điều đó thay vì đoán.
 */
public record ProviderSpeech(byte[] audio, List<WordTimestamp> words) {

    public ProviderSpeech {
        words = words == null ? List.of() : List.copyOf(words);
    }

    /** Nhà cung cấp chỉ trả về tiếng, không kèm mốc thời gian nào. */
    public static ProviderSpeech audioOnly(byte[] audio) {
        return new ProviderSpeech(audio, List.of());
    }

    public boolean hasWords() {
        return !words.isEmpty();
    }
}
