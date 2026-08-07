package com.storytts.backend.service.tts;

import com.storytts.backend.exception.TtsException;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits chapter text into pieces small enough for a provider to accept.
 *
 * Shared by every provider because they all cap the text length per request,
 * only at different limits.
 */
final class TextChunker {

    private TextChunker() {
    }

    /**
     * Splits on sentence, then word boundaries so a chunk never cuts mid-word
     * and the joined audio keeps its natural pauses.
     *
     * @param limit maximum characters per chunk
     */
    static List<String> split(String text, int limit) {
        String normalised = text == null ? "" : text.strip();
        if (normalised.isEmpty()) {
            throw new TtsException("Nội dung chương đang trống, không có gì để đọc.");
        }
        if (normalised.length() <= limit) {
            return List.of(normalised);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : normalised.split("(?<=[.!?…\\n])")) {
            if (sentence.length() > limit) {
                flush(chunks, current);
                chunks.addAll(splitOnWords(sentence, limit));
                continue;
            }
            if (current.length() + sentence.length() > limit) {
                flush(chunks, current);
            }
            current.append(sentence);
        }
        flush(chunks, current);

        return chunks;
    }

    /** Last-resort split for a single run of text with no sentence breaks. */
    private static List<String> splitOnWords(String text, int limit) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String word : text.split(" ")) {
            if (current.length() + word.length() + 1 > limit) {
                flush(parts, current);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        flush(parts, current);
        return parts;
    }

    private static void flush(List<String> target, StringBuilder buffer) {
        if (!buffer.isEmpty()) {
            String value = buffer.toString().strip();
            if (!value.isEmpty()) {
                target.add(value);
            }
            buffer.setLength(0);
        }
    }
}
