package com.storytts.backend.service;

import com.storytts.backend.config.StorageProperties;
import com.storytts.backend.exception.BadRequestException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Stores generated and uploaded media on the local filesystem.
 *
 * Only the file name is persisted in the database; resolving it back to an
 * absolute path always goes through {@link #resolveAudio(String)}, which
 * rejects anything that escapes the configured directory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService {

    private final StorageProperties properties;

    private Path audioRoot;
    private Path bgmRoot;
    private Path coverRoot;

    @PostConstruct
    void init() {
        audioRoot = createDirectory(properties.audioDir());
        bgmRoot = createDirectory(properties.bgmDir());
        coverRoot = createDirectory(properties.coverDir());
        log.info("Audio storage directory: {}", audioRoot);
    }

    private Path createDirectory(String configured) {
        // A missing property used to surface as a NullPointerException from
        // Paths.get, three frames deep in bean creation, naming nothing.
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException(
                    "Thiếu cấu hình thư mục lưu trữ (app.storage.*). Xem application.properties.");
        }
        try {
            Path path = Paths.get(configured).toAbsolutePath().normalize();
            Files.createDirectories(path);
            return path;
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage directory: " + configured, ex);
        }
    }

    /** Writes bytes under a generated name and returns that name. */
    public String storeAudio(byte[] content, String extension) {
        String fileName = UUID.randomUUID() + normaliseExtension(extension);
        Path target = audioRoot.resolve(fileName);
        try {
            Files.write(target, content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write audio file " + fileName, ex);
        }
        return fileName;
    }

    /** Copies an uploaded stream under a generated name and returns that name. */
    public String storeAudio(InputStream input, String extension) {
        return store(audioRoot, input, extension);
    }

    /**
     * Same, into the background-music directory.
     *
     * <p>A directory of its own rather than a prefix in the audio one: chapter
     * narration is derived data that gets rebuilt, while these are files an admin
     * uploaded once. Keeping them apart means a cleanup that empties the audio
     * directory cannot take the music library with it.
     */
    public String storeBgm(InputStream input, String extension) {
        return store(bgmRoot, input, extension);
    }

    private String store(Path root, InputStream input, String extension) {
        String fileName = UUID.randomUUID() + normaliseExtension(extension);
        Path target = root.resolve(fileName);
        try {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write file " + fileName, ex);
        }
        return fileName;
    }

    /**
     * Resolves a stored file name to a readable resource.
     *
     * @throws BadRequestException if the name would escape the audio directory
     */
    public Resource resolveAudio(String fileName) {
        return resolve(audioRoot, fileName);
    }

    public Resource resolveBgm(String fileName) {
        return resolve(bgmRoot, fileName);
    }

    private Resource resolve(Path root, String fileName) {
        Path path = root.resolve(fileName).normalize();
        if (!path.startsWith(root)) {
            throw new BadRequestException("Đường dẫn file không hợp lệ.");
        }
        return new FileSystemResource(path);
    }

    /** Best-effort delete; a missing file is not an error. */
    public void deleteAudio(String fileName) {
        delete(audioRoot, fileName);
    }

    public void deleteBgm(String fileName) {
        delete(bgmRoot, fileName);
    }

    private void delete(Path root, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Path path = root.resolve(fileName).normalize();
            if (path.startsWith(root)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            log.warn("Could not delete file {}: {}", fileName, ex.getMessage());
        }
    }

    public Path getCoverRoot() {
        return coverRoot;
    }

    private String normaliseExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            return ".mp3";
        }
        return extension.startsWith(".") ? extension : "." + extension;
    }
}
