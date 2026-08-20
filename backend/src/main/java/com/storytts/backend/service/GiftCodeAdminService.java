package com.storytts.backend.service;

import com.storytts.backend.domain.GiftCode;
import com.storytts.backend.domain.GiftCodeStatus;
import com.storytts.backend.dto.common.PageResponse;
import com.storytts.backend.dto.gift.GiftCodeDetailDto;
import com.storytts.backend.dto.gift.GiftCodeDto;
import com.storytts.backend.dto.gift.GiftCodeRedemptionDto;
import com.storytts.backend.dto.gift.GiftCodeRequest;
import com.storytts.backend.dto.gift.GiftCodeStatsDto;
import com.storytts.backend.exception.BadRequestException;
import com.storytts.backend.exception.ResourceNotFoundException;
import com.storytts.backend.repository.GiftCodeRedemptionRepository;
import com.storytts.backend.repository.GiftCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Khu quản trị của gift code: tạo, sửa, bật/tắt, và xem đã phát ra những gì.
 *
 * <h3>Ba quy tắc sửa đổi mà backend phải tự giữ</h3>
 * Giao diện có kiểm cả ba, và điều đó không tính: một request gửi thẳng bằng
 * curl cũng đi qua đúng những dòng dưới đây.
 *
 * <ol>
 *   <li><b>Không hạ {@code maxUses} xuống dưới số lượt đã phát.</b> Một mã đã
 *       phát 500 lượt mà {@code maxUses} bị đặt thành 100 là một dòng dữ liệu
 *       không mô tả trạng thái nào có thật: mọi báo cáo "còn lại bao nhiêu" trả
 *       lời bằng số âm, và ràng buộc CHECK ở V13 sẽ từ chối nó ở tầng cuối
 *       cùng — nhưng bằng một lỗi cơ sở dữ liệu, không phải bằng một câu người
 *       ta đọc được.</li>
 *   <li><b>Không đổi {@code code} của một mã đã có người đổi.</b> Dòng sổ cái Xu
 *       của họ ghi nguyên văn cái mã cũ trong phần mô tả. Đổi nó đi là để lại
 *       một lịch sử nói tới một thứ không còn tồn tại.</li>
 *   <li><b>Không xóa cứng một mã đã có người đổi.</b> Xem {@link #delete}.</li>
 * </ol>
 *
 * <h3>Nhật ký</h3>
 * Dự án chưa có bảng nhật ký kiểm toán riêng, và nếp đang dùng cho mọi thao tác
 * quản trị có hệ quả — {@code WalletAdminService}, {@code UserAdminService},
 * {@code ChapterEntitlementStore} — là một dòng {@code log.info} nêu rõ ai làm
 * gì với cái gì. Lớp này theo đúng nếp ấy thay vì dựng một cơ chế thứ hai chỉ
 * cho riêng nó. Cột {@code created_by} bổ sung phần mà log không giữ lâu được:
 * mã nào do ai tạo, tra được bằng một câu truy vấn sau nhiều tháng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GiftCodeAdminService {

    /** Số lần thử sinh mã trước khi chịu thua. Xem {@link #generateCode}. */
    private static final int GENERATE_ATTEMPTS = 5;

    private final GiftCodeRepository giftCodeRepository;
    private final GiftCodeRedemptionRepository redemptionRepository;
    private final CurrentUserService currentUserService;

    /* ------------------------------------------------------------------ */
    /* Đọc                                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Bảng gift code, lọc theo từ khóa, tình trạng và khoảng ngày tạo.
     *
     * <p>Việc lọc theo tình trạng xảy ra trong câu truy vấn chứ không sau khi nạp
     * về — xem {@code GiftCodeRepository.search}. Cùng một mốc {@code now} được
     * dùng cho cả phép lọc lẫn phép suy ra nhãn của từng dòng, nên không có dòng
     * nào lọt vào bộ lọc "Đang phát" rồi hiện nhãn "Hết hạn".
     *
     * @param status tên một {@link GiftCodeStatus}, hoặc null để không lọc
     */
    @Transactional(readOnly = true)
    public PageResponse<GiftCodeDto> list(String keyword, String status,
                                          Instant from, Instant to, Pageable pageable) {
        Instant now = Instant.now();
        return PageResponse.from(
                giftCodeRepository.search(blankToNull(keyword), validStatus(status),
                        from, to, now, pageable),
                code -> GiftCodeDto.from(code, now));
    }

    /** Một mã kèm hai con số đối chiếu — xem {@link GiftCodeDetailDto}. */
    @Transactional(readOnly = true)
    public GiftCodeDetailDto detail(Long id) {
        GiftCode code = find(id);
        return GiftCodeDetailDto.of(code,
                redemptionRepository.countByGiftCodeId(id),
                redemptionRepository.sumCoinsByGiftCode(id),
                Instant.now());
    }

    /** Danh sách người đã đổi một mã, mới nhất trước, có phân trang. */
    @Transactional(readOnly = true)
    public PageResponse<GiftCodeRedemptionDto> redemptions(Long id, Pageable pageable) {
        // Tra sự tồn tại trước, để một id sai trả về 404 chứ không phải một trang
        // rỗng trông y như một mã chưa ai đổi.
        if (!giftCodeRepository.existsById(id)) {
            throw ResourceNotFoundException.of("gift code", id);
        }
        return PageResponse.from(redemptionRepository.findByGiftCode(id, pageable),
                GiftCodeRedemptionDto::from);
    }

    @Transactional(readOnly = true)
    public GiftCodeStatsDto stats() {
        return new GiftCodeStatsDto(
                giftCodeRepository.count(),
                giftCodeRepository.countActive(Instant.now()),
                redemptionRepository.count(),
                redemptionRepository.sumAllCoins());
    }

    /* ------------------------------------------------------------------ */
    /* Ghi                                                                 */
    /* ------------------------------------------------------------------ */

    @Transactional
    public GiftCodeDto create(GiftCodeRequest request) {
        String code = requireNormalized(request.code());
        validateWindow(request.startAt(), request.endAt());

        if (giftCodeRepository.existsByCode(code)) {
            throw new BadRequestException("Gift code “" + code + "” đã tồn tại.");
        }

        GiftCode saved = save(GiftCode.builder()
                .code(code)
                .coinAmount(request.coinAmount())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .maxUses(request.maxUses())
                .enabled(request.enabled() == null || request.enabled())
                .description(blankToNull(request.description()))
                .createdBy(currentUserService.currentUserReference().orElse(null))
                .build(), code);

        log.info("Quản trị viên {} tạo gift code {} — {} Xu, tối đa {} lượt, hiệu lực {} → {}",
                currentUserService.currentUserId().orElse(null), code, saved.getCoinAmount(),
                saved.getMaxUses() == null ? "không giới hạn" : saved.getMaxUses(),
                saved.getStartAt() == null ? "ngay" : saved.getStartAt(),
                saved.getEndAt() == null ? "không hạn" : saved.getEndAt());
        return GiftCodeDto.from(saved, Instant.now());
    }

    @Transactional
    public GiftCodeDto update(Long id, GiftCodeRequest request) {
        GiftCode existing = find(id);
        String code = requireNormalized(request.code());
        validateWindow(request.startAt(), request.endAt());

        boolean used = existing.getUsedCount() > 0;

        // (2) Mã đã có người đổi thì không đổi tên được nữa. Dòng sổ cái Xu của
        //     họ ghi nguyên văn mã cũ; đổi nó đi là để lại một lịch sử nói tới
        //     một thứ không còn tồn tại.
        if (used && !code.equals(existing.getCode())) {
            throw new BadRequestException(
                    "Gift code đã có %d lượt đổi nên không đổi được mã. Hãy tắt mã này và tạo mã mới."
                            .formatted(existing.getUsedCount()));
        }
        if (giftCodeRepository.existsByCodeAndIdNot(code, id)) {
            throw new BadRequestException("Gift code “" + code + "” đã tồn tại.");
        }

        // (1) Trần lượt không được hạ xuống dưới số đã phát.
        Integer maxUses = request.maxUses();
        if (maxUses != null && maxUses < existing.getUsedCount()) {
            throw new BadRequestException(
                    "Gift code đã có %d lượt đổi nên số lượt tối đa không thể nhỏ hơn %d."
                            .formatted(existing.getUsedCount(), existing.getUsedCount()));
        }

        String previous = describe(existing);

        existing.setCode(code);
        existing.setCoinAmount(request.coinAmount());
        existing.setStartAt(request.startAt());
        existing.setEndAt(request.endAt());
        existing.setMaxUses(maxUses);
        existing.setDescription(blankToNull(request.description()));
        if (request.enabled() != null) {
            existing.setEnabled(request.enabled());
        }

        GiftCode saved = save(existing, code);

        // Ghi cả giá trị cũ lẫn mới: "đã sửa mã X" không trả lời được câu hỏi
        // duy nhất hay được hỏi về sau, là sửa từ cái gì thành cái gì.
        log.info("Quản trị viên {} sửa gift code {} — trước: {} — sau: {}",
                currentUserService.currentUserId().orElse(null), code, previous, describe(saved));
        return GiftCodeDto.from(saved, Instant.now());
    }

    /**
     * Bật hoặc tắt một mã.
     *
     * <p>Tách khỏi {@link #update} vì nó là thao tác duy nhất cần làm được trên
     * một mã đã phát: dừng ngay một đợt đang chạy không nên đòi người ta điền lại
     * cả biểu mẫu, nhất là lúc đang cần dừng gấp.
     */
    @Transactional
    public GiftCodeDto setEnabled(Long id, boolean enabled) {
        GiftCode code = find(id);
        code.setEnabled(enabled);
        GiftCode saved = giftCodeRepository.save(code);

        log.info("Quản trị viên {} {} gift code {} (đã đổi {} lượt)",
                currentUserService.currentUserId().orElse(null),
                enabled ? "bật lại" : "tắt", saved.getCode(), saved.getUsedCount());
        return GiftCodeDto.from(saved, Instant.now());
    }

    /**
     * Xóa một mã <b>chưa ai đổi</b>.
     *
     * <p>Có người đổi rồi thì không xóa: những dòng sổ đổi mã giải thích các dòng
     * Xu đã vào ví người ta, và xóa cái mã đi là làm mất lời giải thích ấy. Khóa
     * ngoại ở V13 cố ý không có {@code ON DELETE CASCADE} nên cơ sở dữ liệu cũng
     * sẽ từ chối; phép kiểm ở đây chỉ để câu trả lời là một câu tiếng Việt nói
     * đúng việc cần làm thay vì một lỗi ràng buộc.
     *
     * <p>Không dùng xóa mềm: đã có {@code enabled}, và một mã tắt <i>là</i> một mã
     * bị rút khỏi lưu thông. Thêm một cột {@code deleted_at} nữa sẽ tạo ra hai
     * cách diễn đạt cùng một trạng thái, và mọi truy vấn từ đó phải nhớ cả hai.
     */
    @Transactional
    public void delete(Long id) {
        GiftCode code = find(id);
        if (code.getUsedCount() > 0 || redemptionRepository.countByGiftCodeId(id) > 0) {
            throw new BadRequestException(
                    "Gift code này đã có người đổi nên không xóa được — lịch sử Xu của họ trỏ về nó. "
                            + "Hãy tắt mã để ngừng phát.");
        }
        giftCodeRepository.delete(code);
        log.info("Quản trị viên {} xóa gift code {} (chưa ai đổi)",
                currentUserService.currentUserId().orElse(null), code.getCode());
    }

    /**
     * Sinh một mã ngẫu nhiên chưa có trong cơ sở dữ liệu.
     *
     * <p>Vòng thử tồn tại vì {@link GiftCodes#generate} không hỏi cơ sở dữ liệu —
     * và cũng không nên hỏi. Với khoảng 2^50 khả năng, vòng này gần như không bao
     * giờ chạy quá một lượt; nó ở đây để đường sinh mã có một kết cục rõ ràng
     * thay vì trả về một mã trùng rồi để lệnh lưu hỏng sau đó.
     *
     * <p>Mã trả về mới chỉ là <i>đề nghị</i>: quản trị viên còn sửa được trước khi
     * lưu, và {@code UNIQUE(code)} mới là thứ quyết định lúc lưu.
     */
    @Transactional(readOnly = true)
    public String generateCode(String prefix) {
        for (int attempt = 0; attempt < GENERATE_ATTEMPTS; attempt++) {
            String candidate = GiftCodes.generate(prefix);
            if (!giftCodeRepository.existsByCode(candidate)) {
                return candidate;
            }
            log.warn("Mã sinh ra bị trùng ({}), thử lại lần {}", candidate, attempt + 2);
        }
        throw new BadRequestException("Không sinh được mã mới, vui lòng thử lại.");
    }

    /* ------------------------------------------------------------------ */
    /* Bên trong                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * Lưu, và biến một lần đụng {@code UNIQUE(code)} thành câu tiếng Việt.
     *
     * <p>{@code existsByCode} ở trên đã kiểm, nhưng hai quản trị viên tạo cùng một
     * mã trong cùng một giây thì cả hai đều thấy "chưa có". Chốt chặn là ràng
     * buộc; đây chỉ là chỗ dịch lời từ chối của nó sang thứ người ta đọc được.
     */
    private GiftCode save(GiftCode code, String label) {
        try {
            return giftCodeRepository.saveAndFlush(code);
        } catch (DataIntegrityViolationException ex) {
            throw new BadRequestException("Gift code “" + label + "” đã tồn tại.");
        }
    }

    private GiftCode find(Long id) {
        return giftCodeRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("gift code", id));
    }

    private static String requireNormalized(String raw) {
        String code = GiftCodes.normalize(raw);
        if (code == null) {
            throw new BadRequestException("Vui lòng nhập gift code.");
        }
        return code;
    }

    /**
     * Khoảng hiệu lực phải đi đúng chiều.
     *
     * <p>Bỏ trống một đầu là hợp lệ và có nghĩa — xem {@code GiftCode}. Chỉ khi có
     * cả hai thì thứ tự mới kiểm được, và một mã có {@code endAt} trước
     * {@code startAt} là một mã không bao giờ đổi được: nó sẽ nhảy thẳng từ "chờ
     * tới giờ" sang "hết hạn" mà không có phút nào ở giữa.
     */
    private static void validateWindow(Instant startAt, Instant endAt) {
        if (startAt != null && endAt != null && !startAt.isBefore(endAt)) {
            throw new BadRequestException("Thời gian bắt đầu phải trước thời gian kết thúc.");
        }
    }

    /** Tên tình trạng hợp lệ, hoặc null. Chuỗi lạ được coi như không lọc. */
    private static String validStatus(String status) {
        String value = blankToNull(status);
        if (value == null) {
            return null;
        }
        for (GiftCodeStatus known : GiftCodeStatus.values()) {
            if (known.name().equalsIgnoreCase(value)) {
                return known.name();
            }
        }
        return null;
    }

    /** Một dòng mô tả cấu hình, cho nhật ký "trước / sau". */
    private static String describe(GiftCode code) {
        return "%s / %d Xu / %s → %s / tối đa %s / %s".formatted(
                code.getCode(),
                code.getCoinAmount(),
                code.getStartAt() == null ? "ngay" : code.getStartAt(),
                code.getEndAt() == null ? "không hạn" : code.getEndAt(),
                code.getMaxUses() == null ? "không giới hạn" : code.getMaxUses(),
                code.isEnabled() ? "bật" : "tắt");
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
