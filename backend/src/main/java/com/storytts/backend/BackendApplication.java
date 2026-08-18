package com.storytts.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Backend REST API cho website đọc &amp; nghe truyện có chức năng Text-to-Speech.
 *
 * <p>Phân lớp: Controller → Service → Repository → Entity.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
// Hai việc chạy theo nhịp, cả hai đều thuộc vòng đời của bản audio: giữ nhịp cho
// những kết nối SSE đang mở, và dọn bản audio đã lỗi thời sau hạn lưu giữ.
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
