package com.storytts.backend.service.tts;

import java.util.ArrayList;
import java.util.List;

/**
 * Gộp mốc thời gian theo từng ký tự của nhà cung cấp thành mốc theo từng chữ.
 *
 * <h3>Vì sao phải gộp</h3>
 * ElevenLabs trả về một mảng ký tự kèm hai mảng thời gian song song — mỗi ký tự
 * một dòng, kể cả dấu cách. Trang đọc không tô sáng theo ký tự: mắt người đọc
 * theo chữ, và năm nghìn ký tự nhấp nháy riêng lẻ vừa vô nghĩa vừa nặng. Nên
 * ranh giới ở đây là khoảng trắng: một chữ là một chuỗi ký tự liền nhau không có
 * khoảng trắng, dấu câu dính kèm thì đi theo chữ ấy.
 *
 * <p>Đó cũng chính là cách phía trình duyệt cắt chữ từ nội dung chương. Hai bên
 * cắt giống nhau thì hai bên khớp nhau, và mốc thời gian rơi đúng vào ô chữ đang
 * hiển thị chứ không lệch đi một nhịp.
 *
 * <h3>Vì sao có {@code sourceOffset}</h3>
 * Một chương dài bị {@link TextChunker} cắt thành nhiều lần gọi API, mỗi lần
 * được cắt gọn hai đầu. Nhà cung cấp chỉ biết đoạn nó nhận được, nên thời gian
 * nó trả về luôn bắt đầu từ 0 và vị trí ký tự luôn tính từ đầu đoạn. Lớp này
 * cộng lại: thời gian cộng dồn theo độ dài các đoạn trước, vị trí ký tự cộng
 * theo chỗ đoạn ấy nằm trong chương.
 */
final class WordAligner {

    /**
     * Tìm ký tự của nhà cung cấp trong văn bản gốc thì chỉ dò tối đa ngần này ký
     * tự về phía trước.
     *
     * <p>Bình thường hai bên trùng khít nhau và phép dò dừng ngay ở ký tự đầu.
     * Cửa sổ này là để phòng trường hợp nhà cung cấp chuẩn hóa nhẹ đầu vào (bỏ
     * một ký tự điều khiển, đổi một dấu nháy) — nhảy vài ký tự thì vẫn bắt lại
     * được, còn nhảy xa hơn thì gần như chắc chắn là bắt nhầm vào một chữ giống
     * hệt ở phía sau, nên thà đứng yên còn hơn.
     */
    private static final int RESYNC_WINDOW = 16;

    private WordAligner() {
    }

    /**
     * Một lần gọi nhà cung cấp: chữ đã gửi đi, chỗ nó nằm trong chương, và những
     * gì nhận về.
     *
     * @param text         đúng đoạn văn bản đã gửi cho nhà cung cấp
     * @param sourceOffset chỉ số của ký tự đầu đoạn ấy trong nội dung chương
     * @param characters   mảng ký tự nhà cung cấp trả về, theo đúng thứ tự đọc
     * @param startTimes   thời điểm bắt đầu của từng ký tự, giây, tính từ đầu đoạn
     * @param endTimes     thời điểm kết thúc tương ứng
     */
    record Segment(
            String text,
            int sourceOffset,
            List<String> characters,
            List<Double> startTimes,
            List<Double> endTimes
    ) {

        /**
         * Số dòng thực sự dùng được.
         *
         * <p>Ba mảng lẽ ra dài bằng nhau. Lấy phần chung thay vì tin vào điều đó,
         * vì một câu trả lời méo chỉ nên làm mất mấy chữ cuối chứ không nên ném
         * ngoại lệ giữa lúc đã gọi API xong và đã trả tiền rồi.
         */
        int usableLength() {
            if (characters == null || startTimes == null || endTimes == null) {
                return 0;
            }
            return Math.min(characters.size(), Math.min(startTimes.size(), endTimes.size()));
        }

        /**
         * Độ dài đoạn audio này, để đoạn sau bắt đầu từ đâu.
         *
         * <p>Lấy mốc kết thúc lớn nhất chứ không lấy phần tử cuối: mảng lẽ ra đã
         * sắp xếp, nhưng đây là chỗ mà một dòng lạc sẽ kéo lệch toàn bộ những
         * đoạn sau nó, nên không đáng để tin.
         */
        double duration() {
            double longest = 0;
            for (int i = 0; i < usableLength(); i++) {
                Double end = endTimes.get(i);
                if (end != null && end > longest) {
                    longest = end;
                }
            }
            return longest;
        }
    }

    /**
     * Nối các đoạn lại thành một mốc thời gian duy nhất cho cả chương.
     *
     * @return danh sách theo thứ tự đọc; rỗng khi không đoạn nào nói được gì
     */
    static List<WordTimestamp> align(List<Segment> segments) {
        List<WordTimestamp> words = new ArrayList<>();
        if (segments == null || segments.isEmpty()) {
            return words;
        }

        double timeOffset = 0;
        for (Segment segment : segments) {
            collectWords(segment, timeOffset, words);
            timeOffset += segment.duration();
        }
        return List.copyOf(words);
    }

    /** Cắt một đoạn thành chữ và đẩy vào {@code target}. */
    private static void collectWords(Segment segment, double timeOffset, List<WordTimestamp> target) {
        int length = segment.usableLength();
        if (length == 0) {
            return;
        }

        int[] positions = mapToSource(segment, length);
        int index = 0;

        while (index < length) {
            if (isBlank(segment.characters().get(index))) {
                index++;
                continue;
            }

            int wordStart = index;
            StringBuilder word = new StringBuilder();
            while (index < length && !isBlank(segment.characters().get(index))) {
                word.append(segment.characters().get(index));
                index++;
            }
            int wordEnd = index - 1;

            String text = word.toString();
            if (text.isBlank()) {
                continue;
            }

            double start = timeOffset + valueAt(segment.startTimes(), wordStart);
            double end = timeOffset + valueAt(segment.endTimes(), wordEnd);

            // Một chữ kết thúc trước khi nó bắt đầu là vô nghĩa với vòng lặp tô
            // sáng ở trình duyệt — nó sẽ không bao giờ khớp và chữ ấy bị bỏ qua.
            if (end < start) {
                end = start;
            }

            int charStart = segment.sourceOffset() + positions[wordStart];
            int charEnd = segment.sourceOffset() + positions[wordEnd]
                    + segment.characters().get(wordEnd).length();

            target.add(new WordTimestamp(text, round(start), round(end), charStart, charEnd));
        }
    }

    /**
     * Với mỗi ký tự nhà cung cấp trả về, chỉ ra nó nằm ở đâu trong đoạn văn bản
     * đã gửi đi.
     *
     * <p>Gần như luôn là ánh xạ đồng nhất — mảng ký tự chính là đoạn văn bản ấy
     * tách ra. Vòng lặp này tồn tại cho phần "gần như": con trỏ chỉ tiến, và khi
     * không tìm thấy ký tự trong cửa sổ cho phép thì nó đứng yên thay vì nhảy
     * bừa, nên một sai lệch nhỏ chỉ làm hỏng vị trí của đúng chữ ấy chứ không
     * đẩy lệch cả phần còn lại của chương.
     */
    private static int[] mapToSource(Segment segment, int length) {
        String text = segment.text() == null ? "" : segment.text();
        int[] positions = new int[length];
        int cursor = 0;

        for (int i = 0; i < length; i++) {
            String character = segment.characters().get(i);
            int found = character.isEmpty() ? cursor : text.indexOf(character, cursor);

            if (found >= 0 && found - cursor <= RESYNC_WINDOW) {
                positions[i] = found;
                cursor = found + character.length();
            } else {
                positions[i] = Math.min(cursor, Math.max(text.length() - 1, 0));
            }
        }

        return positions;
    }

    private static double valueAt(List<Double> times, int index) {
        Double value = times.get(index);
        return value == null || value.isNaN() || value.isInfinite() ? 0 : value;
    }

    /** Ba chữ số thập phân là một phần nghìn giây — mịn hơn thế thì chỉ tốn byte. */
    private static double round(double seconds) {
        return Math.round(seconds * 1000d) / 1000d;
    }

    private static boolean isBlank(String character) {
        return character == null || character.isBlank();
    }
}
