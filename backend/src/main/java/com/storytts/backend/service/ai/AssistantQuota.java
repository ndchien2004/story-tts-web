package com.storytts.backend.service.ai;

import com.storytts.backend.config.AiAssistantProperties;
import com.storytts.backend.domain.AiUsageKind;
import com.storytts.backend.exception.AiQuotaExceededException;
import com.storytts.backend.service.AiUsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Đếm lượt hỏi trong ngày — hàng rào chi phí của trợ lý AI.
 *
 * <h3>Vì sao con số này từng sai, và sai theo hướng đắt tiền</h3>
 * Bộ đếm cũ là một bảng băm trong bộ nhớ của tiến trình. Ghi chú lúc ấy có nêu
 * một rủi ro — chạy nhiều bản ứng dụng song song thì mỗi bản đếm một kiểu —
 * nhưng bỏ sót rủi ro thật sự đang xảy ra: <b>khởi động lại</b>. Gói miễn phí
 * của Render ngủ sau 15 phút vắng khách, và tỉnh dậy là một tiến trình mới với
 * bộ đếm về 0. Trần 500 lượt một ngày chỉ có hiệu lực trong quãng giữa hai lần
 * ngủ, nên trang càng ít khách thì hàng rào càng lỏng — đúng chiều ngược với
 * điều người ta muốn.
 *
 * <p>Giờ phép đếm nằm ở {@link AiUsageService}, trên một bảng trong cơ sở dữ
 * liệu. Lập luận cũ ("dựng một bảng chỉ để đếm là quá tay") không còn đúng nữa,
 * vì đường tạo audio cũng cần đúng cái bảng ấy vì đúng một lý do như vậy — hai
 * hàng rào chi phí, một nguồn sự thật.
 *
 * <p>Lớp này ở lại làm phần <i>chính sách</i>: nó biết hạn mức của một Thành
 * viên khác hạn mức của VIP, và biết nói câu từ chối bằng lời của trợ lý.
 * {@link AiUsageService} không biết hai điều đó và không cần biết.
 */
@Component
@RequiredArgsConstructor
public class AssistantQuota {

    private final AiAssistantProperties properties;
    private final AiUsageService usage;

    /**
     * Còn bao nhiêu lượt hôm nay, không tiêu lượt nào.
     *
     * @return {@code null} khi hạn mức là không giới hạn
     */
    public Integer remainingFor(long userId, boolean vip) {
        return usage.remaining(userId, AiUsageKind.ASSISTANT,
                limitFor(vip), properties.dailyQuotaGlobal());
    }

    /**
     * Xin một lượt, và tiêu nó nếu còn.
     *
     * <p>Gọi <i>trước</i> khi gọi Gemini. Một lượt bị tính cho một lời gọi hỏng
     * là chuyện chấp nhận được; một lời gọi tính tiền mà không ai đếm thì không.
     *
     * @throws AiQuotaExceededException hết phần của người này, hoặc của cả ngày
     */
    public void consume(long userId, boolean vip) {
        usage.reserve(userId, AiUsageKind.ASSISTANT, null,
                limitFor(vip), properties.dailyQuotaGlobal(),
                (scope, limit) -> new AiQuotaExceededException(
                        scope == AiUsageService.Scope.GLOBAL
                                ? AiQuotaExceededException.Scope.GLOBAL
                                : AiQuotaExceededException.Scope.USER,
                        limit));
    }

    /** Hạn mức của người này; null nghĩa là không giới hạn. */
    private Integer limitFor(boolean vip) {
        int limit = properties.dailyQuotaFor(vip);
        return limit == AiAssistantProperties.UNLIMITED ? null : limit;
    }
}
