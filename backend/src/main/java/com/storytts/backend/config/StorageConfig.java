package com.storytts.backend.config;

import com.storytts.backend.service.CloudinaryService;
import com.storytts.backend.service.storage.CloudinaryMediaStorage;
import com.storytts.backend.service.storage.LocalMediaStorage;
import com.storytts.backend.service.storage.MediaStorage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chọn nơi lưu media theo cấu hình, một lần lúc khởi động.
 *
 * <p>Đây là chỗ duy nhất trong ứng dụng biết có bao nhiêu kiểu lưu trữ tồn tại.
 * Mọi nơi khác chỉ thấy {@link MediaStorage}.
 */
@Configuration
@Slf4j
public class StorageConfig {

    @Bean
    public MediaStorage mediaStorage(StorageProperties properties, CloudinaryService cloudinaryService) {
        MediaStorage storage = create(properties, cloudinaryService);
        log.info("Nơi lưu media: {}", storage.describe());
        return storage;
    }

    private MediaStorage create(StorageProperties properties, CloudinaryService cloudinaryService) {
        if (properties.driver() != StorageProperties.StorageDriver.CLOUDINARY) {
            return new LocalMediaStorage(properties);
        }

        // Dừng ngay lúc khởi động thay vì lúc có người bấm nghe. Chọn Cloudinary
        // mà thiếu khóa thì mọi lượt dựng audio sau đó đều hỏng, và hỏng ở một
        // chỗ chẳng nhắc gì tới cấu hình — còn tệ hơn là không khởi động được.
        if (!cloudinaryService.isConfigured()) {
            throw new IllegalStateException("""
                    app.storage.driver=cloudinary nhưng thiếu khóa Cloudinary.
                    Cần CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY và CLOUDINARY_API_SECRET.""");
        }
        return new CloudinaryMediaStorage(cloudinaryService);
    }
}
