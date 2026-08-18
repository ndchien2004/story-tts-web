package com.storytts.backend.service.tts;

import com.storytts.backend.config.TtsProperties;
import com.storytts.backend.domain.AudioFile;
import com.storytts.backend.domain.AudioSource;
import com.storytts.backend.domain.AudioStatus;
import com.storytts.backend.domain.Chapter;
import com.storytts.backend.domain.User;
import com.storytts.backend.dto.audio.AudioInfoDto;
import com.storytts.backend.dto.audio.TtsRequest;
import com.storytts.backend.dto.audio.VoiceOptionDto;
import com.storytts.backend.exception.TtsException;
import com.storytts.backend.repository.AudioFileRepository;
import com.storytts.backend.service.ChapterAccessService;
import com.storytts.backend.service.ChapterService;
import com.storytts.backend.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Turns chapter text into audio, reusing previous results where possible.
 *
 * Generated tracks are cached per (chapter, voice, speed). A repeat request for
 * the same combination returns the stored file instead of calling a provider
 * again, which keeps both latency and API usage down.
 *
 * <h3>Hai cửa vào, một cơ chế</h3>
 * Lớp này chỉ biết dựng audio và dùng lại bản cũ; nó không biết ai được phép
 * tiêu tiền. Khu quản trị gọi thẳng vào đây và không bị hạn mức nào. Người đọc
 * đi qua {@link ReaderTtsService}, lớp đó truyền vào một {@link ReaderBudget} —
 * và chỗ hỏi ngân sách nằm ngay trước lệnh ghi bản ghi mới, tức chỉ khi một lần
 * gọi API tính tiền là không thể tránh. Nhờ vậy nghe lại một bản đã có, hay bấm
 * lúc bản đang dựng dở, đều không tốn lượt nào của ai.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TtsService {

    private final ChapterService chapterService;
    private final ChapterAccessService chapterAccessService;
    private final AudioFileRepository audioFileRepository;
    private final StorageService storageService;
    private final TtsProperties properties;
    private final TtsEngine ttsEngine;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Ngân sách của bên gọi, chỉ được hỏi tới khi sắp phát sinh chi phí thật.
     *
     * <p>Ném ngoại lệ trong {@link #beforeNewGeneration(Chapter)} là từ chối.
     * Những yêu cầu được trả về từ cache không bao giờ đi qua đây.
     */
    public interface ReaderBudget {

        /** Khu quản trị: không hạn mức, không ghi tên ai vào bản ghi. */
        ReaderBudget UNMETERED = new ReaderBudget() {
            @Override
            public void beforeNewGeneration(Chapter chapter) {
                // Không chặn gì.
            }

            @Override
            public User requester() {
                return null;
            }
        };

        /**
         * Gọi ngay trước khi xếp hàng một bản dựng mới.
         *
         * @throws RuntimeException để từ chối yêu cầu
         */
        void beforeNewGeneration(Chapter chapter);

        /** Người sẽ bị trừ lượt, hoặc null nếu bản này không tính cho ai. */
        User requester();
    }

    public List<VoiceOptionDto> availableVoices() {
        return ttsEngine.availableVoices();
    }

    /**
     * Starts or resumes synthesis for a chapter.
     *
     * Returns immediately: a fresh request comes back as PROCESSING and the
     * caller polls the chapter's track list until it is READY.
     */
    @Transactional
    public AudioInfoDto requestForChapter(Long chapterId, TtsRequest request) {
        return requestForChapter(chapterId, request, ReaderBudget.UNMETERED);
    }

    /**
     * Như trên, nhưng có một ngân sách được hỏi ý kiến trước khi dựng bản mới.
     *
     * @param budget bên gọi tự quyết định từ chối hay không; xem {@link ReaderBudget}
     */
    @Transactional
    public AudioInfoDto requestForChapter(Long chapterId, TtsRequest request, ReaderBudget budget) {
        if (!properties.enabled()) {
            throw new TtsException("Chức năng tạo audio đang tạm tắt trên máy chủ.");
        }
        if (!ttsEngine.hasAnyProvider()) {
            throw new TtsException(
                    "Chưa cấu hình nhà cung cấp giọng đọc nào. "
                            + "Vui lòng đặt ELEVENLABS_API_KEY trong file .env.");
        }

        Chapter chapter = chapterService.findDetailEntity(chapterId);

        // The same gate the reader and the audio stream go through, so synthesis
        // can never be used to reach a chapter the caller cannot open.
        chapterAccessService.requireAccess(chapter);

        String voice = resolveVoice(request == null ? null : request.voice());
        int speed = resolveSpeed(request == null ? null : request.speed());

        // ---- Ảnh chụp, lấy trong cùng một lần đọc cơ sở dữ liệu ----
        //
        // Ba dòng này là điều kiện đúng đắn của cả lượt dựng. Nội dung và phiên
        // bản của nó phải được lấy ra cùng một lúc, từ cùng một thực thể, rồi
        // không đọc lại nữa: từ đây tới lúc luồng nền gọi nhà cung cấp có thể là
        // hàng phút, và trong quãng ấy Admin có thể sửa chương bao nhiêu lần tùy ý.
        //
        // Đọc phiên bản ở một thời điểm khác với lúc đọc nội dung sẽ cho ra thứ
        // tệ nhất có thể: một bản audio đọc chữ cũ nhưng mang nhãn phiên bản mới,
        // tức là một bản sai *và* trông hợp lệ với mọi phép kiểm tra sau đó.
        String content = chapter.getContent();
        int contentVersion = chapter.getContentVersion();
        String contentHash = hashContent(content);

        User requester = budget.requester();
        Long requesterId = requester == null ? null : requester.getId();

        // Khu quản trị đã lo chương này rồi thì người đọc không có gì để dựng.
        // Trang đọc vốn đã ẩn nút trong trường hợp ấy; đây là chốt chặn cho
        // đường gọi thẳng vào API, và nó trả về bản sẵn có thay vì báo lỗi —
        // thứ người bấm muốn là được nghe, không phải một lời từ chối.
        //
        // "Đã lo rồi" phải tính cả phiên bản: một bản của quản trị đọc theo nội
        // dung cũ thì không giúp gì cho người đang mở nội dung mới, và trả nó về
        // ở đây là dựng lại đúng cái lỗi mà mọi thứ còn lại đang chặn.
        if (requesterId != null) {
            AudioFile fromAdmin = audioFileRepository.findAdminOwnedForChapter(chapterId).stream()
                    .filter(audio -> audio.getStatus() == AudioStatus.READY)
                    .filter(audio -> Integer.valueOf(contentVersion).equals(audio.getContentVersion()))
                    .findFirst()
                    .orElse(null);
            if (fromAdmin != null) {
                return AudioInfoDto.from(fromAdmin, chapterId);
            }
        }

        // Phiên bản nằm trong khóa tra cứu, nên thứ trả về ở đây chắc chắn đọc
        // theo đúng nội dung vừa chụp. Không còn phép so hash nào sau lời gọi này
        // — trước kia có, và nó chính là chỗ duy nhất nội dung cũ bị phát hiện.
        AudioFile cached = audioFileRepository
                .findTtsCache(chapterId, contentVersion, voice, speed, requesterId)
                .orElse(null);
        if (cached != null) {
            // Đang dựng dở cho đúng phiên bản này: để nó chạy tiếp. Xóa hàng mà
            // luồng nền đang ghi vào chỉ tạo ra một lần gọi API nữa và một bản
            // ghi mồ côi. Đây cũng là thứ khiến bấm nút ba lần liên tiếp chỉ tốn
            // một lượt — xem phần idempotency ở javadoc của lớp.
            if (cached.getStatus() == AudioStatus.PROCESSING) {
                return AudioInfoDto.from(cached, chapterId);
            }
            if (cached.getStatus() == AudioStatus.READY) {
                return AudioInfoDto.from(cached, chapterId);
            }

            // Còn lại là FAILED: lần trước hỏng, và không có gì để giữ. Đây là
            // trường hợp *duy nhất* còn xóa file ở đường này. Bản của phiên bản
            // cũ không rơi vào đây nữa — chúng đã thành STALE ngay lúc Admin lưu
            // chương, và bị dọn theo hạn lưu giữ chứ không bị giật khỏi tay người
            // đang nghe dở. Xem AudioRetentionSweeper.
            log.info("Dựng lại chương {} sau một lần hỏng", chapterId);
            storageService.deleteAudio(cached.getFilePath());
            audioFileRepository.delete(cached);
            audioFileRepository.flush();
        }

        // Tới đây mới chắc là sẽ có một lần gọi API tính tiền: không còn bản nào
        // dùng lại được. Đây là chỗ duy nhất hỏi ngân sách, nên mọi đường trả về
        // từ cache ở trên đều miễn phí.
        budget.beforeNewGeneration(chapter);

        AudioFile pending = audioFileRepository.save(AudioFile.builder()
                .chapter(chapter)
                .source(AudioSource.TTS)
                .status(AudioStatus.PROCESSING)
                .voice(voice)
                .speed(speed)
                .contentVersion(contentVersion)
                .contentHash(contentHash)
                .contentType("audio/mpeg")
                .requestedBy(budget.requester())
                .build());

        log.info("Xếp hàng dựng audio chương {} phiên bản {} (giọng={}, tốc độ={})",
                chapterId, contentVersion, voice, speed);

        // Cả nội dung lẫn phiên bản của nó cùng đi theo sự kiện. Luồng nền không
        // được phép đọc lại chương để lấy chữ — nếu nó làm vậy thì "phiên bản đã
        // chụp" và "chữ đem đi đọc" là hai thứ khác nhau, và mọi phép so phiên
        // bản sau đó đều so nhầm đối tượng.
        eventPublisher.publishEvent(
                new TtsGenerationRequested(pending.getId(), content, contentVersion, voice, speed));

        return AudioInfoDto.from(pending, chapterId);
    }

    /**
     * Mã giọng sẽ thật sự đem đi dựng, đồng thời là một phần khóa cache.
     *
     * <p>Phép thử ở đây là "có nhà cung cấp nào nhận mã này không", chứ không
     * phải "mã này có nằm trong danh sách giọng không". Danh sách giọng là một
     * lần gọi mạng tới nhà cung cấp; hỏng một lần là mọi mã giọng đều trông như
     * không hợp lệ, và bản dựng ra sẽ mang một giọng khác hẳn giọng đã chọn.
     *
     * <p>Không khớp thì báo lỗi chứ không lặng lẽ thay giọng khác. Đổi thứ người
     * dùng đã chọn mà không nói là kiểu hỏng khó truy nhất: người bấm nghe ra
     * giọng lạ nhưng mọi màn hình đều báo thành công.
     */
    private String resolveVoice(String requested) {
        if (requested != null && !requested.isBlank()) {
            String trimmed = requested.trim();
            if (ttsEngine.supportsVoice(trimmed)) {
                return trimmed;
            }
            throw new TtsException(
                    "Giọng đọc \"%s\" không thuộc nhà cung cấp nào đang bật. "
                            .formatted(trimmed)
                            + "Vui lòng chọn lại giọng trong danh sách.");
        }

        String fallback = ttsEngine.defaultVoiceCode();
        if (fallback == null) {
            throw new TtsException(
                    "Chưa cấu hình giọng đọc mặc định. "
                            + "Vui lòng đặt ELEVENLABS_VOICE_ID trong file .env rồi khởi động lại máy chủ.");
        }
        return fallback;
    }

    /**
     * Tốc độ giờ chỉ còn là một phần khóa cache.
     *
     * <p>ElevenLabs không nhận tham số tốc độ, nên giá trị này không đổi được
     * giọng đọc nhanh hay chậm; nó ở lại vì khóa cache
     * {@code (chương, giọng, tốc độ)} vẫn dùng tới, và vì người nghe đã có nút
     * chỉnh tốc độ phát ngay trên trình phát — chỗ đó mới là nơi việc này thuộc về.
     */
    /**
     * Dấu vân tay của nội dung chương, dùng để biết bản audio cũ còn khớp không.
     *
     * <p>Băm chứ không so chuỗi: chương dài hàng chục nghìn ký tự, mà thứ cần
     * lưu lại chỉ là "có đổi hay không".
     */
    private String hashContent(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("JVM không có thuật toán SHA-256", ex);
        }
    }

    private int resolveSpeed(Integer requested) {
        int speed = requested != null ? requested : properties.defaultSpeed();
        return Math.max(-3, Math.min(3, speed));
    }
}
