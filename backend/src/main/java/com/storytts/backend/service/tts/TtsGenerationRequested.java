package com.storytts.backend.service.tts;

/**
 * Raised once a PROCESSING audio row has been persisted.
 *
 * Synthesis is triggered by this event rather than called directly, so the work
 * only starts after the surrounding transaction commits and the row is visible
 * to the worker's own transaction.
 */
public record TtsGenerationRequested(Long audioId, String text, String voice, int speed) {
}
