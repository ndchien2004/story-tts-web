# Hộp thư hỗ trợ — Người đọc ↔ Quản trị viên

Đường nhắn tin hai chiều giữa người đọc và bộ phận hỗ trợ, chạy trên WebSocket.

Tài liệu này mô tả **cái đã dựng**: hợp đồng giao thức, ranh giới giao dịch,
những quyết định thiết kế kèm lý do, và những giới hạn đã biết. Nó là chỗ để đọc
trước khi sửa bất cứ gì trong `service/support`.

---

## 1. Nguyên tắc, và nó có nghĩa gì ở đây

```text
Cơ sở dữ liệu  = nguồn sự thật
WebSocket      = lớp vận chuyển thời gian thực
REST           = đồng bộ ban đầu, phục hồi, và đường lui
```

Câu trên dễ gật đầu và khó làm đúng, nên nói cụ thể nó ràng buộc điều gì:

- Một tin nhắn **không bao giờ** được đẩy đi trước khi giao dịch ghi nó commit.
  Mọi lượt đẩy nằm ở `@TransactionalEventListener(AFTER_COMMIT)`.
- Mất một khung tin **không mất dữ liệu**. Không có hàng đợi gửi lại, không có
  bảng outbox, không có số thứ tự sự kiện — vì bên nhận không chờ ai gửi: nó tự
  hỏi lại lịch sử ở mỗi lần nối lại.
- Một kết nối WebSocket **không giữ** kết nối cơ sở dữ liệu nào. Giao dịch mở ra
  và đóng lại bên trong việc xử lý *một* khung tin, đúng như một request HTTP.

## 2. Mô hình nghiệp vụ

**Một người đọc, một luồng, vĩnh viễn** — `UNIQUE (user_id)` trên
`support_conversations`. Đó cũng là phần chống đua của việc tạo: hai tab cùng mở
thì một bên thua ở tầng cơ sở dữ liệu và đọc lại hàng của bên thắng.

Quản trị viên **không** có luồng của riêng mình: phía hỗ trợ là một phía *chung*,
không phải một người. Hộp thư của họ là hàng đợi dùng chung của cả đội, và mốc
"đã đọc" của phía hỗ trợ cũng là **một** mốc dùng chung — một người đã đọc thì
việc ấy đã xong với cả đội.

### Trạng thái luồng

| Trạng thái | Người đọc gửi | Quản trị viên gửi | Ghi chú |
|---|---|---|---|
| `OPEN` | ✓ | ✓ | trạng thái của một luồng vừa tạo |
| `CLOSED` | ✓ → mở lại | ✓ → mở lại | coi là đã xong, **không** phải ngõ cụt |
| `BLOCKED` | ✗ | ✓ | công cụ chặn spam thật |

`CLOSED` **không** chặn gửi, và đó là câu trả lời cho cuộc đua "quản trị viên bấm
đóng đúng lúc người ta đang gõ": kết cục xác định là tin nhắn được giữ và luồng
quay lại `OPEN`, chứ không phải một câu bị nuốt mất mà người gửi không biết. Công
cụ chặn thật là `BLOCKED`.

Mỗi lần đổi trạng thái ghi một **tin hệ thống** vào chính luồng ấy, trong cùng
giao dịch — nên người đọc luôn biết vì sao ô soạn tin của họ vừa đổi.

## 3. Lược đồ

`V15__support_messaging.sql`. Phần đáng nhớ:

```text
support_conversations
  UNIQUE (user_id)                     ← một người một luồng; chống đua tạo trùng
  user_last_read_message_id            ← MỐC, không phải bộ đếm
  admin_last_read_message_id           ← một mốc chung cho cả đội hỗ trợ
  last_message_{id,at,preview,role}    ← bộ nhớ đệm, ghi cùng giao dịch với tin

support_messages
  id bigint auto_increment             ← THỨ TỰ nằm ở đây, không ở created_at
  UNIQUE (conversation_id, sender_id, client_message_id)   ← chống trùng
  INDEX (conversation_id, id)          ← phân trang con trỏ, đồng bộ
  INDEX (conversation_id, sender_role, id)  ← đếm chưa đọc
```

**Mốc, không phải bộ đếm.** Một bộ đếm phải cộng khi có tin và trừ khi có người
đọc; hai lệnh ấy chạy song song thì con số trôi đi không đường về. Một mốc chỉ có
một lệnh — "đẩy lên tới id này" — và số chưa đọc luôn là phép đếm dẫn xuất. Hệ
quả đúng theo yêu cầu: tin A, tin B, một lần đọc, rồi tin C — C không bao giờ bị
coi là đã đọc.

Mốc chỉ được phép **tăng** (`WHERE ... < :id`), nên hai tab bấm lệch nhịp không
kéo con số lùi lại.

## 4. REST API

Người đọc — **không đường nào nhận `conversationId`**; luồng suy từ quyền sở hữu.

```text
GET    /api/support/conversation          ?before= | ?after= | ?limit=
POST   /api/support/messages              { clientMessageId, content }
PATCH  /api/support/read                  { lastMessageId }
POST   /api/support/ws-ticket             → { ticket, expiresInSeconds }
```

Quản trị viên (dưới `hasRole('ADMIN')` ở tầng URL, kiểm lại ở tầng service):

```text
GET    /api/admin/support/conversations   ?status= &q= &page= &size=
GET    /api/admin/support/summary         → { awaitingReply, openConnections, adminConnections }
GET    /api/admin/support/conversations/{id}
GET    /api/admin/support/conversations/{id}/messages   ?before= | ?after= | ?limit=
POST   /api/admin/support/conversations/{id}/messages   { clientMessageId, content }
PATCH  /api/admin/support/conversations/{id}/read        { lastMessageId }
PATCH  /api/admin/support/conversations/{id}/status      { status }
```

### Ba cách gọi đường lịch sử

```text
không tham số   → trang mới nhất   (mở màn hình, và MỖI LẦN NỐI LẠI)
?before=<id>    → tin cũ hơn       (cuộn lên)
?after=<id>     → tin mới hơn      (vắng mặt lâu, một trang không đủ)
```

## 5. Hợp đồng WebSocket

**Đường:** `GET /ws/support?ticket=<vé>`

### Xác thực

`WebSocket` của trình duyệt là một hàm dựng nhận một URL — không đặt được header
`Authorization`, y hệt `EventSource`. Nên danh tính đi trên URL bằng một **vé
dùng một lần, sống 90 giây**, xin ở `POST /api/support/ws-ticket` (đường ấy đi
bằng header như mọi lời gọi khác).

Không dùng token phiên trên URL: nó sống 24 giờ và mở được mọi thứ, còn cái vé
thì đã hết hạn từ lâu vào lúc nó kịp lọt vào một access log.

`SupportHandshakeInterceptor` làm **hai** phép kiểm: đổi vé lấy danh tính, rồi
đối chiếu tài khoản với cơ sở dữ liệu (khóa? vai trò gì?). Trả `false` nghĩa là
**không có kết nối nào được mở** — không có trạng thái "đã nối nhưng chưa xác
thực".

### Khung tin đi lên

```jsonc
{ "type": "message:send", "clientMessageId": "<uuid>", "content": "…",
  "conversationId": 12 }   // chỉ quản trị viên; nhánh người đọc không đọc tới nó
{ "type": "message:read", "lastMessageId": 340, "conversationId": 12 }
{ "type": "ping" }
```

Không có `senderId`, `senderRole`, `createdAt`, `status`. Chúng không bị "bỏ
qua" — **không có biến nào để nhận**, và `@JsonIgnoreProperties(ignoreUnknown)`
khiến việc gửi chúng lên là một việc không có tác dụng gì.

### Khung tin đi xuống

```jsonc
{ "type": "connection:ready", "payload": { "role", "maxMessageLength",
                                           "historyPageSize", "serverTime" } }
{ "type": "message:new",  "payload": { "message", "conversation" } }      // người đọc
{ "type": "message:new",  "payload": { "message", "inbox" } }             // quản trị viên
{ "type": "message:ack",  "payload": { "clientMessageId", "messageId",
                                       "conversationId", "status", "createdAt" } }
{ "type": "message:read", "payload": { "conversationId", "reader",
                                       "lastReadMessageId", "readerUnread" } }
{ "type": "error",        "payload": { "code", "message", "clientMessageId" } }
```

Trường `type` ở ngoài cùng là thứ khiến việc thêm một loại khung tin mới không
làm hỏng máy khách cũ: chúng không nhận ra và bỏ qua.

### Ngữ nghĩa của ACK

| `status` | Nghĩa |
|---|---|
| `ACCEPTED` | tin vừa được ghi và đã commit |
| `DUPLICATE` | lần bấm gửi này đã được ghi từ trước; `messageId` là id của tin đã có |

**Cả hai đều là thành công.** Cả hai đều nghĩa là câu ấy nằm trong cơ sở dữ liệu
đúng một lần — đó chính là điều khiến việc gửi lại an toàn. Không có `REJECTED`:
một lượt gửi bị từ chối đi ra bằng khung `error` với mã riêng, vì lý do từ chối
là thứ giao diện phải phân biệt được ("quá dài" và "luồng đã bị khóa" dẫn tới hai
màn hình khác nhau).

Thứ tự trên đường dây là `message:new` rồi mới `message:ack` — hệ quả tất yếu của
việc đẩy tin xảy ra ở `AFTER_COMMIT`, tức là bên trong lời gọi gửi. Không sao:
cửa sổ đã gửi nhận ra tin của chính mình qua `clientMessageId`.

### Mã đóng kết nối

| Mã | Ý nghĩa | Trình duyệt phải làm gì |
|---|---|---|
| `4001` | vé sai / tài khoản không nạp được | không nối lại |
| `4002` | tài khoản bị khóa, hoặc quyền vừa đổi | **không nối lại** |
| `4003` | chạm trần kết nối | nghỉ lâu hơn rồi thử |
| `4004` | hết hạn sống, hoặc im lặng quá lâu | xin vé mới, nối lại ngay |
| `4005` | khung tin không đọc được | lỗi lập trình phía máy khách |

Không có mã thì cả năm đều là `onclose` và cách xử lý duy nhất còn lại là nối
lại — tức là một tài khoản vừa bị khóa sẽ nối lại mãi mãi.

### Mã lỗi

Cùng một hằng sinh ra cả mã HTTP của REST lẫn `code` của khung `error`, từ
`SupportException.Reason`:

```text
CONVERSATION_NOT_FOUND       404    CONVERSATION_ACCESS_DENIED   403
CONVERSATION_BLOCKED         409    MESSAGE_EMPTY                400
MESSAGE_TOO_LONG             400    MESSAGE_INVALID              400
INVALID_READ_TARGET          400    INVALID_STATUS_TRANSITION    409
SUPPORT_NOT_FOR_ADMIN        400    SUPPORT_RATE_LIMITED         429
```

## 6. Ranh giới giao dịch

```text
BEGIN
  SELECT ... FOR UPDATE     ← khóa hàng cuộc trò chuyện
  SELECT                    ← lần bấm gửi này đã ghi chưa (chống trùng)
  INSERT                    ← tin nhắn
  UPDATE                    ← bộ nhớ đệm tin cuối + mốc đã đọc của người gửi
  SELECT count(*) × 2       ← số chưa đọc của hai phía
COMMIT
  → publish sự kiện → tầng WebSocket đẩy đi
```

Không có lời gọi mạng, không có lượt gửi WebSocket, không có gì chờ trình duyệt
**bên trong** khối trên. Pool ở đây chỉ có mười kết nối.

**Chống trùng dựa vào ràng buộc, không dựa vào mức cô lập.** Ba lớp:

1. **Khóa hàng cuộc trò chuyện** — hai lượt gửi cùng một luồng xếp hàng.
2. **Phép kiểm trùng bên trong khóa** — đáng tin nhờ một chi tiết của InnoDB:
   `SELECT ... FOR UPDATE` là *locking read*, và locking read không mở read view
   của `REPEATABLE READ`. Ảnh chụp vì thế lập ở câu SELECT thường kế tiếp, tức là
   sau khi khóa về tay, tức là sau khi lượt trước commit.
3. **`UNIQUE (conversation_id, sender_id, client_message_id)`** — bảo đảm thật,
   và là thứ duy nhất không phụ thuộc chi tiết nào của cơ sở dữ liệu. Lỗi ràng
   buộc có đường hồi phục: `SupportService` bắt `DataIntegrityViolationException`
   rồi đọc lại tin đã ghi trong một giao dịch mới, trả về nó như một lần trùng.

> **Đừng đặt `isolation` riêng ở đây.** Bản đầu ghi
> `@Transactional(isolation = READ_COMMITTED)` và nó hỏng ở **mọi** lượt gửi trên
> bản chạy thật: `InvalidIsolationLevelException`. Spring chỉ áp được mức cô lập
> tùy chọn khi nó lấy được kết nối JDBC ngay lúc mở giao dịch, và điều đó đòi chế
> độ nhả kết nối `ON_CLOSE`. Ứng dụng này cố ý đặt
> `DELAYED_ACQUISITION_AND_RELEASE_AFTER_TRANSACTION` để vài phương thức ghi file
> hoặc gọi dịch vụ ngoài ở đầu một method `@Transactional` không cầm kết nối
> trong lúc chờ. Đổi cấu hình ấy để chiều một mức cô lập là đánh đổi sai chiều —
> và ba lớp ở trên đã đủ.
>
> `src/test/resources/application.properties` nay mang cùng khóa `handling_mode`
> với bản chính, chính vì lỗi ấy: thiếu nó, toàn bộ test xanh trong khi bản thật
> hỏng ở lượt gửi đầu tiên.

### Vì sao không có bảng outbox

Mẫu outbox tồn tại vì cơ sở dữ liệu và đường gửi có thể hỏng độc lập. Ở đây
`support_messages` **đã là** outbox: nó được ghi trong cùng giao dịch nghiệp vụ,
và bên nhận đồng bộ lại từ chính nó ở mỗi lần nối lại. Một khung tin mất trên
đường không mất mát gì, vì không có "lần gửi" nào là lần cuối cùng.

Thêm `status`, `attempt_count`, `available_at` cùng một tác vụ quét lại sẽ là bản
sao thứ hai của cùng dữ liệu, chạy trên một máy chủ ngủ sau mười lăm phút vắng
khách. Cùng kết luận đã ghi cho `notifications` ở V14.

## 7. Đồng bộ và phục hồi

### Một khoảng hở có thật, và cách nó được vá

Số thứ tự tin nhắn do cơ sở dữ liệu cấp lúc **ghi**, không phải lúc **commit**.
Hai giao dịch song song vì thế có thể commit ngược thứ tự id: tin 11 commit trước
tin 10. Một trình duyệt đồng bộ đúng vào khoảnh khắc ấy thấy 11 mà không thấy 10
— và nếu nó ghi nhớ "đã tới 11" rồi từ đó chỉ xin `?after=11`, tin số 10 **vĩnh
viễn** không về.

Nên mỗi lần nối lại, trình duyệt tải **trang cuối** của lịch sử chứ không xin
"phần sau con trỏ". Lượt đọc ấy xảy ra *sau* khoảnh khắc hở nên nó thấy cả hai;
việc gộp theo id và bỏ trùng khiến vài chục tin đã có không gây ra gì.

`?after=` vẫn tồn tại — cho trường hợp vắng mặt lâu, khi một trang không đủ.

### Ba đường đồng bộ

```text
mở màn hình         → tải trang mới nhất → phần đã có từ trước
WebSocket nối được  → tải trang mới nhất → phần bỏ lỡ lúc mất mạng
tab quay lại        → tải trang mới nhất → phần bỏ lỡ lúc trình duyệt ngủ
```

Đường thứ ba tồn tại vì trình duyệt trên điện thoại đóng băng cả tab lẫn kết nối
của nó khi người dùng chuyển ứng dụng, và nó không báo cho ai biết.

### Nối lại

Quãng nghỉ tăng dần (1s → 30s) **kèm ngẫu nhiên**. Ngẫu nhiên không phải để cho
đẹp: máy chủ khởi động lại thì mọi trình duyệt cùng mất kết nối trong một giây, và
một quãng nghỉ cố định sẽ khiến tất cả quay lại đúng một lúc.

## 8. Bảo mật

| Câu hỏi | Câu trả lời |
|---|---|
| Người A đọc được luồng người B? | Đường của người đọc **không có tham số `conversationId`** |
| Giả mạo `senderId` / `senderRole`? | Không có trường nào để nhận; máy chủ tự điền |
| Giả mạo `createdAt`? | Mốc luôn từ đồng hồ máy chủ |
| Tài khoản bị khóa giữa chừng? | Mỗi lệnh nạp lại tài khoản từ cơ sở dữ liệu; thêm vào đó, `AccountAccessRevoked` đóng mọi kết nối ngay |
| Quản trị viên bị hạ quyền giữa chừng? | Như trên, và kết nối bị đóng vì nó đang nằm sai nhóm định tuyến |
| Kết nối cũ sống mãi? | `session-max-lifetime` (30 phút) buộc bắt tay lại, và bắt tay đòi một phiên còn hiệu lực |
| Đánh dấu đã đọc tin của luồng khác? | `existsByIdAndConversationId` kiểm tin có thuộc luồng không |
| XSS? | Giao diện dựng nội dung bằng text node; không có `dangerouslySetInnerHTML` ở nhánh này |
| Trang lạ mở kết nối? | `setAllowedOrigins` — **trình duyệt không áp CORS lên WebSocket**, nên đây là phép kiểm duy nhất tồn tại |
| Khung tin 50MB? | `setTextMessageSizeLimit` theo từng phiên, đặt trước khi khung tin đầu tiên tới |

Hai hàng rào tần suất, ở hai chỗ khác nhau vì chúng bảo vệ hai thứ khác nhau:

```text
theo KẾT NỐI, trước khi phân tích (240 khung/phút) → CPU và luồng máy chủ
theo TÀI KHOẢN, trước khi ghi     (20 tin/phút)    → sức ép lên cơ sở dữ liệu
```

Hàng rào thứ hai đếm theo *người* chứ không theo *kết nối*: mở thêm tab không
được phép nhân đôi hạn mức.

### Quyền riêng tư của phía hỗ trợ

Người đọc thấy câu trả lời là của **"Hỗ trợ viên"** — không tên thật, không id,
không ảnh. Khu quản trị thì thấy đúng ai đã trả lời (một đội không phân biệt được
thì hai người sẽ cùng trả lời một câu). Việc chọn dạng nào **không** do trình
duyệt quyết định mà do đường đi: `/api/support/**` luôn dựng dạng thứ nhất.

## 9. Triển khai

### Nhiều bản ứng dụng — giới hạn đã biết

Sổ kết nối nằm trong bộ nhớ của **một tiến trình**. Người dùng nối vào bản A và
quản trị viên nối vào bản B thì:

- tin nhắn **vẫn được ghi** và **vẫn tới nơi** — nó nằm trong cơ sở dữ liệu, và
  mỗi lần nối lại hay quay lại tab đều kéo về phần bỏ lỡ;
- nhưng nó **không tới ngay lập tức**.

Trang này hiện chạy **một bản** (`render.yaml`, gói free), nên đó là một giới hạn
đã biết chứ không phải một lỗi đang có. Khi cần chạy nhiều bản, có hai chỗ phải
đổi và chỉ hai:

1. `SupportSocketRegistry` — thêm một lớp chuyển tiếp Redis pub/sub đứng giữa nó
   và `SupportRealtime`. Thêm một lớp, không sửa lớp nào.
2. `OneTimeTicketStore` — vé phát ở bản A không đổi được ở bản B. Sticky session
   ở bộ cân bằng tải giải quyết được mà không phải sửa mã.

### Proxy và bộ cân bằng tải

- Nhịp ping mặc định **25 giây**, ngắn hơn hạn chờ nhàn rỗi của Render và của
  hầu hết proxy. Đứng sau một proxy chặt hơn thì hạ `SUPPORT_HEARTBEAT_INTERVAL`.
- Nginx cần `proxy_set_header Upgrade`/`Connection` và một
  `proxy_read_timeout` rộng hơn nhịp ping.
- `CORS_ALLOWED_ORIGINS` dùng chung cho cả REST lẫn WebSocket, nên không có hai
  danh sách để lệch nhau.
- Đường `/ws/support` để `permitAll` ở tầng URL vì chuỗi lọc không có header nào
  để đọc; việc kiểm quyền nằm trong interceptor bắt tay.

### Cấu hình

Xem `.env.example`, mục "Hop thu ho tro". Mọi giá trị có mặc định — để trống tất
cả thì tính năng vẫn chạy.

## 10. Kiểm thử

```text
SupportJpaTest             32 bài  ghi/đọc, chống trùng, phân quyền, chưa đọc,
                                   vòng đời luồng, phân trang, sự kiện
                                   — KHÔNG mở một kết nối nào (đó là một khẳng định)
SupportConcurrencyTest      6 bài  giao dịch chạy thật sự song song
SupportContentTest         10 bài  làm sạch nội dung, từng ký tự
SupportSocketRegistryTest  14 bài  định tuyến, trần, dọn dẹp, đá ra
SupportHandshakeAccessTest  7 bài  ai mở được kết nối
```

`SupportConcurrencyTest` là `@SpringBootTest` chứ không `@DataJpaTest`, và đó là
điều kiện: `@DataJpaTest` gói mọi lệnh ghi trong *một* giao dịch, nên không có
hai giao dịch nào để nhìn thấy nhau.

`SupportHandshakeAccessTest` đáng nói riêng: nếu hai phép kiểm trong interceptor
bị xóa, **không một bài kiểm nào khác trong dự án đỏ lên** — trong khi một người
lạ mở được đường nhận tin nhắn riêng của người khác.

---

## Phụ lục — những gì cố ý **không** làm

| Không có | Vì sao |
|---|---|
| STOMP / SockJS | Mang theo một mô hình phân quyền thứ hai đặt cạnh mô hình đã có |
| Bảng outbox | `support_messages` đã là outbox — xem §6 |
| Sửa / xóa tin nhắn | Ngoài phạm vi; một cột `deleted_at` không mã nào ghi là một lời hứa suông |
| Tệp đính kèm | Chưa có đường tải lên, phát lại, hay hạn dung lượng |
| Chỉ báo "đang gõ" | Không có yêu cầu; nó cần một kênh trạng thái tạm và một hạn tự hết |
| Trạng thái `ARCHIVED` | Không đổi quy tắc nghiệp vụ nào — chỉ là một cách lọc, mà `CLOSED` đã làm |
| Cột `status` trên tin nhắn | Trạng thái ấy thuộc về *quan hệ* giữa tin và người đọc, và được suy từ mốc đã đọc |
| Tác vụ tự xóa tin cũ | Không có chính sách lưu trữ nào định nghĩa nó; dữ liệu thuộc về người đọc |
