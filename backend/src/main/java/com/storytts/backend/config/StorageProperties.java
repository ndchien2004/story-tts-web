package com.storytts.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thư mục lưu file audio và ảnh bìa trên server (mục 6 đề bài). */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String audioDir,
        String coverDir
) {
}
