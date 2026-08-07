package com.storytts.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Backend REST API cho website đọc &amp; nghe truyện có chức năng Text-to-Speech.
 *
 * <p>Phân lớp: Controller → Service → Repository → Entity (yêu cầu mục 10 đề bài).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
