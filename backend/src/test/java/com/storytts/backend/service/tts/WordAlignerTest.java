package com.storytts.backend.service.tts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.assertj.core.data.Offset;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Kiểm thử phần gộp ký tự thành chữ.
 *
 * <p>Đây là chỗ quyết định phần tô sáng ở trang đọc rơi vào đâu, và cũng là chỗ
 * duy nhất trong luồng tạo audio có thể kiểm được mà không cần mạng: nó thuần
 * túy là số vào, số ra. Hai điều được ghim ở đây — ranh giới chữ trùng với cách
 * trình duyệt cắt chữ, và nhiều đoạn ghép lại thì thời gian phải cộng dồn chứ
 * không đoạn nào cũng bắt đầu từ 0.
 */
class WordAlignerTest {

    /** Mốc thời gian được làm tròn tới phần nghìn giây, nên so sánh cũng chỉ tới đó. */
    private static final Offset<Double> TOLERANCE = within(0.002);

    /** Dựng một đoạn mà mảng ký tự chính là văn bản tách ra, mỗi ký tự một nhịp. */
    private static WordAligner.Segment evenlyTimed(String text, int sourceOffset,
                                                   double startAt, double perCharacter) {
        List<String> characters = text.chars()
                .mapToObj(codePoint -> String.valueOf((char) codePoint))
                .toList();

        List<Double> starts = new java.util.ArrayList<>(characters.size());
        List<Double> ends = new java.util.ArrayList<>(characters.size());
        for (int i = 0; i < characters.size(); i++) {
            starts.add(startAt + i * perCharacter);
            ends.add(startAt + (i + 1) * perCharacter);
        }

        return new WordAligner.Segment(text, sourceOffset, characters, starts, ends);
    }

    @Test
    @DisplayName("Gộp ký tự thành chữ, lấy khoảng trắng làm ranh giới")
    void groupsCharactersIntoWords() {
        String text = "Ngày xưa";
        List<WordTimestamp> words = WordAligner.align(List.of(evenlyTimed(text, 0, 0, 0.1)));

        assertThat(words).extracting(WordTimestamp::word).containsExactly("Ngày", "xưa");

        int firstLength = "Ngày".length();
        WordTimestamp first = words.getFirst();
        assertThat(first.start()).isEqualTo(0);
        assertThat(first.end()).isCloseTo(firstLength * 0.1, TOLERANCE);
        assertThat(first.charStart()).isZero();
        assertThat(first.charEnd()).isEqualTo(firstLength);

        // Chữ thứ hai bắt đầu sau dấu cách, không phải ngay sau chữ trước.
        WordTimestamp second = words.get(1);
        assertThat(second.start()).isCloseTo((firstLength + 1) * 0.1, TOLERANCE);
        assertThat(second.charStart()).isEqualTo(text.indexOf("xưa"));
        assertThat(second.charEnd()).isEqualTo(text.length());
    }

    @Test
    @DisplayName("Dấu câu đi theo chữ nó dính vào, không thành một chữ riêng")
    void keepsPunctuationAttached() {
        List<WordTimestamp> words = WordAligner.align(
                List.of(evenlyTimed("Ngày xưa, có một", 0, 0, 0.1)));

        assertThat(words).extracting(WordTimestamp::word)
                .containsExactly("Ngày", "xưa,", "có", "một");
    }

    @Test
    @DisplayName("Xuống dòng cũng là ranh giới chữ, và vị trí ký tự vẫn khớp văn bản gốc")
    void treatsNewlinesAsBoundaries() {
        String text = "Một hai\nBa";
        List<WordTimestamp> words = WordAligner.align(List.of(evenlyTimed(text, 0, 0, 0.1)));

        assertThat(words).extracting(WordTimestamp::word).containsExactly("Một", "hai", "Ba");
        for (WordTimestamp word : words) {
            assertThat(text.substring(word.charStart(), word.charEnd())).isEqualTo(word.word());
        }
    }

    @Test
    @DisplayName("Nhiều đoạn: thời gian cộng dồn, vị trí ký tự tính theo chỗ đoạn nằm trong chương")
    void offsetsFollowingSegments() {
        // Chương thật: hai đoạn liền nhau, cách nhau một dấu cách mà TextChunker
        // đã cắt bỏ khỏi cả hai đầu.
        String chapter = "Một hai ba bốn";
        String head = "Một hai";
        String tail = "ba bốn";

        List<WordTimestamp> words = WordAligner.align(List.of(
                evenlyTimed(head, 0, 0, 0.1),
                evenlyTimed(tail, chapter.indexOf(tail), 0, 0.1)));

        assertThat(words).extracting(WordTimestamp::word)
                .containsExactly("Một", "hai", "ba", "bốn");

        // Đoạn sau bắt đầu ở đúng chỗ đoạn trước kết thúc, chứ không lại từ 0.
        double headDuration = head.length() * 0.1;
        assertThat(words.get(2).start()).isCloseTo(headDuration, TOLERANCE);
        assertThat(words.get(3).start()).isCloseTo(headDuration + 3 * 0.1, TOLERANCE);

        // Và mọi chữ vẫn chỉ đúng vào chỗ của nó trong nội dung chương.
        for (WordTimestamp word : words) {
            assertThat(chapter.substring(word.charStart(), word.charEnd())).isEqualTo(word.word());
        }
    }

    @Test
    @DisplayName("Mốc thời gian luôn không giảm và không có chữ nào kết thúc trước khi bắt đầu")
    void producesMonotonicTimeline() {
        List<WordTimestamp> words = WordAligner.align(List.of(
                evenlyTimed("Một hai ba", 0, 0, 0.12),
                evenlyTimed("bốn năm", 11, 0, 0.12)));

        double previousStart = -1;
        for (WordTimestamp word : words) {
            assertThat(word.end()).isGreaterThanOrEqualTo(word.start());
            assertThat(word.start()).isGreaterThanOrEqualTo(previousStart);
            previousStart = word.start();
        }
    }

    @Test
    @DisplayName("Ba mảng lệch độ dài thì lấy phần chung, không ném lỗi")
    void toleratesRaggedInput() {
        WordAligner.Segment ragged = new WordAligner.Segment(
                "Một hai",
                0,
                List.of("M", "ộ", "t", " ", "h", "a", "i"),
                List.of(0.0, 0.1, 0.2, 0.3, 0.4),
                List.of(0.1, 0.2, 0.3, 0.4, 0.5));

        List<WordTimestamp> words = WordAligner.align(List.of(ragged));

        // Năm ký tự dùng được: "Một" trọn vẹn, rồi "h" của chữ sau.
        assertThat(words).extracting(WordTimestamp::word).containsExactly("Một", "h");
    }

    @Test
    @DisplayName("Không có đoạn nào thì không có chữ nào — và không có ngoại lệ nào")
    void handlesEmptyInput() {
        assertThat(WordAligner.align(List.of())).isEmpty();
        assertThat(WordAligner.align(null)).isEmpty();
    }

    @Test
    @DisplayName("Nhà cung cấp bỏ bớt một ký tự thì chỉ chữ ấy lệch, phần sau vẫn khớp")
    void resyncsAfterAMissingCharacter() {
        // Văn bản có một ký tự vô hình (dấu nối mềm) mà nhà cung cấp không đọc và
        // cũng không kể vào mảng ký tự trả về.
        String text = "Một­hai ba";
        List<String> characters = List.of("M", "ộ", "t", "h", "a", "i", " ", "b", "a");

        List<Double> starts = new java.util.ArrayList<>();
        List<Double> ends = new java.util.ArrayList<>();
        for (int i = 0; i < characters.size(); i++) {
            starts.add(i * 0.1);
            ends.add((i + 1) * 0.1);
        }

        List<WordTimestamp> words = WordAligner.align(
                List.of(new WordAligner.Segment(text, 0, characters, starts, ends)));

        // Chữ cuối là chỗ quan trọng: nó phải chỉ đúng vào "ba" trong văn bản gốc
        // chứ không lệch đi một ký tự vì cái ký tự bị bỏ ở trên.
        WordTimestamp last = words.getLast();
        assertThat(last.word()).isEqualTo("ba");
        assertThat(text.substring(last.charStart(), last.charEnd())).isEqualTo("ba");
    }
}
