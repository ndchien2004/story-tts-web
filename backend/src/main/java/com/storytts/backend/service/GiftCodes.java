package com.storytts.backend.service;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Chuẩn hóa và sinh mã gift code. Không trạng thái, không phải bean.
 *
 * <h3>Một chỗ chuẩn hóa, không phải hai</h3>
 * Đường tạo mã và đường đổi mã <b>phải</b> gọi cùng một hàm. Nếu đường tạo viết
 * hoa còn đường đổi chỉ cắt khoảng trắng, kết quả không phải một lỗi báo ra được
 * — nó là một cái mã nằm trong cơ sở dữ liệu mà không ai đổi được, và không có
 * gì trong hệ thống nói rằng có chuyện đó. Ràng buộc {@code UNIQUE(code)} cũng
 * chỉ chặn được mã trùng nếu mọi thứ đi vào cột ấy đã qua đúng một phép biến đổi
 * này.
 *
 * <h3>Bảng chữ cái bỏ bớt bốn ký tự</h3>
 * Không có {@code I}, {@code O}, {@code 0}, {@code 1}. Mã gift code được đọc qua
 * điện thoại, chép lại từ ảnh chụp màn hình, gõ tay từ một tấm áp phích — và bốn
 * ký tự ấy là bốn cách phổ biến nhất để một người gõ đúng thứ họ nhìn thấy mà
 * vẫn ra sai mã. Bỏ chúng đi làm entropy mỗi ký tự giảm từ 5.17 xuống 5.04 bit;
 * bù lại bằng độ dài, thứ không tốn gì.
 */
public final class GiftCodes {

    /** 32 ký tự: A–Z và 2–9, trừ I, O. Xem ghi chú ở đầu lớp. */
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    /**
     * 10 ký tự sau tiền tố — 32^10, tức khoảng 2^50.
     *
     * <p>Nhiều hơn mức cần cho việc tránh trùng (đã có {@code UNIQUE} lo việc
     * ấy), và đó không phải mục đích. Mục đích là để không đoán được: một mã 6 ký
     * tự có thể bị dò hết bằng vài triệu request, và mỗi lần dò trúng là Xu thật
     * rời khỏi ngân sách của một đợt phát.
     */
    private static final int RANDOM_LENGTH = 10;

    /** Chia mã thành cụm cho dễ đọc và dễ đọc-cho-người-khác-chép. */
    private static final int GROUP_SIZE = 5;

    /** Giới hạn của cột {@code gift_codes.code}. */
    public static final int MAX_LENGTH = 64;

    /** Kiểu mật mã học, không phải {@link java.util.Random}: mã đoán được là Xu mất. */
    private static final SecureRandom RANDOM = new SecureRandom();

    private GiftCodes() {
    }

    /**
     * Dạng chuẩn của một mã: bỏ khoảng trắng hai đầu, viết hoa.
     *
     * <p>{@link Locale#ROOT} chứ không phải locale mặc định của máy chủ. Trong
     * locale Thổ Nhĩ Kỳ, {@code "i".toUpperCase()} cho ra {@code "İ"} — một ký tự
     * khác hẳn — nên một máy chủ đặt sai vùng sẽ lưu mã dưới một dạng và tra nó
     * dưới một dạng khác.
     *
     * @return null nếu đầu vào rỗng hoặc chỉ có khoảng trắng
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Sinh một mã ngẫu nhiên, kèm tiền tố nếu có.
     *
     * <p>Ví dụ: {@code generate("Summer")} → {@code SUMMER-8FK29-DLT4M}.
     *
     * <p>Không bảo đảm mã chưa tồn tại — việc ấy chỉ cơ sở dữ liệu trả lời được,
     * và bên gọi ({@code GiftCodeAdminService.generateCode}) thử lại vài lần trước
     * khi chịu thua. Với 2^50 khả năng thì vòng thử ấy gần như không bao giờ chạy
     * quá một lượt; nó tồn tại để đường sinh mã không có kết cục "im lặng ghi đè".
     *
     * @param prefix tiền tố tùy chọn; ký tự không thuộc [A-Z0-9] bị loại bỏ
     */
    public static String generate(String prefix) {
        StringBuilder out = new StringBuilder(MAX_LENGTH);

        String cleanPrefix = sanitizePrefix(prefix);
        if (!cleanPrefix.isEmpty()) {
            out.append(cleanPrefix).append('-');
        }

        for (int i = 0; i < RANDOM_LENGTH; i++) {
            if (i > 0 && i % GROUP_SIZE == 0) {
                out.append('-');
            }
            out.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return out.toString();
    }

    /**
     * Giữ lại phần dùng được của tiền tố người dùng gõ.
     *
     * <p>Cắt ngắn để phần ngẫu nhiên luôn còn đủ chỗ trong 64 ký tự của cột: một
     * tiền tố dài không được phép làm mã mất entropy.
     */
    private static String sanitizePrefix(String prefix) {
        String normalized = normalize(prefix);
        if (normalized == null) {
            return "";
        }
        String kept = normalized.replaceAll("[^A-Z0-9]", "");
        int room = MAX_LENGTH - RANDOM_LENGTH - (RANDOM_LENGTH / GROUP_SIZE) - 1;
        return kept.length() > room ? kept.substring(0, room) : kept;
    }
}
