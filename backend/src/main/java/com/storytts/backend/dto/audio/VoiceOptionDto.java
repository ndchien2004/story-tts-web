package com.storytts.backend.dto.audio;

import com.storytts.backend.service.tts.TtsVoice;

/** A selectable narration voice. */
public record VoiceOptionDto(String code, String name, String gender, String region) {

    public static VoiceOptionDto from(TtsVoice voice) {
        return new VoiceOptionDto(
                voice.getCode(), voice.getDisplayName(), voice.getGender(), voice.getRegion());
    }
}
