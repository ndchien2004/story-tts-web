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
    private Path coverRoot;

    @PostConstruct
    void init() {
        audioRoot = createDirectory(properties.audioDir());
        coverRoot = createDirectory(properties.coverDir());
        log.info("Audio storage directory: {}", audioRoot);
    }

    private Path createDirectory(String configured) {
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
        String fileName = UUID.randomUUID() + normaliseExtension(extension);
        Path target = audioRoot.resolve(fileName);
        try {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot write audio file " + fileName, ex);
        }
        return fileName;
    }

    /**
     * Resolves a stored file name to a readable resource.
     *
     * @throws BadRequestException if the name would escape the audio directory
     */
    public Resource resolveAudio(String fileName) {
        Path path = audioRoot.resolve(fileName).normalize();
        if (!path.startsWith(audioRoot)) {
            throw new BadRequestException("Đường dẫn file không hợp lệ.");
        }
        return new FileSystemResource(path);
    }

    /** Best-effort delete; a missing file is not an error. */
    public void deleteAudio(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Path path = audioRoot.resolve(fileName).normalize();
            if (path.startsWith(audioRoot)) {
                Files.deleteIfExists(path);
            }
        } catch (IOException ex) {
            log.warn("Could not delete audio file {}: {}", fileName, ex.getMessage());
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
