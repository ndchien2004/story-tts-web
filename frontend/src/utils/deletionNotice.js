import { formatCoins } from "./format";

/**
 * Câu báo sau khi xóa một chương hoặc một truyện.
 *
 * <p>Xóa nội dung đã bán sẽ trả lại Xu cho người mua, trong cùng giao dịch với
 * lệnh xóa. Đó là một việc đụng tới tiền, xảy ra như hệ quả phụ của một cú bấm
 * nút — nên nó phải được nói ra. Không nói thì quản trị viên chỉ phát hiện khi
 * đọc báo cáo doanh thu tháng sau.
 *
 * <p>Và chỉ nói khi có gì để nói: chương không bán bằng Xu thì câu báo giữ
 * nguyên như trước, không kèm một mệnh đề "đã hoàn 0 Xu" vô nghĩa.
 *
 * @param base   câu báo cơ sở, ví dụ `Đã xóa chương “Chương 1”.`
 * @param result thân response của lệnh xóa
 */
export function deletionNotice(base, result) {
  const coins = result?.refundedCoins ?? 0;
  if (coins <= 0) {
    return base;
  }

  const readers = result?.refundedReaders ?? 0;
  return `${base} Đã hoàn ${formatCoins(coins)} cho ${readers} người đã mua.`;
}
