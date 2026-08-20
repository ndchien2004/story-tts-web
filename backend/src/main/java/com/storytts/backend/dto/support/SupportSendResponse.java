package com.storytts.backend.dto.support;

/**
 * Câu trả lời cho một lượt gửi tin qua REST.
 *
 * <h3>Vì sao đường REST tồn tại bên cạnh WebSocket</h3>
 * Không phải để dùng song song, mà để có một đường lui. {@code WebSocket} vắng
 * mặt hoặc bị chặn ở vài webview nhúng và vài mạng doanh nghiệp; ở đó hộp thư
 * hỗ trợ vẫn phải gửi được, chỉ là không thấy tin của bên kia ngay lập tức.
 * Cùng cách chia mà cái chuông thông báo đã dùng với SSE.
 *
 * <p>Hệ quả quan trọng: hai đường phải cho ra <b>cùng một</b> kết quả, kể cả
 * khi cùng một {@code clientMessageId} đi lên bằng đường này rồi lại bằng đường
 * kia. Chúng cho ra cùng kết quả vì chúng gọi vào cùng một chỗ, và vì việc chống
 * trùng nằm ở ràng buộc của cơ sở dữ liệu chứ không ở tầng vận chuyển.
 *
 * @param duplicate lần bấm gửi này đã được ghi từ trước. Vẫn là <b>thành
 *                  công</b>: {@code message} là tin đã có, không phải một tin
 *                  thứ hai, và câu ấy nằm trong cơ sở dữ liệu đúng một lần.
 *                  Tương ứng với trạng thái {@code DUPLICATE} của khung tin báo
 *                  nhận trên WebSocket — cùng ngữ nghĩa, cùng cách xử lý ở
 *                  trình duyệt.
 */
public record SupportSendResponse(
        SupportMessageDto message,
        SupportConversationDto conversation,
        boolean duplicate
) {
}
