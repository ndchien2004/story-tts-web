package com.storytts.backend.service;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Serves and manages chapter audio.
 *
 * Every read path calls {@link AccessControlService#requireAccess(Chapter)}
 * first, so a locked chapter cannot be listened to even if the caller knows the
 * audio id.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AudioService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav",
            "audio/ogg", "audio/mp4", "audio/aac");

    private static final long MAX_UPLOAD_BYTES = 64L * 1024 * 1024;

    private final AudioFileRepository audioFileRepository;
    private final ChapterService chapterService;
    private final AccessControlService accessControlService;
    private final StorageService storageService;

    /** All usable tracks for a chapter, uploaded and generated alike. */
    @Transactional(readOnly = true)
    public List<AudioInfoDto> listForChapter(Long chapterId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        return audioFileRepository.findByChapterId(chapterId).stream()
                .filter(audio -> audio.getStatus() != AudioStatus.FAILED)
                .map(audio -> AudioInfoDto.from(audio, chapterId))
                .toList();
    }

    /**
     * Resolves an audio file for streaming.
     *
     * @throws com.storytts.backend.exception.ChapterLockedException if the caller
     *         may not access the parent chapter
     */
    @Transactional(readOnly = true)
    public StreamHandle openForStreaming(Long chapterId, Long audioId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        AudioFile audio = audioFileRepository.findById(audioId)
                .orElseThrow(() -> ResourceNotFoundException.of("file audio", audioId));

        if (!audio.getChapter().getId().equals(chapterId)) {
            throw new BadRequestException("File audio không thuộc chương này.");
        }
        if (audio.getStatus() != AudioStatus.READY || audio.getFilePath() == null) {
            throw new BadRequestException("File audio chưa sẵn sàng để phát.");
        }

        Resource resource = storageService.resolveAudio(audio.getFilePath());
        if (!resource.exists() || !resource.isReadable()) {
            throw new ResourceNotFoundException("File audio không còn tồn tại trên máy chủ.");
        }
        return new StreamHandle(resource, audio.getContentType());
    }

    /** Stores an admin-supplied recording, replacing any previous upload. */
    @Transactional
    public AudioInfoDto uploadForChapter(Long chapterId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn file audio để tải lên.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BadRequestException("File audio vượt quá 64 MB.");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(
                    "Định dạng không được hỗ trợ. Vui lòng dùng file MP3, WAV, OGG hoặc M4A.");
        }

        Chapter chapter = chapterService.findDetailEntity(chapterId);

        // One recording per chapter: drop the old one so storage does not grow
        // every time an admin re-uploads.
        audioFileRepository
                .findFirstByChapterIdAndSourceAndStatus(chapterId, AudioSource.UPLOAD, AudioStatus.READY)
                .ifPresent(existing -> {
                    storageService.deleteAudio(existing.getFilePath());
                    audioFileRepository.delete(existing);
                });

        String fileName;
        try {
            fileName = storageService.storeAudio(
                    file.getInputStream(), extensionOf(file.getOriginalFilename()));
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được file tải lên.");
        }

        AudioFile audio = AudioFile.builder()
                .chapter(chapter)
                .filePath(fileName)
                .source(AudioSource.UPLOAD)
                .status(AudioStatus.READY)
                .contentType(contentType)
                .fileSize(file.getSize())
                .build();

        AudioFile saved = audioFileRepository.save(audio);
        log.info("Uploaded audio for chapter {} ({} bytes)", chapterId, file.getSize());
        return AudioInfoDto.from(saved, chapterId);
    }

    @Transactional
    public void delete(Long audioId) {
        AudioFile audio = audioFileRepository.findById(audioId)
                .orElseThrow(() -> ResourceNotFoundException.of("file audio", audioId));
        storageService.deleteAudio(audio.getFilePath());
        audioFileRepository.delete(audio);
    }

    private String extensionOf(String originalName) {
        if (originalName == null) {
            return ".mp3";
        }
        int dot = originalName.lastIndexOf('.');
        return dot >= 0 ? originalName.substring(dot) : ".mp3";
    }

    /** Resource plus its media type, ready for a range-aware controller. */
    public record StreamHandle(Resource resource, String contentType) {
    }
}
