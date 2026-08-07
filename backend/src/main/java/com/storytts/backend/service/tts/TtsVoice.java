package com.storytts.backend.service.tts;

import java.util.Arrays;
import java.util.Optional;

/**
 * Vietnamese voices offered by the provider, exposed to the client so the
 * reader can pick one.
 */
public enum TtsVoice {

    BANMAI("banmai", "Ban Mai", "Nữ", "Miền Bắc"),
    LEMINH("leminh", "Lê Minh", "Nam", "Miền Bắc"),
    THUMINH("thuminh", "Thu Minh", "Nữ", "Miền Bắc"),
    GIAHUY("giahuy", "Gia Huy", "Nam", "Miền Trung"),
    MYAN("myan", "Mỹ An", "Nữ", "Miền Trung"),
    LANNHI("lannhi", "Lan Nhi", "Nữ", "Miền Nam"),
    LINHSAN("linhsan", "Linh San", "Nữ", "Miền Nam"),
    MINHQUANG("minhquang", "Minh Quang", "Nam", "Miền Nam");

    private final String code;
    private final String displayName;
    private final String gender;
    private final String region;

    TtsVoice(String code, String displayName, String gender, String region) {
        this.code = code;
        this.displayName = displayName;
        this.gender = gender;
        this.region = region;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGender() {
        return gender;
    }

    public String getRegion() {
        return region;
    }

    public static Optional<TtsVoice> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(voice -> voice.code.equalsIgnoreCase(code.trim()))
                .findFirst();
    }
}
