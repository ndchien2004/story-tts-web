package com.storytts.backend.service;

import com.storytts.backend.domain.AiUsage;
import com.storytts.backend.domain.AiUsageKind;
import com.storytts.backend.repository.AiUsageRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sổ đếm lượt dùng AI nằm trong bộ nhớ, đủ để {@link AiUsageService} chạy thật.
 *
 * <h3>Vì sao không mock thẳng {@code AiUsageService}</h3>
 * Thứ đáng kiểm ở hai hàng rào chi phí là <i>phép đếm</i>: ai hết lượt, ai hết
 * trước, một lần từ chối có ăn mất phần của lần bấm sau hay không. Thay phép
 * đếm ấy bằng một con số dựng sẵn thì test chỉ còn kiểm được rằng nó biết tự trả
 * lời chính mình.
 *
 * <p>Nên lớp này chỉ giả <b>nơi lưu</b>, và chỉ hiện thực đúng những câu truy vấn
 * {@link AiUsageService} dùng tới. Phần thật sự thuộc về cơ sở dữ liệu — dòng sổ
 * sống sót qua việc dọn bản audio, khóa ngoại {@code ON DELETE SET NULL} — nằm ở
 * {@code AiUsageJpaTest}, chạy trên cơ sở dữ liệu thật.
 *
 * <p>Mốc thời gian không được lọc mà chỉ được ghi lại: mọi dòng dựng sẵn ở đây
 * đều thuộc hôm nay, còn câu hỏi "hôm nay bắt đầu lúc nào" có bài kiểm riêng và
 * hỏi tới {@link #lastSince()}.
 */
public final class InMemoryAiUsage {

    private final List<AiUsage> rows = new ArrayList<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AiUsageService service;
    private Instant lastSince;

    public InMemoryAiUsage() {
        this.service = new AiUsageService(buildRepository());
    }

    public AiUsageService service() {
        return service;
    }

    /** Mọi dòng đã ghi, kể cả dòng đã hoàn. */
    public List<AiUsage> rows() {
        return rows;
    }

    /** Mốc "từ đầu ngày" mà lần đếm gần nhất đã dùng. */
    public Instant lastSince() {
        return lastSince;
    }

    /** Người này đã tiêu sẵn bấy nhiêu lượt loại này trong hôm nay. */
    public void seed(Long userId, AiUsageKind kind, int count) {
        for (int i = 0; i < count; i++) {
            rows.add(AiUsage.builder()
                    .id(sequence.incrementAndGet())
                    .userId(userId)
                    .kind(kind)
                    .createdAt(Instant.now())
                    .build());
        }
    }

    private AiUsageRepository buildRepository() {
        AiUsageRepository repository = mock(AiUsageRepository.class);

        when(repository.save(any(AiUsage.class))).thenAnswer(invocation -> {
            AiUsage row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(sequence.incrementAndGet());
                rows.add(row);
            }
            return row;
        });

        when(repository.rankForUser(anyLong(), any(), any(), anyLong())).thenAnswer(invocation -> {
            lastSince = invocation.getArgument(2);
            Long userId = invocation.getArgument(0);
            long upTo = invocation.getArgument(3);
            return live(invocation.getArgument(1))
                    .filter(row -> row.getUserId().equals(userId))
                    .filter(row -> row.getId() <= upTo)
                    .count();
        });

        when(repository.rankGlobal(any(), any(), anyLong())).thenAnswer(invocation -> {
            lastSince = invocation.getArgument(1);
            long upTo = invocation.getArgument(2);
            return live(invocation.getArgument(0))
                    .filter(row -> row.getId() <= upTo)
                    .count();
        });

        when(repository.countForUser(anyLong(), any(), any())).thenAnswer(invocation -> {
            Long userId = invocation.getArgument(0);
            return live(invocation.getArgument(1))
                    .filter(row -> row.getUserId().equals(userId))
                    .count();
        });

        when(repository.countAll(any(), any())).thenAnswer(invocation ->
                live(invocation.getArgument(0)).count());

        when(repository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return rows.stream().filter(row -> row.getId().equals(id)).findFirst();
        });

        when(repository.findFirstByAudioFileIdAndRefundedAtIsNull(anyLong()))
                .thenAnswer(invocation -> {
                    Long audioId = invocation.getArgument(0);
                    return rows.stream()
                            .filter(row -> audioId.equals(row.getAudioFileId()))
                            .filter(row -> !row.isRefunded())
                            .findFirst();
                });

        return repository;
    }

    /** Những dòng còn tính vào hạn mức: đúng loại, và chưa được hoàn. */
    private Stream<AiUsage> live(AiUsageKind kind) {
        return rows.stream()
                .filter(row -> row.getKind() == kind)
                .filter(row -> !row.isRefunded());
    }
}
