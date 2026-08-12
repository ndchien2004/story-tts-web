package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Cấu hình chuyển văn bản thành giọng nói.
 *
 * <p>API key đọc từ biến môi trường hoặc file .env, không bao giờ nằm trong mã nguồn.
 *
 * @param providers    id các nhà cung cấp theo thứ tự sẽ thử; cái nào chưa có
 *                     key thì tự động bị bỏ qua
 * @param defaultSpeed tốc độ dùng khi lời gọi không nêu, đồng thời là một phần
 *                     khóa cache của bản audio
 * @param reader       ngưỡng cho đường người đọc tự bấm tạo audio
 */
@ConfigurationProperties(prefix = "app.tts")
public record TtsProperties(
        boolean enabled,
        List<String> providers,
        int defaultSpeed,
        ElevenLabs elevenlabs,
        Reader reader
) {

    /**
     * Khối {@code reader} vắng mặt trong cấu hình vẫn phải ra một bộ ngưỡng dùng
     * được — nếu không thì thiếu vài dòng properties là hỏng cả lúc khởi động.
     */
    public TtsProperties {
        reader = reader == null ? Reader.defaults() : reader;
    }

    /**
     * @param voiceId giọng lấy từ tài khoản, dùng khi lời gọi không chỉ định giọng nào
     * @param modelId bắt buộc là model đa ngôn ngữ thì tiếng Việt mới đọc đúng
     */
    public record ElevenLabs(
            String endpoint,
            String apiKey,
            String voiceId,
            String modelId
    ) {
        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    /**
     * Ngân sách cho nút "Nghe bằng AI" của người đọc (mục 4.5 của đề bài).
     *
     * <p>Mỗi lần dựng một bản audio mới là một lần gọi API tính tiền, nên đường
     * này không thể mở trần như đường đọc chữ. Toàn bộ ngưỡng nằm ở đây chứ
     * không rải trong code: hạ một con số là siết lại được ngay, và
     * {@code enabled=false} là tắt hẳn mà console quản trị vẫn dựng audio bình thường.
     *
     * <p>Không có tùy chọn mở cho Khách. Không phải vì sợ khách, mà vì không có
     * danh tính thì không có gì để đếm hạn mức lên: một đường vô danh sẽ chỉ còn
     * trần chung chặn, tức một người bền bỉ là đủ tiêu hết phần của cả ngày.
     *
     * @param dailyQuota        số bản audio mới một thành viên được tạo trong ngày;
     *                          -1 là không giới hạn, 0 là chặn hẳn
     * @param dailyQuotaVip     hạn mức riêng cho VIP
     * @param dailyQuotaGlobal  trần chung cho toàn bộ người đọc trong ngày — cầu dao
     *                          cuối, chặn cả trường hợp nhiều người cùng đốt quota
     * @param maxChars          chương dài quá thì không cho người đọc tự dựng; một
     *                          chương bị cắt thành nhiều lần gọi nhà cung cấp
     *                          (4500 ký tự mỗi lần) nên đây mới là cần chi phí thật
     */
    public record Reader(
            boolean enabled,
            int dailyQuota,
            int dailyQuotaVip,
            int dailyQuotaGlobal,
            int maxChars
    ) {
        public static Reader defaults() {
            return new Reader(true, 3, 10, 100, 20_000);
        }
    }
}
