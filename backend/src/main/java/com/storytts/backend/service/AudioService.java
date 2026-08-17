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
import com.storytts.backend.service.storage.ByteRange;
import com.storytts.backend.service.storage.MediaNotFoundException;
import com.storytts.backend.service.storage.MediaSlice;
import com.storytts.backend.service.tts.TranscriptCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Set;

/**
 * Serves and manages chapter audio.
 *
 * Every read path calls {@link ChapterAccessService#requireAccess(Chapter)}
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

    /**
     * Trần dung lượng một bản thu admin tải lên.
     *
     * <p>Hạ từ 64MB xuống khi audio chuyển sang lưu ở Cloudinary. Đường lưu cục
     * bộ chép thẳng từ luồng xuống đĩa nên dung lượng file không ảnh hưởng bộ
     * nhớ, còn Cloudinary nhận file trong một request có chữ ký nên nội dung
     * buộc phải nằm trọn trong heap — mà heap ở đây là 224MB, dùng chung với mọi
     * request khác đang chạy. Chương audio thật đo được nằm trong khoảng 40KB
     * đến 8MB, nên con số này vẫn còn rộng gấp mấy lần chỗ cần dùng.
     */
    private static final long MAX_UPLOAD_BYTES = 32L * 1024 * 1024;

    private final AudioFileRepository audioFileRepository;
    private final AudioTranscriptRepository audioTranscriptRepository;
    private final TranscriptCodec transcriptCodec;
    private final ChapterService chapterService;
    private final ChapterAccessService chapterAccessService;
    private final StorageService storageService;
    private final AudioAssetRepair audioAssetRepair;
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
        chapterAccessService.requireAccess(chapter);

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
        chapterAccessService.requireAccess(chapter);

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
        chapterAccessService.requireAccess(chapter);

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
     * Kiểm quyền rồi trả về chỗ cần đọc — <b>không</b> mở luồng byte nào.
     *
     * <h3>Vì sao tách làm hai bước</h3>
     * Đây là một giao dịch, còn {@link #openStream} thì không, và ranh giới giữa
     * chúng nằm đúng trước lời gọi mạng đầu tiên. Khi audio nằm ở Cloudinary,
     * mở một lát byte là một vòng mạng đi ra ngoài; gộp nó vào giao dịch này là
     * cầm một kết nối cơ sở dữ liệu suốt quãng chờ ấy. Với pool mười kết nối và
     * hai mươi luồng Tomcat, vài người cùng bấm nghe là đủ để những request
     * bình thường xếp hàng chờ — đúng cái bẫy mà
     * {@link com.storytts.backend.service.tts.TtsGenerationWorker} đã tách ra
     * để tránh.
     *
     * <p>Không gộp được hai bước bằng cách gọi lẫn nhau trong cùng một bean:
     * {@code @Transactional} chạy bằng proxy, nên một method gọi method khác
     * cùng lớp thì annotation lặng lẽ vô hiệu. Bên gọi phải đi qua cả hai —
     * xem {@code AudioController.stream}.
     *
     * @throws com.storytts.backend.exception.ChapterLockedException if the caller
     *         may not access the parent chapter
     */
    @Transactional(readOnly = true)
    public StreamTarget resolveForStreaming(Long chapterId, Long audioId) {
        Chapter chapter = chapterService.findDetailEntity(chapterId);
        chapterAccessService.requireAccess(chapter);

        AudioFile audio = audioFileRepository.findById(audioId)
                .orElseThrow(() -> ResourceNotFoundException.of("file audio", audioId));

        if (!audio.getChapter().getId().equals(chapterId)) {
            throw new BadRequestException("File audio không thuộc chương này.");
        }
        requireOwnership(audio);
        if (audio.getStatus() != AudioStatus.READY || audio.getFilePath() == null) {
            throw new BadRequestException("File audio chưa sẵn sàng để phát.");
        }

        return new StreamTarget(audioId, audio.getFilePath(), audio.getContentType());
    }

    /**
     * Mở lát byte đã được {@link #resolveForStreaming} cho phép.
     *
     * <p>Cố ý không có {@code @Transactional}: xem lý do ở method trên.
     *
     * <p>Bản ghi trỏ tới một file không còn tồn tại được đánh dấu FAILED ngay
     * tại đây, chứ không chỉ báo lỗi rồi thôi — xem {@link AudioAssetRepair}.
     * Người bấm nghe vẫn nhận một thông báo, nhưng lần bấm "Nghe bằng AI" tiếp
     * theo dựng được bản mới thay vì gặp lại đúng bản chết ấy.
     *
     * @param range khoảng byte trình phát hỏi tới, hay null để phát từ đầu
     */
    public MediaSlice openStream(StreamTarget target, ByteRange range) {
        MediaSlice slice;
        try {
            slice = storageService.openAudio(target.key(), range);
        } catch (MediaNotFoundException ex) {
            audioAssetRepair.markMissing(target.audioId(), target.key());
            throw new ResourceNotFoundException(AudioAssetRepair.MESSAGE);
        }

        // Kiểu media lấy từ bản ghi trước: đó là kiểu của file lúc được nhận vào,
        // còn nơi lưu trữ chỉ là bên chuyển tiếp và có thể mô tả khác đi.
        String contentType = target.contentType() != null
                ? target.contentType()
                : slice.contentType();
        return new MediaSlice(slice.body(), slice.contentLength(), slice.totalLength(),
                slice.start(), slice.end(), contentType, slice.partial());
    }

    /**
     * Chỗ cần đọc của một bản audio đã qua kiểm quyền.
     *
     * @param audioId     để đánh dấu bản ghi nếu file của nó đã biến mất
     * @param key         khóa lưu trữ
     * @param contentType kiểu media ghi trong bản ghi, có thể null
     */
    public record StreamTarget(Long audioId, String key, String contentType) {
    }

    /**
     * Stores an admin-supplied recording, replacing any previous upload.
     *
     * <p><b>Thứ tự ở đây có chủ ý.</b> File được chép xuống đĩa trước mọi câu lệnh
     * SQL, vì Hibernate chỉ lấy kết nối khỏi pool ở câu lệnh đầu tiên chứ không
     * phải lúc giao dịch mở ra (xem {@code hibernate.connection.handling_mode}
     * trong application.properties). Đảo lại thứ tự này là giữ một kết nối suốt
     * lúc ghi một file tới 64 MB. Đổi lại, một chương không tồn tại sẽ để lại file
     * vừa ghi, nên nó được dọn ở khối catch bên dưới.
     */
    @Transactional
    public AudioInfoDto uploadForChapter(Long chapterId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Vui lòng chọn file audio để tải lên.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new BadRequestException(
                    "File audio vượt quá %d MB.".formatted(MAX_UPLOAD_BYTES / (1024 * 1024)));
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BadRequestException(
                    "Định dạng không được hỗ trợ. Vui lòng dùng file MP3, WAV, OGG hoặc M4A.");
        }

        String fileName;
        try {
            fileName = storageService.storeAudio(
                    file.getInputStream(), extensionOf(file.getOriginalFilename()));
        } catch (IOException ex) {
            throw new BadRequestException("Không đọc được file tải lên.");
        }

        try {
            Chapter chapter = chapterService.findDetailEntity(chapterId);

            // One recording per chapter: drop the old one so storage does not grow
            // every time an admin re-uploads.
            audioFileRepository
                    .findFirstByChapterIdAndSourceAndStatus(chapterId, AudioSource.UPLOAD, AudioStatus.READY)
                    .ifPresent(existing -> {
                        storageService.deleteAudio(existing.getFilePath());
                        audioFileRepository.delete(existing);
                    });

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
        } catch (RuntimeException ex) {
            // Giao dịch sẽ cuộn ngược, nên hàng trỏ tới file này không tồn tại.
            storageService.deleteAudio(fileName);
            throw ex;
        }
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
}
