import { Field, Select, TextInput } from "../ui";

/**
 * Chọn tình trạng xuất bản: nháp, đăng ngay, hay hẹn giờ.
 *
 * <h3>Vì sao một ô chọn cộng một ô giờ, không phải một cái công tắc</h3>
 * Công tắc chỉ nói được hai trạng thái, mà ở đây có ba — và trạng thái thứ ba
 * (hẹn giờ) mới là thứ đáng có: "ra chương lúc 20h thứ Sáu" là nếp làm việc
 * bình thường của một trang truyện, và trước đây nó phải làm bằng cách ngồi
 * canh giờ rồi bấm tay.
 *
 * <p>Ô giờ chỉ hiện ra khi đã chọn hẹn giờ. Để nó luôn hiện thì mỗi lần đăng
 * ngay lại có một ô ngày trống nằm cạnh, và người dùng phải tự đoán xem bỏ
 * trống nó nghĩa là gì.
 */
export default function PublishControl({ state, at, onChange, hint }) {
  return (
    <>
      <Field
        label="Tình trạng"
        htmlFor="publishState"
        hint={hint ?? "Bản nháp chỉ quản trị viên thấy."}
      >
        <Select
          id="publishState"
          value={state}
          onChange={(event) => onChange(event.target.value, at)}
        >
          <option value="PUBLISHED">Đăng ngay — mọi người đọc được</option>
          <option value="DRAFT">Bản nháp — chỉ mình thấy</option>
          <option value="SCHEDULED">Hẹn giờ — tự đăng khi tới giờ</option>
        </Select>
      </Field>

      {state === "SCHEDULED" && (
        <Field
          label="Đăng lúc"
          htmlFor="publishAt"
          hint="Tới giờ là tự hiện, không cần mở trang này lại."
        >
          <TextInput
            id="publishAt"
            type="datetime-local"
            required
            value={at}
            onChange={(event) => onChange(state, event.target.value)}
          />
        </Field>
      )}
    </>
  );
}

/**
 * Trạng thái ban đầu của ô, đọc từ những gì máy chủ trả về.
 *
 * @param publishState một trong DRAFT / SCHEDULED / PUBLISHED
 * @param publishedAt  mốc ISO, hoặc null với bản nháp
 */
export function publishFormFrom(publishState, publishedAt) {
  return {
    publishState: publishState ?? "PUBLISHED",
    publishAt: publishState === "SCHEDULED" ? toLocalInput(publishedAt) : "",
  };
}

/**
 * Phần payload nói về việc xuất bản.
 *
 * <p>`draft` là một cờ riêng chứ không phải "gửi publishedAt = null": JSON
 * không phân biệt được "không gửi trường này" với "gửi null", nên nếu null có
 * nghĩa là nháp thì một lời gọi chỉ định sửa cái tiêu đề sẽ lặng lẽ gỡ cả
 * chương xuống.
 */
export function publishPayload(form) {
  if (form.publishState === "DRAFT") {
    return { draft: true, publishedAt: null };
  }
  if (form.publishState === "SCHEDULED" && form.publishAt) {
    return { draft: false, publishedAt: new Date(form.publishAt).toISOString() };
  }
  // Đăng ngay: để máy chủ giữ nguyên giờ đăng cũ nếu đã có, còn không thì lấy
  // thời điểm hiện tại. Gửi giờ của trình duyệt lên đây sẽ dời mốc của một
  // chương đã đăng từ lâu về hôm nay chỉ vì có người sửa cái tiêu đề.
  return { draft: false, publishedAt: null };
}

/**
 * Mốc ISO → chuỗi mà `<input type="datetime-local">` nhận.
 *
 * Ô ấy chỉ đọc được `YYYY-MM-DDTHH:mm` theo giờ địa phương, nên không thể đưa
 * thẳng chuỗi ISO có chữ Z vào. Trừ đi độ lệch múi giờ trước khi cắt chuỗi là
 * cách ngắn nhất để con số hiện ra đúng giờ mà người dùng đang sống.
 */
function toLocalInput(iso) {
  if (!iso) return "";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "";
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 16);
}
