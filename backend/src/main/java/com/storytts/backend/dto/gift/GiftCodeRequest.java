package com.storytts.backend.dto.gift;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Quản trị viên tạo hoặc sửa một gift code.
 *
 * <h3>Cái gì được kiểm ở đây, và cái gì không</h3>
 * Những chú thích dưới đây kiểm được hình dạng của từng trường một: mã không
 * rỗng, số Xu dương, số lượt dương. Ba quy tắc còn lại cần nhìn nhiều thứ cùng
 * lúc và nằm ở {@code GiftCodeAdminService}:
 *
 * <ul>
 *   <li>{@code startAt < endAt} — hai trường, không phải một.</li>
 *   <li>Mã không trùng — cần hỏi cơ sở dữ liệu.</li>
 *   <li>{@code maxUses} không được hạ xuống dưới số lượt đã phát — cần biết
 *       trạng thái hiện tại của dòng đang sửa.</li>
 * </ul>
 *
 * <p>Không có trường {@code usedCount}. Nó là kết quả của việc người ta đổi mã,
 * không phải một thứ để đặt; nhận nó từ client sẽ là cách nhanh nhất để một
 * request sửa cấu hình lặng lẽ ghi đè lên con số mà cả tính năng này dựa vào.
 *
 * @param code        mã; sẽ được chuẩn hóa thành chữ hoa trước khi lưu
 * @param coinAmount  số Xu mỗi lượt đổi nhận được
 * @param startAt     null nghĩa là có hiệu lực ngay
 * @param endAt       null nghĩa là không hết hạn
 * @param maxUses     null nghĩa là không giới hạn lượt
 * @param enabled     null lúc tạo nghĩa là bật
 */
public record GiftCodeRequest(

        @NotBlank(message = "Vui lòng nhập gift code")
        @Size(max = 64, message = "Gift code tối đa 64 ký tự")
        // Chỉ chữ, số và dấu gạch ngang. Khoảng trắng bị loại vì một mã có
        // khoảng trắng sẽ bị dán sai ở nửa số chỗ nó được chép qua; ký tự đặc
        // biệt bị loại vì mã còn đi qua URL và qua ô tìm kiếm.
        @Pattern(regexp = "^[A-Za-z0-9-]+$",
                message = "Gift code chỉ gồm chữ, số và dấu gạch ngang")
        String code,

        @NotNull(message = "Vui lòng nhập số Xu")
        @Min(value = 1, message = "Số Xu phải lớn hơn 0")
        Long coinAmount,

        Instant startAt,

        Instant endAt,

        // Trần trên chỉ để chặn một con số gõ nhầm (thêm ba số 0) biến thành
        // một mã coi như không giới hạn. Muốn không giới hạn thật thì bỏ trống.
        @Min(value = 1, message = "Số lượt tối đa phải lớn hơn 0")
        @Max(value = 10_000_000, message = "Số lượt tối đa quá lớn; để trống nếu không giới hạn")
        Integer maxUses,

        Boolean enabled,

        @Size(max = 300, message = "Ghi chú tối đa 300 ký tự")
        String description
) {
}
