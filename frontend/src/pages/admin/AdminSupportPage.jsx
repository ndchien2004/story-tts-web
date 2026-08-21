import { useCallback, useEffect, useMemo, useState } from "react";
import { adminSupportApi } from "../../api/endpoints";
import Avatar from "../../components/Avatar";
import SupportThread from "../../components/support/SupportThread";
import { Badge, Button, EmptyState, SearchInput, Spinner } from "../../components/ui";
import { useAdminSupport } from "../../context/admin-support-context";
import { useSupportSocket } from "../../context/support-socket-context";
import { useSupportThread } from "../../hooks/useSupportThread";
import useDebouncedValue from "../../hooks/useDebouncedValue";
import { relativeTime } from "../../utils/format";
import AdminPage from "./AdminPage";

/**
 * Hộp thư hỗ trợ dùng chung của cả đội quản trị.
 *
 * <h3>Dựng bằng chính bộ khung của bảng quản trị, không phải một bố cục riêng</h3>
 * Bản đầu của màn hình này tự vẽ lấy: một {@code <h1>} của riêng nó, một lưới
 * hai cột tự định nghĩa, và những cái khung bo tròn không có ở đâu khác trong
 * console. Kết quả là nó trông như một trang web thứ hai dán vào — lệch mép với
 * thanh trên, lệch nếp với mọi màn hình bên cạnh.
 *
 * <p>Giờ nó dùng đúng những mảnh mà mọi trang quản trị khác dùng:
 * {@link AdminPage} lo thanh trên và đường dẫn, {@code .admin-split} lo hai
 * khoang cùng cái đường kẻ ở giữa, {@code .admin-panel} lo phần giấy. Cái duy
 * nhất còn của riêng nó là những gì thật sự chỉ nó có — một danh sách hội thoại
 * và một khung chat.
 *
 * <h3>Hàng đợi, không phải hộp thư riêng</h3>
 * Mọi quản trị viên nhìn thấy cùng một danh sách, và mốc "đã đọc" là <i>một</i>
 * mốc dùng chung — một người đã đọc thì việc ấy đã xong với cả đội. Đó là cách
 * một hàng đợi hỗ trợ vận hành, và cũng là thứ giữ cho hai người không cùng
 * nhảy vào trả lời một câu.
 */
export default function AdminSupportPage() {
  const [status, setStatus] = useState("");
  const [keyword, setKeyword] = useState("");
  const search = useDebouncedValue(keyword, 350);

  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);

  const socket = useSupportSocket();

  /*
   * Mấy con số ở đầu khoang đến từ context của cả bảng quản trị, không từ một
   * lượt gọi của riêng trang này.
   *
   * Trước đây trang tự gọi `adminSupportApi.summary()` song song với danh sách.
   * Từ khi tab "Hỗ trợ" có huy hiệu, con số ấy đã được hỏi ở tầng trên — và hai
   * bên cùng nghe một socket, nên MỖI tin nhắn sinh ra HAI lượt gọi cùng một
   * đường. Tệ hơn cái giá là hệ quả: hai lượt về không cùng lúc, nên có những
   * khoảnh khắc huy hiệu trên thanh bên và dòng chữ ngay bên cạnh nó nói hai
   * con số khác nhau về đúng một sự thật.
   *
   * Một nguồn, hai chỗ vẽ.
   */
  const support = useAdminSupport();

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await adminSupportApi.conversations({
        status: status || undefined,
        q: search || undefined,
        page: 0,
        size: 30,
      });
      setItems(page.content ?? []);
    } finally {
      setLoading(false);
    }
  }, [status, search]);

  useEffect(() => { load(); }, [load]);

  const thread = useSupportThread({
    mode: "admin",
    conversationId: selected,
    enabled: selected != null,
  });

  /*
   * Danh sách nghe THẲNG socket dùng chung, không chờ khung hội thoại.
   *
   * Đây là chỗ sửa lỗi "phải bấm F5 mới thấy tin mới". Trước đây kết nối nằm
   * bên trong useSupportThread, tức là nó chỉ tồn tại khi đã chọn một hội
   * thoại — nên quản trị viên ngồi nhìn danh sách mà chưa mở gì thì không có
   * đường nào để tin tới, và danh sách chỉ đổi sau nhịp 45 giây hoặc sau F5.
   *
   * Kết nối dùng chung mang khung tin của MỌI luồng xuống, nên ở đây không lọc
   * theo selected: một tin ở hội thoại đang đóng cũng phải đẩy dòng của nó
   * lên đầu danh sách.
   *
   * Làm mới cả trang danh sách thay vì vá tại chỗ dòng ấy: số chưa đọc, bản xem
   * trước và thứ tự sắp xếp đều do máy chủ tính, và một phép vá ở trình duyệt
   * sẽ là bản sao thứ hai của ba quy tắc ấy — đúng thứ lệch dần rồi không ai
   * biết. Một lượt gọi cho một tin nhắn là cái giá chấp nhận được ở quy mô của
   * một hộp thư hỗ trợ.
   */
  useEffect(() => {
    if (!socket) return undefined;
    return socket.subscribe((frame) => {
      if (frame.type === "message:new") {
        load();
        return;
      }
      // Chỉ mốc đã đọc của PHÍA HỖ TRỢ mới đổi được thứ gì trên danh sách này
      // — cụ thể là con số chưa đọc ở mép phải mỗi dòng. Mốc của người đọc thì
      // không: không cột nào ở đây vẽ nó ra. Khung tin ấy vẫn tới đây vì cả hai
      // phía dùng chung một hình dạng khung tin (xem `SupportRealtime`), nên
      // lọc nó ở đây là bỏ đi một lượt tải lại toàn bộ hộp thư mỗi lần có một
      // người đọc bất kỳ mở hộp thoại chat của họ ra.
      if (frame.type === "message:read" && frame.payload?.reader === "ADMIN") load();
    });
  }, [socket, load]);

  /*
   * Nhịp thưa, giữ lại làm lưới đỡ.
   *
   * Socket đã lo phần tức thời; cái này chỉ để danh sách không đứng yên vĩnh
   * viễn trong quãng mất kết nối, hoặc khi tin được ghi ở một bản ứng dụng
   * khác — xem phần "Nhiều bản ứng dụng" trong docs/SUPPORT_MESSAGING.md.
   */
  useEffect(() => {
    const timer = window.setInterval(load, 45_000);
    return () => window.clearInterval(timer);
  }, [load]);

  const current = useMemo(
    () => items.find((item) => item.conversation.id === selected) ?? null,
    [items, selected],
  );

  const changeStatus = async (next) => {
    if (!selected) return;
    await adminSupportApi.setStatus(selected, next);
    await load();
    thread.refresh();
    // Đổi trạng thái ghi một tin hệ thống vào luồng, trong cùng giao dịch — và
    // lượt ghi ấy đẩy luôn mốc đã đọc của phía gửi. Nghĩa là bấm "Đóng" có thể
    // kéo một luồng ra khỏi hàng đợi chờ trả lời, tức là đổi con số trên huy
    // hiệu. Đường này không đi qua socket, nên nó phải tự nói.
    support?.refresh();
  };

  const threadStatus = thread.conversation?.status;

  return (
    <AdminPage crumbs={[{ label: "Hỗ trợ" }]} title="Hỗ trợ">
      <div className="admin-split admin-support">
        {/* ---------------- Danh sách ---------------- */}
        <section className="admin-panel">
          <div className="admin-panel-head">
            <span className="admin-panel-title">Cuộc trò chuyện</span>
            {support?.loaded && (
              <span className="admin-stat" title="Số kết nối thời gian thực đang mở">
                {support.awaitingReply} chờ trả lời
                <span className="muted"> · {support.openConnections} kết nối</span>
              </span>
            )}
          </div>

          <div className="admin-panel-head support-inbox-filters">
            <SearchInput
              className="admin-search"
              value={keyword}
              placeholder="Tìm theo tên hoặc email…"
              onChange={(event) => setKeyword(event.target.value)}
            />
            <div className="support-tabs" role="tablist" aria-label="Lọc theo trạng thái">
              {STATUS_TABS.map((tab) => (
                <button
                  key={tab.value}
                  type="button"
                  role="tab"
                  aria-selected={status === tab.value}
                  className={`support-tab ${status === tab.value ? "active" : ""}`}
                  onClick={() => setStatus(tab.value)}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          <div className="admin-panel-body">
            {loading && items.length === 0 && <Spinner label="Đang tải hộp thư…" />}

            {!loading && items.length === 0 && (
              <EmptyState title="Chưa có cuộc trò chuyện nào">
                Khi một người đọc gửi tin hỗ trợ, nó sẽ hiện ở đây.
              </EmptyState>
            )}

            <ul className="support-inbox">
              {items.map((item) => (
                <li key={item.conversation.id}>
                  <button
                    type="button"
                    className={`support-inbox-row ${
                      selected === item.conversation.id ? "active" : ""
                    }`}
                    aria-current={selected === item.conversation.id ? "true" : undefined}
                    onClick={() => setSelected(item.conversation.id)}
                  >
                    <Avatar name={item.user.displayName} src={item.user.avatarUrl} size="sm" />

                    <span className="support-inbox-main">
                      <span className="support-inbox-line">
                        <span className="support-inbox-name">{item.user.displayName}</span>
                        {item.conversation.lastMessageAt && (
                          <time
                            className="support-inbox-time"
                            dateTime={item.conversation.lastMessageAt}
                          >
                            {relativeTime(item.conversation.lastMessageAt)}
                          </time>
                        )}
                      </span>

                      <span className="support-inbox-preview">
                        {item.lastMessagePreview ?? "Chưa có tin nhắn"}
                      </span>

                      {/* Chỉ vẽ hàng nhãn khi thật sự có nhãn để vẽ. Một hàng
                          rỗng vẫn chiếm chiều cao, và những dòng cao thấp khác
                          nhau là thứ khiến một danh sách trông xộc xệch. */}
                      {hasTags(item) && (
                        <span className="support-inbox-tags">
                          <ModeBadge mode={item.conversation.assistantMode} />
                          {item.conversation.status !== "OPEN" && (
                            <StatusBadge status={item.conversation.status} />
                          )}
                          {!item.user.enabled && <Badge tone="danger">Đã khóa</Badge>}
                          {item.user.vip && <Badge>VIP</Badge>}
                        </span>
                      )}
                    </span>

                    {/* Con số chưa đọc ở mép phải, ngoài cột chữ: nó phải tìm
                        thấy được bằng một cái liếc dọc, không phải bằng cách
                        đọc từng dòng. */}
                    {item.conversation.unread > 0 && (
                      <span className="support-inbox-unread" aria-label="tin chưa đọc">
                        {item.conversation.unread}
                      </span>
                    )}
                  </button>
                </li>
              ))}
            </ul>
          </div>
        </section>

        {/* ---------------- Hội thoại ---------------- */}
        <section className="admin-panel">
          {!selected && (
            <div className="admin-panel-body admin-panel-body-pad">
              <EmptyState title="Chọn một cuộc trò chuyện">
                Danh sách bên trái xếp theo hoạt động mới nhất.
              </EmptyState>
            </div>
          )}

          {selected && (
            <>
              <div className="admin-panel-head">
                <span className="admin-panel-title">
                  {current?.user.displayName ?? "Cuộc trò chuyện"}
                </span>
                {threadStatus && threadStatus !== "OPEN" && (
                  <StatusBadge status={threadStatus} />
                )}

                <div className="support-actions">
                  {threadStatus !== "CLOSED" && (
                    <Button variant="ghost" size="sm" onClick={() => changeStatus("CLOSED")}>
                      Đóng
                    </Button>
                  )}
                  {threadStatus !== "OPEN" && (
                    <Button variant="ghost" size="sm" onClick={() => changeStatus("OPEN")}>
                      {threadStatus === "BLOCKED" ? "Bỏ khóa" : "Mở lại"}
                    </Button>
                  )}
                  {threadStatus !== "BLOCKED" && (
                    <Button variant="danger" size="sm" onClick={() => changeStatus("BLOCKED")}>
                      Khóa
                    </Button>
                  )}
                </div>
              </div>

              <SupportThread
                thread={thread}
                mySide="ADMIN"
                emptyHint="Chưa có tin nhắn nào trong cuộc trò chuyện này."
                composerPlaceholder="Trả lời người đọc…"
                disabledNotice={threadStatus === "BLOCKED"
                  ? "Người đọc không gửi được tin trong lúc luồng bị khóa. Bạn vẫn trả lời được."
                  : null}
              />
            </>
          )}
        </section>
      </div>
    </AdminPage>
  );
}

const STATUS_TABS = [
  { value: "", label: "Tất cả" },
  { value: "OPEN", label: "Đang mở" },
  { value: "CLOSED", label: "Đã đóng" },
  { value: "BLOCKED", label: "Đã khóa" },
];

/**
 * Dòng này có nhãn nào đáng vẽ không.
 *
 * {@code OPEN} cố ý không có nhãn: nó là trạng thái của gần như mọi dòng, và
 * một cái nhãn xuất hiện ở khắp nơi thì không phân biệt được gì — nó chỉ làm
 * dày danh sách. Nhãn ở đây dành cho những gì <i>khác</i> thường.
 */
function hasTags(item) {
  return item.conversation.status !== "OPEN"
    || item.conversation.assistantMode !== "HUMAN"
    || !item.user.enabled
    || item.user.vip;
}

/**
 * Ai đang phụ trách luồng, nhìn từ phía người trực.
 *
 * <h3>Hai nhãn, và cái thứ ba cố ý không có</h3>
 * <pre>
 *   AI      → "Trợ lý AI"      — không phải việc của bạn
 *   HANDOFF → "Chờ tư vấn viên" — đang có người chờ, chưa ai nhận
 *   HUMAN   → (không nhãn)      — trạng thái bình thường
 * </pre>
 *
 * {@code HUMAN} không có nhãn vì nó là mặc định của mọi luồng có từ trước V16
 * và của mọi luồng đã có người nhận. Gắn nhãn cho cái bình thường là làm loãng
 * hai cái bất thường — cùng lập luận với việc {@code StatusBadge} bỏ qua
 * {@code OPEN}.
 *
 * <p>Nhãn {@code AI} quan trọng hơn vẻ ngoài của nó: một luồng đang do trợ lý
 * phụ trách <i>không</i> hiện số chưa đọc và <i>không</i> vào phép đếm chờ trả
 * lời — xem {@code SupportConversationRepository}. Không có nhãn thì người trực
 * sẽ thấy một cuộc trò chuyện có tin mới mà không có con số nào bên cạnh, và
 * tưởng là hỏng.
 */
function ModeBadge({ mode }) {
  if (mode === "AI") return <Badge tone="info">Trợ lý AI</Badge>;
  if (mode === "HANDOFF") return <Badge tone="warning">Chờ tư vấn viên</Badge>;
  return null;
}

function StatusBadge({ status }) {
  if (status === "CLOSED") return <Badge>Đã đóng</Badge>;
  if (status === "BLOCKED") return <Badge tone="danger">Đã khóa</Badge>;
  if (status === "OPEN") return <Badge tone="success">Đang mở</Badge>;
  return null;
}
