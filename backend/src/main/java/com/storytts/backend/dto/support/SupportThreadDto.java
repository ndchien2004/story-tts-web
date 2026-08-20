package com.storytts.backend.dto.support;

import java.util.List;

/**
 * Một lát cắt của luồng hội thoại: trạng thái, một trang tin nhắn, và có còn nữa không.
 *
 * <h3>Một hình dạng cho cả ba đường đọc</h3>
 * Mở lần đầu, cuộn lên xem tin cũ, và lấy phần bỏ lỡ sau khi nối lại — ba việc
 * khác nhau, nhưng chúng trả lời cùng một câu và vì thế dùng chung một hình
 * dạng. Ba hình dạng khác nhau sẽ nghĩa là ba nhánh dựng giao diện, và cái nhánh
 * ít chạy nhất — đường phục hồi sau mất kết nối — sẽ là cái sai.
 *
 * <h3>{@code conversation} luôn đi kèm, kể cả khi trình duyệt đã có nó</h3>
 * Và đó là chủ ý. Trạng thái luồng đổi được trong lúc trình duyệt đang ngoại
 * tuyến — quản trị viên đóng, hoặc chặn — nên mỗi lượt đồng bộ phải mang theo
 * câu trả lời mới nhất, không chỉ mang tin nhắn. Không có nó thì một trình duyệt
 * nối lại sau nửa tiếng vẫn vẽ ô soạn tin cho một luồng đã bị chặn, và người
 * dùng chỉ biết khi bấm gửi.
 *
 * <h3>{@code messages} luôn tăng dần theo {@code id}</h3>
 * Kể cả khi câu truy vấn bên dưới chạy ngược (cuộn lên thì phải lấy từ mới về
 * cũ). Việc đảo lại xảy ra đúng một lần, ở tầng service, thay vì để mỗi màn hình
 * tự nhớ chiều nào đi với đường nào.
 *
 * @param hasMore lát cắt này bị cắt vì chạm trần — còn nữa ở <i>cùng chiều</i>
 *                mà nó vừa đi. Một nghĩa cho cả hai chiều, cố ý: cuộn lên thì nó
 *                là "còn tin cũ hơn, vẽ nút xem thêm"; bắt kịp sau khi mất kết
 *                nối thì nó là "chưa lấy hết, xin tiếp một lượt nữa". Hai nghĩa
 *                khác nhau trên một trường sẽ là chỗ mà đường ít chạy hơn —
 *                đường phục hồi — đọc sai.
 *                <p>Suy từ việc trang đã đầy hay chưa, không từ một phép đếm
 *                tổng số tin mà không màn hình nào cần và mọi luồng dài đều
 *                phải trả giá cho nó.
 */
public record SupportThreadDto(
        SupportConversationDto conversation,
        List<SupportMessageDto> messages,
        boolean hasMore
) {
}
