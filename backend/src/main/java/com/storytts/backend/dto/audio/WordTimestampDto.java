package com.storytts.backend.dto.audio;

/**
 * Một chữ, và lúc giọng đọc đọc tới nó.
 *
 * <p>Đây là đơn vị nhỏ nhất của phần tô sáng theo giọng đọc ở trang đọc. Trình
 * duyệt nhận cả mảng một lần khi mở chương rồi tự dò trong lúc phát, nên định
 * dạng ở đây cố ý phẳng và không có gì phải giải mã thêm.
 *
 * @param word      chính chữ ấy, dấu câu dính kèm giữ nguyên
 * @param start     giây, tính từ đầu file audio
 * @param end       giây; luôn {@code >= start}
 * @param charStart chỉ số ký tự đầu tiên của chữ trong nội dung chương
 * @param charEnd   chỉ số ngay sau ký tự cuối cùng (nửa mở)
 */
public record WordTimestampDto(
        String word,
        double start,
        double end,
        int charStart,
        int charEnd
) {
}
