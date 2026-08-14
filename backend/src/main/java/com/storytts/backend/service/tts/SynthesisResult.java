package com.storytts.backend.service.tts;

import java.util.List;

/**
 * Finished audio plus a record of what produced it.
 *
 * The provider is reported back because a fallback means the audio may not come
 * from the one that was asked for, and both the stored row and the reader
 * should reflect that.
 *
 * @param words mốc thời gian từng chữ, hoặc rỗng khi nhà cung cấp không kèm theo
 */
public record SynthesisResult(
        byte[] audio,
        List<WordTimestamp> words,
        String providerId,
        String providerName
) {

    public SynthesisResult {
        words = words == null ? List.of() : List.copyOf(words);
    }
}
