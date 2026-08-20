import { useCallback, useEffect, useState } from "react";
import { adminApi } from "../../api/endpoints";
import AdminPage from "./AdminPage";
import { useAdminToast } from "../../context/admin-toast-context";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { Alert, Button, Field, Select, TextArea, TextInput } from "../../components/ui";

const TYPES = [
  { value: "ANNOUNCEMENT", label: "Thông báo chung" },
  { value: "SYSTEM", label: "Thông tin hệ thống" },
];

const PRIORITIES = [
  { value: "INFO", label: "Bình thường" },
  { value: "WARNING", label: "Cần để ý" },
  { value: "IMPORTANT", label: "Quan trọng" },
];

const EMPTY = {
  target: "ALL",
  userId: null,
  type: "ANNOUNCEMENT",
  priority: "INFO",
  title: "",
  message: "",
};

/**
 * Soạn một tin gửi cho người đọc.
 *
 * <h3>Đây là màn hình duy nhất tạo thông báo bằng tay</h3>
 * Bốn loại thông báo còn lại — VIP, gỡ chương, hoàn Xu, thanh toán — không có
 * màn hình nào cả: chúng sinh ra ngay bên trong giao dịch nghiệp vụ đã tạo ra
 * chúng, vì đó là cách duy nhất để một lời báo "đã hoàn 100 Xu" không bao giờ
 * đi ra khi lần hoàn ấy chưa commit. Ở đây thì ngược lại: không có nghiệp vụ nào
 * phía sau, chỉ có một người muốn nói một câu.
 *
 * <h3>Bấm hai lần không gửi hai lần</h3>
 * Máy chủ dựng khóa chống trùng từ chính nội dung cộng ngày gửi, nên gửi lại
 * đúng câu ấy trong cùng một ngày là một lệnh rỗng — xem
 * {@code AdminNotificationService}. Nút bị khóa trong lúc gửi chỉ là phép lịch
 * sự với con chuột; thứ thật sự chặn nằm ở tầng dưới.
 *
 * <h3>Chỉ có chữ, không có đường dẫn</h3>
 * Không có ô nhập URL, và đó là chủ ý: nút bấm trong một thông báo được dựng từ
 * một tập ý định cố định ở phía trình duyệt, nên không có đường nào để một
 * chuỗi tự do trở thành đích đến của một liên kết. Nội dung cũng được hiện ra
 * dưới dạng chữ thuần, nên gõ thẻ HTML vào đây sẽ thấy đúng những ký tự ấy.
 */
export default function AdminNotificationsPage() {
  const [form, setForm] = useState(EMPTY);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState(null);
  const notify = useAdminToast();

  const set = (patch) => setForm((current) => ({ ...current, ...patch }));

  const toOne = form.target === "USER";
  const canSend =
    form.title.trim().length > 0 &&
    form.message.trim().length > 0 &&
    (!toOne || form.userId !== null);

  async function send(event) {
    event.preventDefault();
    if (!canSend || sending) return;

    setSending(true);
    setError(null);
    try {
      const result = await adminApi.sendNotification({
        target: form.target,
        userId: toOne ? form.userId : null,
        type: form.type,
        priority: form.priority,
        title: form.title.trim(),
        message: form.message.trim(),
      });

      // Nói cả hai con số. Chúng lệch nhau khi một phần người nhận đã có đúng
      // tin này từ lần bấm trước, và im lặng về điều đó sẽ khiến quản trị viên
      // tưởng lần gửi vừa rồi hỏng.
      notify(
        result.created === result.recipients
          ? `Đã gửi tới ${result.recipients} người.`
          : `Đã gửi tới ${result.created}/${result.recipients} người; số còn lại đã nhận tin này từ trước.`,
      );
      setForm({ ...EMPTY, target: form.target });
    } catch (err) {
      setError(err.message);
    } finally {
      setSending(false);
    }
  }

  return (
    <AdminPage crumbs={[{ label: "Thông báo" }]} title="Thông báo">
      {error && <Alert tone="error">{error}</Alert>}

      <section className="admin-panel">
        <div className="admin-panel-head">
          <span className="admin-panel-title">Gửi thông báo</span>
        </div>

        <form className="admin-panel-body admin-panel-body-pad vip-plan-form" onSubmit={send}>
          <Field label="Người nhận" htmlFor="notif-target">
            <Select
              id="notif-target"
              value={form.target}
              onChange={(event) => set({ target: event.target.value, userId: null })}
            >
              <option value="ALL">Tất cả thành viên đang hoạt động</option>
              <option value="USER">Một thành viên</option>
            </Select>
          </Field>

          {toOne && <UserPicker onPick={(userId) => set({ userId })} picked={form.userId} />}

          <div className="vip-form-wide">
            <Field label="Loại" htmlFor="notif-type">
              <Select
                id="notif-type"
                value={form.type}
                onChange={(event) => set({ type: event.target.value })}
              >
                {TYPES.map((entry) => (
                  <option key={entry.value} value={entry.value}>
                    {entry.label}
                  </option>
                ))}
              </Select>
            </Field>

            <Field
              label="Mức độ"
              htmlFor="notif-priority"
              hint="Chỉ đổi cách hiện ra, không bật hộp thoại nào."
            >
              <Select
                id="notif-priority"
                value={form.priority}
                onChange={(event) => set({ priority: event.target.value })}
              >
                {PRIORITIES.map((entry) => (
                  <option key={entry.value} value={entry.value}>
                    {entry.label}
                  </option>
                ))}
              </Select>
            </Field>
          </div>

          <Field label="Tiêu đề" htmlFor="notif-title" hint={`${form.title.length}/160`}>
            <TextInput
              id="notif-title"
              maxLength={160}
              value={form.title}
              onChange={(event) => set({ title: event.target.value })}
              placeholder="Bảo trì hệ thống tối nay"
            />
          </Field>

          <Field label="Nội dung" htmlFor="notif-message" hint={`${form.message.length}/500`}>
            <TextArea
              id="notif-message"
              rows={4}
              maxLength={500}
              value={form.message}
              onChange={(event) => set({ message: event.target.value })}
              placeholder="Trang sẽ tạm dừng khoảng 15 phút từ 23h00 để nâng cấp."
            />
          </Field>

          <div className="row">
            <Button type="submit" variant="primary" disabled={!canSend} loading={sending}>
              Gửi thông báo
            </Button>
          </div>
        </form>
      </section>
    </AdminPage>
  );
}

/**
 * Chọn một người nhận bằng cách gõ tên.
 *
 * <p>Dùng lại đúng đường tìm thành viên của bảng quản trị thay vì bắt quản trị
 * viên gõ id: một con số tài khoản là thứ không ai nhớ, và gõ nhầm nó nghĩa là
 * một người lạ nhận được tin nhắn dành cho người khác.
 */
function UserPicker({ onPick, picked }) {
  const [keyword, setKeyword] = useState("");
  const [results, setResults] = useState([]);
  const debounced = useDebouncedValue(keyword, 300);

  const search = useCallback(() => {
    if (debounced.trim().length < 2) {
      setResults([]);
      return undefined;
    }

    let cancelled = false;
    adminApi
      .listUsers({ keyword: debounced.trim(), page: 0, size: 8 })
      .then((data) => {
        if (!cancelled) setResults(data.content ?? []);
      })
      // Tìm hỏng thì ô vẫn gõ được; danh sách gợi ý trống là câu trả lời đủ.
      .catch(() => {
        if (!cancelled) setResults([]);
      });

    return () => {
      cancelled = true;
    };
  }, [debounced]);

  useEffect(search, [search]);

  return (
    <Field
      label="Tìm thành viên"
      htmlFor="notif-user"
      hint={picked ? `Đã chọn tài khoản #${picked}` : "Gõ ít nhất 2 ký tự của username hoặc email."}
    >
      <TextInput
        id="notif-user"
        value={keyword}
        onChange={(event) => {
          setKeyword(event.target.value);
          // Gõ tiếp là bỏ lựa chọn cũ: giữ nó lại sẽ khiến ô tìm kiếm hiện một
          // cái tên còn tin thì đi tới một cái tên khác.
          onPick(null);
        }}
        placeholder="username hoặc email"
      />

      {results.length > 0 && (
        <ul className="admin-picker">
          {results.map((user) => (
            <li key={user.id}>
              <button
                type="button"
                className={`admin-picker-item ${picked === user.id ? "active" : ""}`}
                onClick={() => {
                  onPick(user.id);
                  setKeyword(user.username);
                  setResults([]);
                }}
              >
                <strong>{user.displayName || user.username}</strong>
                <span className="muted">@{user.username}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Field>
  );
}
