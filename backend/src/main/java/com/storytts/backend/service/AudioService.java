package com.storytts.backend.service;

import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.User;
import com.storytts.backend.domain.ViewType;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.ChapterTranscriptDto;
import com.storytts.backend.dto.audio.WordTimestampDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.repository.AudioTranscriptRepository;
import com.storytts.backend.service.tts.TranscriptCodec;
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
 *
 * <p>A second gate sits behind that one. Audio a reader made for themselves is
 * theirs alone, and knowing an id is not permission to hear it — see
 * {@link #requireOwnership(AudioFile)}, which every path resolving a single
 * track goes through.
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
    private final AudioTranscriptRepository audioTranscriptRepository;
    private final TranscriptCodec transcriptCodec;
    private final ChapterService chapterService;
    private final AccessControlService accessControlService;
    private final StorageService storageService;
    private final ViewEventService viewEventService;
    private final CurrentUserService currentUserService;

    /**
     * Bản do một người đọc tự dựng thì chỉ người ấy được chạm tới.
     *
     * <p>Bản của khu quản trị ({@code requestedBy} rỗng) thì ai cũng nghe được —
     * đó là phần được dựng sẵn cho mọi người.
     *
     * <p>Trả về "không tìm thấy" chứ không phải "không được phép": người lạ dò
     * số thứ tự thì không nên biết được số nào có thật, số nào không.
     */
    private void requireOwnership(AudioFile audio) {
        User owner = audio.getRequestedBy();
        if (owner == null) {
            return;
        }
        Long viewer = currentUserService.currentUserId().orElse(null);
        if (!owner.getId().equals(viewer)) {
            throw ResourceNotFoundException.of("file audio", audio.getId());
        }
    }

    /** All usable tracks for a chapter, uploaded and generated alike. */
    @Transactional
    public List<AudioInfoDto> listForChapter(Long chapterId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        // Một lượt nghe được tính ở đây chứ không ở endpoint stream: trình phát gọi
        // stream nhiều lần cho mỗi lần tua, còn danh sách track thì chỉ hỏi một lần
        // khi mở chương — đúng một lượt cho một phiên nghe.
        viewEventService.record(chapter.getStory().getId(), chapterId, ViewType.LISTEN);

        Long viewer = currentUserService.currentUserId().orElse(null);
        return audioFileRepository.findVisibleForChapter(chapterId, viewer).stream()
                .filter(audio -> audio.getStatus() != AudioStatus.FAILED)
                .map(audio -> AudioInfoDto.from(audio, chapterId))
                .toList();
    }

    /**
     * Trạng thái một bản audio, không tính thêm lượt nghe.
     *
     * <p>Trang đọc phải hỏi liên tục trong lúc chờ dựng xong, nên không dùng
     * {@link #listForChapter} cho việc đó được: đường ấy cố ý ghi một lượt nghe
     * mỗi lần gọi, và thăm dò vài giây một lần sẽ tự thổi phồng biểu đồ của trang
     * quản trị lên hàng chục lượt nghe không có thật.
     *
     * <p>Trả về cả bản FAILED, vì "dựng hỏng vì lý do này" cũng là một câu trả lời
     * mà người đang chờ cần biết.
     */
    @Transactional(readOnly = true)
    public AudioInfoDto trackStatus(Long chapterId, Long audioId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        AudioFile audio = audioFileRepository.findById(audioId)
                .orElseThrow(() -> ResourceNotFoundException.of("file audio", audioId));

        if (!audio.getChapter().getId().equals(chapterId)) {
            throw new BadRequestException("File audio không thuộc chương này.");
        }
        requireOwnership(audio);
        return AudioInfoDto.from(audio, chapterId);
    }

    /**
     * Mốc thời gian từng chữ của một bản audio, cho phần tô sáng theo giọng đọc.
     *
     * <p>Đi qua đúng hai lớp cửa như đường phát: chương bị khóa thì không đọc
     * được lời của nó, và bản của người khác thì không phải của mình. Mốc thời
     * gian chính là gần hết nội dung chương viết lại dưới dạng mảng — để hở chỗ
     * này là mở một cửa sau vào chương trả phí.
     *
     * <p>Bản không có mốc (admin tải lên, hoặc dựng từ trước khi có tính năng
     * này) trả về danh sách rỗng chứ không phải 404: "bản này không có chữ nào
     * được đánh mốc" là một câu trả lời đúng, không phải một lỗi.
     */
    @Transactional(readOnly = true)
    public ChapterTranscriptDto transcript(Long chapterId, Long audioId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        accessControlService.requireAccess(chapter);

        AudioFile audio = audioFileRepository.findById(audioId)
                .orElseThrow(() -> ResourceNotFoundException.of("file audio", audioId));

        if (!audio.getChapter().getId().equals(chapterId)) {
            throw new BadRequestException("File audio không thuộc chương này.");
        }
        requireOwnership(audio);

        List<WordTimestampDto> words = audioTranscriptRepository.findById(audioId)
                .map(transcript -> transcriptCodec.decode(transcript.getWordsJson()))
                .orElseGet(List::of);

        return new ChapterTranscriptDto(
                chapter.getStory().getId(),
                chapterId,
                audioId,
                AudioInfoDto.streamPath(chapterId, audioId),
                audio.getContentHash(),
                words.size(),
                words);
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
        requireOwnership(audio);
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
