# Triển khai

Hệ thống chạy trên ba dịch vụ, tất cả đều gói miễn phí.

```
        Vercel                    Render                     Aiven
   ┌──────────────┐         ┌────────────────┐         ┌──────────────┐
   │  React SPA   │ ──────► │  Spring Boot   │ ──────► │   MySQL 8    │
   │ vercel.json  │  HTTPS  │  Docker, 512MB │   TLS   │  1GB / 1 CPU │
   └──────────────┘         └───────┬────────┘         └──────────────┘
                                    │
                                    ▼
                            ┌────────────────┐
                            │   Cloudinary   │
                            │ audio + ảnh    │
                            └────────────────┘
```

---

## Điều quan trọng nhất phải biết về Render

**Hệ tệp của Render gói miễn phí là tạm thời.** File ghi ra đĩa biến mất khi:

- triển khai lại,
- khởi động lại,
- **và sau 15 phút không có ai truy cập** — dịch vụ ngủ đi, tỉnh dậy là một máy sạch.

Gói miễn phí cũng **không gắn được Persistent Disk** (tính năng của gói trả phí).

Hệ quả nếu để `STORAGE_DRIVER=local` trên Render: mỗi bản audio dựng bằng
ElevenLabs — tốn tiền thật cho từng bản — biến mất trong vòng một giờ, trong khi
hàng trong cơ sở dữ liệu (nằm ở Aiven, bền vững) vẫn ghi `status = READY`. Chương
ấy sau đó **không dựng lại được**, vì bộ nhớ đệm thấy đã có bản READY nên từ chối
gọi nhà cung cấp lần nữa.

> **Vì vậy `STORAGE_DRIVER=cloudinary` là bắt buộc trên Render.** Đây không phải
> một lựa chọn tối ưu hóa.

---

## Biến môi trường

Đặt trong Render → Environment. `render.yaml` là bản mô tả đối chiếu; biến nào có
`sync: false` thì phải điền tay ở dashboard.

### Bắt buộc

| Biến | Giá trị | Vì sao |
|---|---|---|
| `STORAGE_DRIVER` | `cloudinary` | Xem trên |
| `DB_SSL_MODE` | `REQUIRED` | Aiven bắt buộc mã hóa đường truyền |
| `DB_CREATE_IF_MISSING` | `false` | Aiven cấp sẵn database, tài khoản không được tạo thêm |
| `SEED_ENABLED` | `false` | Không bơm 6 truyện demo vào bản chạy thật |
| `DB_HOST` `DB_PORT` `DB_NAME` `DB_USER` `DB_PASSWORD` | từ Aiven | |
| `JWT_SECRET` | chuỗi ngẫu nhiên ≥ 32 ký tự | `openssl rand -base64 48` |
| `CLOUDINARY_CLOUD_NAME` `CLOUDINARY_API_KEY` `CLOUDINARY_API_SECRET` | từ Cloudinary | audio **và** ảnh đại diện |
| `CORS_ALLOWED_ORIGINS` | tên miền Vercel | |

### Tùy chọn (thiếu thì tính năng tương ứng tự tắt)

`ELEVENLABS_API_KEY`, `ELEVENLABS_VOICE_ID`, `GOOGLE_CLIENT_ID`,
`MAIL_USERNAME`, `MAIL_PASSWORD`, `PAYOS_*`.

---

## Luồng triển khai

```
git push (main)
      │
      ▼
GitHub Actions ── mvn test ── migration trên MySQL thật ── lint/build frontend
      │                                   │
      │                         không pass thì dừng ở đây
      ▼
Render tự dựng ảnh Docker từ backend/Dockerfile
      │
      ▼
Ứng dụng khởi động → Flyway migrate → ddl-auto=validate đối chiếu lược đồ
      │
      ▼
Render gọi /actuator/health cho tới khi 200
      │
      ▼
Chuyển lưu lượng sang bản mới
```

**Migration chạy trong ứng dụng, lúc khởi động.** Không có bước thủ công nào.
Flyway đang ở `baseline-on-migrate=true` vì cơ sở dữ liệu có từ thời `ddl-auto=update`;
với một database sạch hoàn toàn thì nó chạy V1 thật sự.

**Nhập nội dung KHÔNG nằm trong luồng này** — xem `content/README.md`. Một tệp
JSON hỏng không được phép làm cả trang web không lên được.

---

## Kiểm tra sau khi triển khai

```bash
curl https://<app>.onrender.com/actuator/health          # {"status":"UP"}
```

Rồi trong giao diện:

1. Mở một chương công khai — đọc được chữ.
2. Bấm play — nghe được, và tua được (kéo thanh thời gian).
3. Đăng nhập bằng tài khoản admin → khu quản trị → tải lên một file audio.
4. **Đợi hơn 15 phút cho dịch vụ ngủ, rồi mở lại và bấm play.** Đây là phép thử
   quyết định: audio còn nghe được nghĩa là nó thật sự nằm ở Cloudinary.

Đối chiếu toàn bộ cơ sở dữ liệu với nơi lưu file:

```
GET  /api/admin/storage/audit     # chỉ đọc, liệt kê bản ghi trỏ vào hư không
POST /api/admin/storage/repair    # đánh dấu chúng hỏng để dựng lại được
```

---

## Dọn dữ liệu cũ từ thời hệ tệp tạm thời

Cơ sở dữ liệu hiện tại gần như chắc chắn có những hàng `READY` trỏ tới file đã
biến mất cùng một lần ngủ nào đó của Render.

Không cần làm gì cả — hệ thống **tự chữa**: lần đầu có người bấm play một bản
như thế, nó được đánh dấu `FAILED` ngay tại chỗ, và nút "Nghe bằng AI" dựng lại
được bản mới. Muốn dọn hết một lượt thì gọi `POST /api/admin/storage/repair`.

Cách này được chọn thay vì rà soát lúc khởi động, vì trên Render "lúc khởi động"
nghĩa là vài chục lần một ngày, và mỗi lượt rà soát là một vòng mạng cho từng
hàng.

---

## Giới hạn cần theo dõi

| Dịch vụ | Hạn mức | Chạm trần thì sao |
|---|---|---|
| **Aiven MySQL** | **1 GB** đĩa (đã hạ từ 5GB tháng 5/2025), 76 kết nối | Ghi bị từ chối. `audio_transcripts` (~300KB/bản) là bảng lớn nhanh nhất. |
| **Cloudinary** | 25 credits/tháng (1 credit = 1GB lưu **hoặc** 1GB băng thông) | Cảnh báo từ 90%, sau đó khóa tài khoản. |
| **Render** | 750 giờ/tháng, băng thông theo workspace | Dịch vụ bị tạm dừng tới tháng sau. |
| **Vercel** | băng thông gói Hobby | |

Ước tính: audio chương đo được nằm trong khoảng 40KB–8MB, trung bình ~1.8MB.
25 credits ≈ 10GB lưu + 15GB băng thông ≈ **khoảng 5.000 chương**.

**Khi nào cần chuyển sang nơi khác:** Cloudinary chạm 80% credits, hoặc cần chạy
nhiều hơn một instance. Lúc đó thêm một implementation của `MediaStorage`
(ví dụ `S3MediaStorage` cho Cloudflare R2) và đổi `STORAGE_DRIVER` — không phần
nào của tầng nghiệp vụ phải sửa.

---

## WebSocket (hộp thư hỗ trợ)

Kết nối chạy ở `/ws/support`. Không có API key nào phải điền — mọi thứ có mặc
định, và để trống toàn bộ `SUPPORT_*` thì tính năng vẫn chạy. Bốn điều phải biết
trước khi triển khai:

**1. Render hỗ trợ WebSocket sẵn, kể cả gói free.** Không cần bật gì. Nhưng nó
đóng một kết nối im lặng quá lâu, nên nhịp ping mặc định là **25 giây** — ngắn
hơn hạn chờ của cả Render lẫn hầu hết proxy. Đứng sau một proxy chặt hơn thì hạ
`SUPPORT_HEARTBEAT_INTERVAL`; đứng sau Nginx tự dựng thì nhớ
`proxy_set_header Upgrade` / `Connection` và một `proxy_read_timeout` rộng hơn
nhịp ping.

**2. `CORS_ALLOWED_ORIGINS` áp cho cả WebSocket**, và ở đó nó là hàng rào *duy
nhất*: trình duyệt **không** áp CORS lên WebSocket, nên một tên miền không nằm
trong danh sách bị chặn ở phía máy chủ chứ không phải phía trình duyệt. Đổi tên
miền frontend mà quên biến này thì hộp thư hỗ trợ ngừng kết nối trong khi mọi
thứ khác vẫn chạy bình thường — một cách hỏng rất khó đoán nếu không biết trước.

**3. Dịch vụ ngủ sau 15 phút vắng khách** — kết nối đứt hết, và trình duyệt tự
nối lại (quãng nghỉ tăng dần, có ngẫu nhiên để nhiều tab không cùng quay lại một
lúc). Không mất tin nhắn nào: cơ sở dữ liệu là nguồn sự thật, và mỗi lần nối lại
kèm một lượt đồng bộ.

**4. Sổ kết nối nằm trong bộ nhớ của một tiến trình.** Trang này chạy **một
instance**, nên đây là giới hạn đã biết chứ không phải lỗi đang có. Ngày chạy
nhiều instance, tin nhắn *vẫn được ghi và vẫn tới nơi* — chỉ là không tức thời
khi hai bên nằm ở hai bản khác nhau. Hai chỗ phải đổi khi ấy: một lớp chuyển
tiếp Redis pub/sub cho `SupportSocketRegistry`, và sticky session cho cái vé (vé
phát ở bản A không đổi được ở bản B). Chi tiết ở
[SUPPORT_MESSAGING.md](SUPPORT_MESSAGING.md).

Theo dõi số kết nối đang mở ở `GET /api/admin/support/summary` — con số duy nhất
phát hiện được rò rỉ kết nối và bão nối lại. Nó nằm sau `hasRole('ADMIN')` chứ
không ở `/actuator`, vì các endpoint actuator khác đọc được biến môi trường, tức
là đọc được cả API key lẫn mật khẩu cơ sở dữ liệu.

---

## Khi WebSocket "chạy ở máy mình mà hỏng trên bản deploy"

Đã xảy ra một lần, và nguyên nhân **không** nằm ở WebSocket. Ghi lại đầy đủ vì
mọi dấu hiệu ban đầu đều chỉ sai hướng.

### Triệu chứng

Hộp thư hỗ trợ im lặng trên bản deploy. Trong DevTools không có dòng WS nào —
vì `POST /api/support/ws-ticket` trả **500 Internal Server Error**, nên trình
duyệt không bao giờ tới bước gọi `new WebSocket(...)`.

### Nguyên nhân thật

**Backend trên Render còn cũ hơn frontend trên Vercel.** Frontend đã có tính
năng Hỗ trợ; backend thì chưa, nên đường `/api/support/ws-ticket` **không tồn
tại ở đó**. Câu trả lời đúng phải là 404.

Nó ra ngoài thành 500 vì `GlobalExceptionHandler` có một bộ bắt
`Exception.class` và khi ấy chưa có gì hẹp hơn cho `NoResourceFoundException` —
thứ Spring ném khi không đường nào khớp. Một mã trạng thái sai không chỉ là một
con số sai: nó là tấm biển chỉ đường sai. "500" nói *máy chủ hỏng*, nên cuộc
điều tra đi tìm ở cấu hình WebSocket, danh sách CORS, proxy của Render, biến môi
trường và chuyện `ws`/`wss` — năm chỗ không có gì.

Cả hai đã được sửa: 404 giờ ra đúng là 404 (kèm một dòng WARN
`KHONG_CO_DUONG` trong log Render), và `render.yaml` ghi rõ `autoDeploy: true`.
Bài kiểm giữ chỗ: `ErrorStatusTest`.

### Cách kiểm tra trong ba mươi giây

`/v3/api-docs` để `permitAll`, nên nó trả lời được câu "bản đang chạy có những
đường nào" mà không cần đăng nhập:

```bash
# Backend co tinh nang Ho tro khong?
curl -s https://<app>.onrender.com/v3/api-docs | grep -c '/api/support'
#   0  → backend cu hon frontend. Vao Render → Manual Deploy → Deploy latest commit.
#   >0 → backend da co; van hong thi doc bang duoi.

# Duong khong ton tai phai tra 404, khong duoc tra 500.
curl -s -o /dev/null -w '%{http_code}\n' \
  https://<app>.onrender.com/swagger-ui/khong-co-that.html
```

### Bảng tra: `/api/support/ws-ticket` trả về gì

| Mã | Nghĩa | Việc phải làm |
|---|---|---|
| **404** | Bản backend này chưa có tính năng Hỗ trợ | Triển khai lại backend |
| **401** | Token hết hạn, hoặc tài khoản bị khóa | Đăng nhập lại |
| **503** | Hết chỗ giữ vé (`OneTimeTicketStore` đầy) | Tự khỏi; vé sống 90 giây |
| **5xx** khác | Máy chủ đang thức dậy sau 15 phút ngủ | Đợi ~1 phút |
| **200** nhưng WS vẫn hỏng | Vé phát được, bắt tay bị chặn | Xem hai dòng dưới |

Bắt tay hỏng thì log của Render nói thẳng lý do — cả hai dòng đều ở mức INFO,
không phải DEBUG:

```text
WEBSOCKET_ORIGIN_REJECTED ho-tro: nguồn … không nằm trong danh sách cho phép …
        → thêm tên miền vào CORS_ALLOWED_ORIGINS
WEBSOCKET_AUTH_FAILED ho-tro: vé không hợp lệ hoặc đã dùng
        → vé hết hạn (90 giây), hoặc đang chạy nhiều instance mà không sticky
```

> Trình duyệt **không bao giờ** nói cho JavaScript biết vì sao một lần bắt tay
> WebSocket bị từ chối — `onclose` không mang mã HTTP. Nên với đường này, log
> của máy chủ không phải một cách chẩn đoán tiện tay; nó là cách **duy nhất**.

---

## Sao lưu

Xem [BACKUP.md](BACKUP.md).
