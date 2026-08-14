package com.storytts.backend.service.tts;

/**
 * Khi nào một chữ được đọc lên, và chữ ấy nằm ở đâu trong nội dung chương.
 *
 * <p>Hai cặp số chứ không phải một. {@code start}/{@code end} là thời gian trong
 * file audio — thứ trình phát so với đồng hồ của nó. {@code charStart}/
 * {@code charEnd} là vị trí trong chuỗi văn bản của chương — thứ trang đọc dùng
 * để biết phải tô sáng đoạn nào. Thiếu cặp sau thì phía trình duyệt phải tự đoán
 * lại chữ nào ứng với chữ nào, và một chương có nhiều chữ lặp lại (tên riêng,
 * hư từ) là chỗ việc đoán ấy sai.
 *
 * @param word      chính chữ ấy, giữ nguyên dấu câu dính kèm
 * @param start     giây, tính từ đầu file audio
 * @param end       giây; luôn {@code >= start}
 * @param charStart chỉ số ký tự đầu tiên của chữ trong nội dung chương
 * @param charEnd   chỉ số ngay sau ký tự cuối cùng, theo lối nửa mở như
 *                  {@link String#substring(int, int)}
 */
public record WordTimestamp(String word, double start, double end, int charStart, int charEnd) {
}
