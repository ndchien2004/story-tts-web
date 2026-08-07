package com.storytts.backend.service.tts;

/**
 * Finished audio plus a record of what produced it.
 *
 * The provider is reported back because a fallback means the audio may not come
 * from the one that was asked for, and both the stored row and the reader
 * should reflect that.
 */
public record SynthesisResult(byte[] audio, String providerId, String providerName) {
}
