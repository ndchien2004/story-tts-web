package com.storytts.backend.domain;

/** Nguồn gốc file audio (bảng audio_files, mục 8 đề bài). */
public enum AudioSource {
    /** File thu âm sẵn do Admin upload. */
    UPLOAD,
    /** File do hệ thống sinh ra qua API Text-to-Speech. */
    TTS
}
