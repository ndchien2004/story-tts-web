# Truyện Nghe — Website đọc & nghe truyện có Text-to-Speech

Website đọc truyện chữ, nghe audio thu sẵn, và **chuyển chương truyện thành giọng nói bằng AI** khi
chương đó chưa có bản audio. Quản trị viên khóa từng chương ở ba mức (Công khai / Yêu cầu đăng nhập /
VIP) để quyết định nội dung nào được đọc và nghe tự do.

Backend Java (Spring Boot, REST API thuần) + frontend React (Vite), hai phần tách rời, nói chuyện với
nhau bằng JWT.

---

## Mục lục

- [Tính năng](#tính-năng)
- [Công nghệ](#công-nghệ)
- [Kiến trúc](#kiến-trúc)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Cài đặt và chạy](#cài-đặt-và-chạy)
- [Cấu hình API key](#cấu-hình-api-key)
- [Tài khoản có sẵn](#tài-khoản-có-sẵn)
- [Danh sách API](#danh-sách-api)
- [Kiểm thử](#kiểm-thử)
- [Phạm vi đề bài](#phạm-vi-đề-bài)

---

## Tính năng

### Người đọc

| Nhóm | Chức năng |
|---|---|
| Tài khoản | Đăng ký, đăng nhập, đăng xuất; mật khẩu băm BCrypt; phiên làm việc bằng JWT |
| Đăng nhập Google | Một nút, lần đầu thì tạo luôn tài khoản; đã có tài khoản cùng email thì ghép vào tài khoản đó |
| Quên mật khẩu | Nhận liên kết đặt lại qua email, dùng một lần và có hạn |
| Duyệt truyện | Danh sách có phân trang, lọc theo thể loại, sắp xếp (mới nhất / phổ biến / A-Z), tìm theo tên truyện hoặc tác giả |
| Đọc | Trang đọc riêng, chuyển chương trước/sau, chỉnh cỡ chữ và giãn dòng, giao diện sáng/tối |
| Khóa chương | Chương không đủ quyền hiện icon 🔒 kèm mức yêu cầu; nội dung **không bao giờ** được gửi về trình duyệt |
| Nghe | Trình phát HTML5 có tua (HTTP Range), nhớ vị trí đang nghe, chế độ nghe liên tục tự chuyển chương |
| Nghe bằng AI | Tạo audio từ nội dung chương qua API TTS, chọn giọng và tốc độ, chạy nền và tự cập nhật khi xong |
| Cá nhân | Tủ truyện (đang đọc dở / đã đọc xong / yêu thích), tiến độ đọc, hồ sơ + ảnh đại diện |
| Gợi ý | Truyện cùng thể loại với những truyện đã đọc, hiện ở trang chủ |
| Tương tác | Chấm sao, bình luận, xóa bình luận của chính mình |
| Nâng cấp VIP | Mua gói VIP theo tháng, **thanh toán thật qua PayOS** (chuyển khoản / QR); hạn được cộng dồn khi gia hạn sớm |

### Quản trị viên

| Màn hình | Chức năng |
|---|---|
| Tổng quan | Số liệu toàn hệ thống, **ba biểu đồ Chart.js**, nêu trước những việc cần làm (chương thiếu audio, bản audio hỏng) |
| Truyện & chương | CRUD truyện và chương, đặt mức khóa từng chương, **đổi mức khóa hàng loạt**, upload audio thu sẵn |
| Thể loại & tác giả | CRUD, kèm số truyện của từng mục; chặn xóa mục còn truyện đang dùng |
| Audio & giọng đọc | Lọc chương theo tình trạng audio, **tạo audio AI hàng loạt** (tối đa 20 chương/lượt) |
| Bình luận | Kiểm duyệt toàn bộ bình luận của mọi truyện, tìm kiếm và xóa |
| Thành viên | Cấp/thu hồi VIP vĩnh viễn, khóa/mở tài khoản, nâng/hạ quyền quản trị |
| Gói VIP & thanh toán | Tự đặt gói bán ra (số tháng và giá tùy ý), bật/tắt bán; xem mọi đơn và đối chiếu lại đơn còn treo với cổng thanh toán |

---

## Công nghệ

| Thành phần | Lựa chọn |
|---|---|
| Backend | Java 21, Spring Boot 3.5.6 (Web, Data JPA, Security, Validation, AOP) |
| Xác thực | JWT (jjwt 0.12), BCrypt |
| Đăng nhập Google | Google Identity Services phía trình duyệt; ID token được kiểm chữ ký tại máy chủ bằng JWKS |
| Gửi email | Spring Boot Mail qua SMTP (mặc định Gmail) |
| Cơ sở dữ liệu | MySQL 8 (chạy bằng Docker Compose) |
| Frontend | React 19, Vite 8, React Router 7, Axios |
| Biểu đồ | Chart.js 4 + react-chartjs-2 (tải theo yêu cầu, chỉ ở trang quản trị) |
| TTS | FPT.AI Text-to-Speech (chính), ElevenLabs (dự phòng) |
| Thanh toán | PayOS (tạo link thanh toán + webhook, ký HMAC-SHA256) |
| Lưu ảnh đại diện, ảnh thương hiệu | Cloudinary |
| Lưu audio | Thư mục local `backend/uploads/audio` |
| Tài liệu API | springdoc-openapi (Swagger UI) |

Giao diện tự viết bằng CSS thuần, không dùng thư viện UI. Không có state manager ngoài React Context.

---

## Kiến trúc

### Phân lớp

```
React SPA (trình duyệt)
      │  Axios, kèm JWT trong header Authorization
      ▼
JwtAuthenticationFilter          ← xác thực, đặt người dùng vào SecurityContext
      ▼
Controller                       ← mỏng: nhận request, trả DTO, không chứa nghiệp vụ
      ▼
Service                          ← toàn bộ nghiệp vụ + kiểm tra quyền
      ▼
Repository (Spring Data JPA)
      ▼
MySQL
```

Nguyên tắc xuyên suốt: **Entity không rời khỏi tầng Service.** Ranh giới luôn là DTO. Ví dụ
`ChapterSummaryDto` cố ý không có trường `content`, nên danh sách chương hiển thị được cho tất cả mọi
người mà nội dung chương bị khóa vẫn không có đường ra khỏi máy chủ.

### Kiểm tra quyền — một lớp dùng chung

Có đúng **ba** đường chạm tới nội dung chương, và cả ba đều đi qua `AccessControlService`:

| Đường vào | Endpoint |
|---|---|
| Đọc chữ | `GET /api/chapters/{id}` |
| Nghe audio | `GET /api/chapters/{id}/audio/{audioId}` |
| Tạo audio AI | `POST /api/chapters/{id}/tts` |

| access_level | Khách | Thành viên | VIP | Admin |
|---|:---:|:---:|:---:|:---:|
| PUBLIC | ✔ | ✔ | ✔ | ✔ |
| MEMBER | ✘ | ✔ | ✔ | ✔ |
| VIP | ✘ | ✘ | ✔ | ✔ |

Không đủ quyền thì trả **403** kèm thông báo, React hiện màn hình yêu cầu đăng nhập hoặc nâng cấp VIP.
Đường TTS bắt buộc phải qua cùng cửa này, nếu không người dùng có thể dùng TTS để lách qua chương bị khóa.

Hàm `AccessControlService.canAccess(accessLevel, principal)` được viết dạng thuần, không phụ thuộc
`SecurityContext`, để có thể kiểm thử mà không cần dựng Spring.

### Luồng "Nghe bằng AI"

```
React: AudioPlayer → useChapterAudio → POST /api/chapters/{id}/tts
   │
   ▼
JwtAuthenticationFilter → AudioController → TtsService.requestForChapter
   │
   ├─ accessControlService.requireAccess(chapter)     ← 403 nếu không đủ quyền
   ├─ audioFileRepository.findTtsCache(chương, giọng, tốc độ)
   │     └─ đã có bản READY → trả về ngay, KHÔNG gọi API
   ├─ chưa có → ghi audio_files với status = PROCESSING
   └─ publishEvent(...) rồi return luôn  ────────────────┐
                                                          │ @Async
React poll GET /tts/{audioId}/status                      ▼
   cho tới khi READY                        TtsGenerationWorker
                                              ├─ TtsEngine chọn nhà cung cấp
                                              │    fptai → elevenlabs (bỏ qua cái chưa có key)
                                              ├─ cắt nội dung thành khúc ≤ 4500 ký tự, ghép lại
                                              ├─ StorageService lưu uploads/audio/<uuid>.mp3
                                              └─ cập nhật status = READY (hoặc FAILED kèm lý do)
   │
   ▼
<audio src="…/audio/{audioId}?access_token=…">
   AudioController đọc header Range → 206 Partial Content → tua được ngay
```

Ba điểm đáng chú ý:

1. **Cache khóa theo bộ ba (chương, giọng, tốc độ)** — đổi giọng là một bản audio khác, nên khóa cache
   phải gồm cả ba. Nghe lại cùng giọng thì không tốn thêm lần gọi API nào.
2. **Bất đồng bộ bằng event** — request trả về sau vài chục mili giây với trạng thái `PROCESSING`, việc
   gọi API TTS mất 10-60 giây chạy ở luồng nền. Làm đồng bộ thì chương dài sẽ timeout.
3. **Token qua query param cho audio** — thẻ `<audio>` của HTML không gửi được header, nên
   `JwtAuthenticationFilter` chấp nhận thêm tham số `access_token`. Không có đường này thì không stream
   được chương bị khóa.

### Luồng mua VIP

```
Người dùng chọn gói → POST /api/vip/orders
      │  máy chủ đọc giá và số tháng từ DB (client chỉ gửi planId)
      ▼
PayosClient tạo link thanh toán, ký HMAC-SHA256 → trả checkoutUrl
      ▼
Trình duyệt sang trang PayOS, người dùng chuyển khoản / quét QR
      ▼
┌─ PayOS gọi webhook  → đối chiếu chữ ký → cộng hạn VIP
└─ Trang kết quả tự hỏi lại cổng thanh toán (dự phòng khi webhook không tới được)
```

Ba điểm đáng chú ý:

1. **Client không quyết định số tiền** — chỉ gửi `planId`, còn giá và số tháng đọc từ cơ sở dữ liệu
   rồi chép vào đơn. Nhận số tiền từ client thì ai cũng tự đặt giá cho gói một năm.
2. **Chữ ký là thứ duy nhất bảo vệ webhook** — endpoint đó phải để công khai vì PayOS không mang JWT,
   nên chữ ký HMAC được đối chiếu trước khi bất cứ gì được ghi.
3. **Cộng hạn đúng một lần cho mỗi đơn** — webhook có thể được gửi lại nhiều lần và trang kết quả
   cũng có thể xác nhận cùng đơn đó, nên thao tác cộng hạn được viết để gọi lại không cộng thêm.

Chạy dưới `localhost` thì PayOS không gọi webhook vào được; đường dự phòng ở trang kết quả (hỏi lại
`GET /v2/payment-requests/{orderCode}`) là lý do luồng vẫn chạy đủ khi phát triển.

### Luồng đăng nhập bằng Google

```
Trình duyệt: nút của Google Identity Services
      │  Google trả về ID token (JWT do Google ký)
      ▼
POST /api/auth/google
      │
      ├─ GoogleIdTokenVerifier: lấy khóa công khai (JWKS) → kiểm chữ ký,
      │                         đối chiếu aud = Client ID, iss, hạn dùng, email_verified
      ├─ tìm tài khoản theo google_id → nếu chưa có thì tìm theo email
      └─ chưa có cả hai → tạo tài khoản mới ngay tại đây
      ▼
Trả JWT của hệ thống, giống hệt đăng nhập bằng mật khẩu
```

Ba điểm đáng chú ý:

1. **Chữ ký được kiểm tại máy chủ, không gọi lại Google mỗi lần.** Khóa công khai lấy từ JWKS và nhớ
   theo `kid`; gặp `kid` lạ (Google xoay khóa) thì tải lại đúng một lần.
2. **Không có bước nào tin trình duyệt.** Email, tên và ảnh đều đọc từ token đã kiểm chữ ký chứ không
   nhận từ body — nếu không, ai cũng tự khai mình là email bất kỳ.
3. **Ghép theo `sub` trước, email sau.** `sub` là định danh không đổi của tài khoản Google; bước tìm
   theo email là để người đã đăng ký bằng mật khẩu bấm nút Google vẫn về đúng tài khoản cũ.

Luồng này dùng **ID token**, không phải authorization code, nên máy chủ chỉ cần Client ID — không có
client secret nào phải giữ.

### Luồng quên mật khẩu

```
POST /api/auth/forgot-password  { email }
      │
      ├─ tìm tài khoản đang hoạt động → không thấy thì im lặng dừng
      ├─ vô hiệu mọi liên kết cũ của người này
      ├─ sinh token 32 byte ngẫu nhiên, lưu SHA-256 của nó vào password_reset_tokens
      └─ @Async gửi email chứa token gốc
      ▼
Người dùng mở /dat-lai-mat-khau?token=…
      ▼
POST /api/auth/reset-password  { token, password }
      └─ tra theo băm → còn hạn & chưa dùng → băm mật khẩu mới, đánh dấu token đã dùng
```

Ba điểm đáng chú ý:

1. **Trả lời như nhau dù email có tồn tại hay không.** Nói thẳng "email này chưa đăng ký" là biến
   trang quên mật khẩu thành công cụ dò danh sách người dùng.
2. **Cơ sở dữ liệu chỉ giữ băm của token.** Chuỗi gốc chỉ nằm trong email đã gửi, nên đọc được bảng
   cũng không dựng lại được liên kết để chiếm tài khoản.
3. **Gửi email chạy nền.** Ngoài chuyện không bắt người dùng chờ SMTP, đây còn là cách để thời gian
   trả lời không tố cáo địa chỉ nào có thật.

### Cơ sở dữ liệu

`users` · `genres` · `authors` · `stories` · `chapters` · `audio_files` · `reading_progress` ·
`favorites` · `ratings_comments` · `view_events` · `vip_plans` · `vip_orders` ·
`password_reset_tokens`

`view_events` ghi mỗi lượt mở chương để đọc hoặc nghe. Cần bảng riêng vì `view_count` chỉ là số cộng
dồn — biết tổng nhưng không tách được ra từng ngày, mà biểu đồ theo ngày lại hỏi đúng câu đó.

Quyền VIP đến từ hai nguồn tách bạch: `users.is_vip` là quyền Admin cấp tay, không hạn; `users.vip_until`
là hạn của gói đã mua. `User.isVip()` xét cả hai, nên phần còn lại của hệ thống không cần biết sự khác
biệt đó.

`users.google_id` giữ claim `sub` của tài khoản Google, null nghĩa là chưa liên kết. Tài khoản tạo bằng
Google vẫn có `password_hash` — cột đó NOT NULL — nhưng là một chuỗi ngẫu nhiên không ai đoán được;
muốn có mật khẩu thật thì đi đường "quên mật khẩu".

Schema do Hibernate tự tạo (`ddl-auto=update`), không cần chạy file SQL nào.

---

## Cấu trúc thư mục

```
story-tts-web/
├─ backend/
│  └─ src/main/java/com/storytts/backend/
│     ├─ config/         SecurityConfig, CorsProperties, TtsProperties, GoogleProperties…
│     ├─ controller/     REST endpoint (thư mục con admin/ cho khu quản trị)
│     ├─ domain/         Entity JPA
│     ├─ dto/            Đối tượng ở ranh giới API
│     ├─ exception/      Exception nghiệp vụ + handler chung
│     ├─ repository/     Spring Data JPA
│     ├─ security/       JwtAuthenticationFilter, AppUserPrincipal, JwtService,
│     │                  GoogleIdTokenVerifier (kiểm ID token bằng JWKS)
│     └─ service/        Nghiệp vụ; service/tts/ cho Text-to-Speech,
│                        service/payment/ cho PayOS (ký HMAC + gọi REST),
│                        MailService + PasswordResetService cho quên mật khẩu
├─ frontend/
│  └─ src/
│     ├─ api/            client Axios + khai báo endpoint
│     ├─ components/     Component dùng lại
│     ├─ context/        Auth, Theme
│     ├─ hooks/          useChapterAudio, useDebouncedValue, useAuthProviders
│     ├─ pages/          Trang theo route (thư mục con admin/)
│     ├─ styles/         CSS thuần, chia theo nhóm
│     ├─ utils/          Định dạng tiền/ngày, chuẩn hóa Unicode
│     └─ brand.js        URL logo và ảnh banner trên Cloudinary
├─ docker-compose.yml    MySQL 8
├─ .env                  Cấu hình thật — ĐÃ loại khỏi Git
└─ .env.example          Bản mẫu để sao chép
```

---

## Cài đặt và chạy

### Yêu cầu

- JDK 21 trở lên
- Node.js 20 trở lên
- Docker (để chạy MySQL) — hoặc một MySQL 8 tự cài sẵn

### 1. Chuẩn bị cấu hình

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env
```

File `.env` ở thư mục gốc chứa toàn bộ mật khẩu và API key. File này **đã nằm trong `.gitignore`**,
không bao giờ được commit.

### 2. Khởi động cơ sở dữ liệu

```bash
docker compose up -d
```

MySQL chạy ở cổng 3306, database `story_tts` được tạo tự động.

### 3. Chạy backend

```bash
cd backend
./mvnw spring-boot:run          # Windows: mvnw.cmd spring-boot:run
```

Backend chạy ở `http://localhost:8080`, Swagger UI ở `http://localhost:8080/swagger-ui.html`.

> **Phải chạy từ trong thư mục `backend/`.** File `.env` được nạp bằng đường dẫn tương đối `../.env`,
> chạy từ chỗ khác là không thấy cấu hình.

Lần chạy đầu tiên, `DataSeeder` tự tạo tài khoản Admin, vài tài khoản demo, 6 truyện mẫu (mỗi truyện 4
chương đủ cả ba mức khóa) và bình luận mẫu. Muốn tắt: đặt `SEED_ENABLED=false` trong `.env`.

### 4. Chạy frontend

```bash
cd frontend
npm install
npm run dev
```

Giao diện ở `http://localhost:5173`.

### Lệnh hay dùng

| Lệnh | Tác dụng |
|---|---|
| `./mvnw spring-boot:run` | Chạy backend |
| `./mvnw test` | Chạy test backend |
| `./mvnw clean package` | Đóng gói file JAR |
| `npm run dev` | Frontend chế độ phát triển |
| `npm run build` | Build frontend cho production |
| `npm run lint` | Kiểm tra frontend bằng oxlint |

---

## Cấu hình API key

Tất cả đều nằm trong `.env` ở thư mục gốc. Không có key nào được viết cứng trong mã nguồn.

### Text-to-Speech (bắt buộc nếu muốn dùng chức năng đọc bằng AI)

```properties
# Thứ tự thử. Nhà cung cấp nào chưa có key thì tự động bị bỏ qua.
TTS_PROVIDERS=fptai,elevenlabs

# FPT.AI — giọng Việt. Lấy key tại https://fpt.ai/tts (Dashboard → API Key)
FPT_TTS_API_KEY=
FPT_TTS_VOICE=banmai
FPT_TTS_SPEED=0

# ElevenLabs — dự phòng. https://elevenlabs.io → Profile → API Keys
ELEVENLABS_API_KEY=
ELEVENLABS_VOICE_ID=
# Bắt buộc dùng model đa ngôn ngữ thì tiếng Việt mới đọc đúng
ELEVENLABS_MODEL_ID=eleven_multilingual_v2
```

Không điền key nào thì phần còn lại của web vẫn chạy bình thường, chỉ nút "Nghe bằng AI" báo lỗi rõ
ràng là máy chủ chưa cấu hình.

### Cloudinary (ảnh đại diện người dùng, ảnh thương hiệu)

```properties
CLOUDINARY_CLOUD_NAME=
CLOUDINARY_API_KEY=
CLOUDINARY_API_SECRET=
CLOUDINARY_FOLDER=story-tts-web
```

Để trống thì chức năng đổi ảnh đại diện **tự ẩn** khỏi giao diện, không hiện nút rồi mới báo lỗi.

Logo và ảnh banner cũng nằm trên Cloudinary, khai báo trong `frontend/src/brand.js` — kể cả khi ba
biến trên để trống thì các ảnh đó vẫn hiển thị, vì chúng là URL công khai chứ không phải upload lúc
chạy.

### Đăng nhập bằng Google

```properties
GOOGLE_CLIENT_ID=
```

Lấy tại **https://console.cloud.google.com** → APIs & Services → Credentials → Create credentials →
OAuth client ID, loại **Web application**. Trong phần **Authorized JavaScript origins** khai đúng
origin của frontend, khi phát triển là `http://localhost:5173`; thiếu bước này thì nút của Google
không hiện ra.

Chỉ cần Client ID. Luồng ở đây dùng **ID token** chứ không đổi authorization code, nên client secret
không được dùng tới và không cần lưu ở đâu cả.

Để trống thì nút "Đăng nhập bằng Google" **tự ẩn** khỏi cả trang đăng nhập lẫn trang đăng ký.

### Email (chức năng quên mật khẩu)

```properties
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=dia-chi-gmail-cua-ban@gmail.com
MAIL_PASSWORD=mat-khau-ung-dung-16-ky-tu
MAIL_FROM=
MAIL_FROM_NAME=Truyen Nghe
MAIL_RESET_URL=http://localhost:5173/dat-lai-mat-khau
MAIL_RESET_TOKEN_TTL_MINUTES=30
```

Với Gmail phải dùng **mật khẩu ứng dụng** chứ không phải mật khẩu đăng nhập: bật xác minh 2 bước cho
tài khoản rồi tạo tại **https://myaccount.google.com/apppasswords**. Chuỗi 16 ký tự nhận được điền vào
`MAIL_PASSWORD` (bỏ khoảng trắng cũng được).

`MAIL_FROM` để trống thì lấy luôn `MAIL_USERNAME` làm địa chỉ người gửi. `MAIL_RESET_URL` phải trỏ đúng
route `/dat-lai-mat-khau` của frontend — đó là nơi liên kết trong email dẫn tới.

Để trống `MAIL_USERNAME` thì liên kết "Quên mật khẩu?" **tự ẩn** khỏi trang đăng nhập, và trang
`/quen-mat-khau` nói rõ là máy chủ chưa cấu hình email thay vì để người dùng gửi rồi chờ vô ích.

### PayOS (thanh toán nâng cấp VIP)

```properties
PAYOS_CLIENT_ID=
PAYOS_API_KEY=
PAYOS_CHECKSUM_KEY=
PAYOS_RETURN_URL=http://localhost:5173/thanh-toan/ket-qua
PAYOS_CANCEL_URL=http://localhost:5173/thanh-toan/ket-qua
```

Lấy tại **https://my.payos.vn** → Kênh thanh toán → Thông tin xác thực API.

Để trống thì trang nâng cấp vẫn hiện bảng giá nhưng báo rõ là chưa mua được, phần còn lại của ứng
dụng chạy bình thường.

Khi triển khai thật, khai báo thêm webhook trong dashboard PayOS:

```
https://<tên-miền-của-bạn>/api/payments/payos/webhook
```

Chạy dưới `localhost` thì bỏ qua bước này — PayOS không gọi vào máy cá nhân được, và trang kết quả đã
tự hỏi lại cổng thanh toán nên luồng mua vẫn chạy đủ.

### Các cấu hình khác

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `JWT_SECRET` | chuỗi mẫu | **Bắt buộc đổi** khi chạy thật, tối thiểu 32 ký tự |
| `JWT_EXPIRATION_MS` | 86400000 | Hạn dùng token (24 giờ) |
| `APP_ADMIN_USERNAME` / `_PASSWORD` | admin / Admin@123 | Tài khoản Admin tạo ở lần chạy đầu |
| `CORS_ALLOWED_ORIGINS` | localhost:5173… | Origin được phép gọi API |
| `AUDIO_DIR` | ./uploads/audio | Nơi lưu file audio |
| `SERVER_PORT` | 8080 | Cổng backend |

---

## Tài khoản có sẵn

`DataSeeder` tạo sẵn ở lần chạy đầu tiên, để kiểm thử khóa chương bằng ba mức quyền khác nhau:

| Tài khoản | Mật khẩu | Quyền | Đọc được chương |
|---|---|---|---|
| `admin` | `Admin@123` | Quản trị viên | Tất cả |
| `vipuser` | `Vip@123` | Thành viên VIP | PUBLIC + MEMBER + VIP |
| `member` | `Member@123` | Thành viên thường | PUBLIC + MEMBER |
| *(không đăng nhập)* | — | Khách | Chỉ PUBLIC |

Thêm 50 tài khoản độc giả mẫu `docgia01` … `docgia50` (mật khẩu chung `Doc@123`), dùng làm người viết
cho bình luận mẫu và để trang quản lý thành viên có đủ dữ liệu nhiều trang.

> Mật khẩu Admin lấy từ `APP_ADMIN_PASSWORD` trong `.env`. Đổi ngay khi triển khai thật.

---

## Danh sách API

Tài liệu đầy đủ có tương tác: **`http://localhost:8080/swagger-ui.html`**

### Công khai

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/auth/providers` | Máy chủ đang bật cách đăng nhập nào (Google, quên mật khẩu) |
| POST | `/api/auth/register` | Đăng ký |
| POST | `/api/auth/login` | Đăng nhập, trả JWT |
| POST | `/api/auth/google` | Đăng nhập bằng ID token của Google; lần đầu thì tạo luôn tài khoản |
| POST | `/api/auth/forgot-password` | Gửi liên kết đặt lại mật khẩu — **trả lời như nhau dù email có tồn tại hay không** |
| POST | `/api/auth/reset-password` | Đặt mật khẩu mới bằng token trong email |
| GET | `/api/stories` | Danh sách truyện (keyword, genreId, status, sort, page, size) |
| GET | `/api/stories/{id}` | Chi tiết truyện + danh sách chương |
| GET | `/api/chapters/{id}` | Nội dung chương — **403 nếu không đủ quyền** |
| GET | `/api/chapters/{id}/audio` | Các bản audio của chương |
| GET | `/api/chapters/{id}/audio/{audioId}` | Stream audio, hỗ trợ Range |
| POST | `/api/chapters/{id}/tts` | Tạo audio bằng AI |
| GET | `/api/chapters/{id}/tts/{audioId}/status` | Trạng thái tạo audio |
| GET | `/api/genres`, `/api/authors` | Danh mục |
| GET | `/api/stories/{id}/comments` | Bình luận của một truyện |
| GET | `/api/vip/plans` | Bảng giá các gói VIP đang bán |
| POST | `/api/payments/payos/webhook` | PayOS gọi về — bảo vệ bằng chữ ký HMAC, không phải JWT |

### Cần đăng nhập

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/auth/me` | Thông tin tài khoản hiện tại |
| POST/DELETE | `/api/me/avatar` | Đổi / gỡ ảnh đại diện |
| GET | `/api/me/shelf` | Tủ truyện: mỗi truyện một dòng kèm số chương đã đọc |
| GET | `/api/me/recommendations` | Gợi ý truyện cùng thể loại với truyện đã đọc |
| GET | `/api/me/favorites`, `/api/me/reading` | Yêu thích, lịch sử đọc |
| POST | `/api/stories/{id}/favorite` | Bật/tắt yêu thích |
| PUT | `/api/chapters/{id}/progress` | Ghi vị trí đang đọc/nghe |
| POST | `/api/stories/{id}/comments` | Gửi đánh giá và bình luận |
| DELETE | `/api/comments/{id}` | Xóa bình luận của mình (Admin xóa được của mọi người) |
| GET | `/api/vip/me` | Tình trạng VIP của tài khoản hiện tại |
| POST | `/api/vip/orders` | Tạo đơn, trả về link thanh toán PayOS |
| GET | `/api/vip/orders/{orderCode}` | Tra cứu đơn; đơn còn treo sẽ được hỏi lại cổng thanh toán |
| POST | `/api/vip/orders/{orderCode}/cancel` | Hủy đơn chưa thanh toán |

### Chỉ Admin (`/api/admin/**`)

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/admin/stats` | Số liệu tổng quan |
| GET | `/api/admin/stats/charts` | Dữ liệu ba biểu đồ (mặc định 14 ngày) |
| POST/PUT/DELETE | `/api/admin/stories`, `/api/admin/chapters` | CRUD truyện, chương |
| PATCH | `/api/admin/chapters/{id}/access-level` | Đổi mức khóa một chương |
| PATCH | `/api/admin/chapters/access-level` | Đổi mức khóa **nhiều chương** |
| POST | `/api/admin/chapters/{id}/audio` | Upload audio thu sẵn |
| GET | `/api/admin/audio/chapters` | Chương kèm tình trạng audio |
| POST | `/api/admin/audio/batch-tts` | Tạo audio AI hàng loạt |
| GET | `/api/admin/comments` | Toàn bộ bình luận để kiểm duyệt |
| POST/PUT/DELETE | `/api/admin/genres`, `/api/admin/authors` | CRUD danh mục |
| GET | `/api/admin/users` | Danh sách thành viên |
| PATCH | `/api/admin/users/{id}/vip` · `/enabled` · `/role` | Cấp VIP vĩnh viễn, khóa tài khoản, đổi quyền |
| GET/POST/PUT | `/api/admin/vip/plans` | Xem và cấu hình các gói VIP |
| PATCH | `/api/admin/vip/plans/{id}/active` | Bật/tắt bán một gói |
| GET | `/api/admin/vip/orders` | Mọi đơn nâng cấp (lọc theo trạng thái) |
| POST | `/api/admin/vip/orders/{orderCode}/refresh` | Đối chiếu một đơn còn treo với PayOS |

---

## Kiểm thử

```bash
cd backend
./mvnw test
```

Test dùng H2 in-memory nên **không cần MySQL đang chạy**, và cũng không gọi API TTS thật (mọi phụ thuộc
bên ngoài đều được thay bằng mock).

| Lớp test | Số bài | Kiểm điều gì |
|---|---|---|
| `AccessControlServiceTest` | 19 | Toàn bộ bảng phân quyền 3 mức khóa × 4 nhóm người dùng, chương không đặt mức khóa, và `requireAccess` phải ném 403 **không kèm nội dung chương** |
| `TtsServiceTest` | 11 | Chặn TTS với chương bị khóa (kể cả khi đã có sẵn cache), dùng lại bản READY, dọn bản FAILED rồi tạo lại, xếp hàng bất đồng bộ, kẹp tốc độ, giọng lạ quay về mặc định |
| `PasswordResetServiceTest` | 12 | Email lạ và tài khoản bị khóa không nhận được liên kết, chỉ băm của token được lưu, liên kết cũ bị vô hiệu, và một liên kết chỉ dùng được một lần |
| `AuthServiceGoogleTest` | 10 | Tạo tài khoản ở lần đăng nhập Google đầu tiên, ghép vào tài khoản cùng email, ưu tiên `google_id`, không ghi đè ảnh đại diện cũ, chặn tài khoản bị khóa |
| `BackendApplicationTests` | 1 | Toàn bộ context Spring khởi động được |

Hai lớp đầu chính là hai luồng mà mục 6 của đề bài nêu đích danh. Đáng chú ý nhất là bài
`kiemQuyenTruocKhiTraCache`: nó khẳng định cache **không** trở thành cửa sau — chương đã bị khóa thì
ngay cả bản audio tạo sẵn từ trước cũng không được trả về.

Hai lớp sau kiểm phần xác thực mới. Đáng chú ý là `chiLuuBamCuaToken`: nó đối chiếu chuỗi đi trong
email với chuỗi nằm trong cơ sở dữ liệu và khẳng định hai thứ đó khác nhau — cái sau là SHA-256 của
cái trước. Không có bài này thì việc lưu nhầm token gốc sẽ trôi qua mà không ai biết.

---

## Phạm vi đề bài

Toàn bộ mục **[BB]** (bắt buộc) và toàn bộ mục **[NC]** (nâng cao) đã hoàn thành, gồm cả hai mục 4.1
từng bỏ ngỏ: **đăng nhập bằng Google** và **quên mật khẩu qua email**.

Ngoài phạm vi đề bài, mục 11 (hướng mở rộng) đã làm thêm **thanh toán thật để mua VIP** qua PayOS —
đề bài chỉ yêu cầu Admin cấp VIP thủ công, và chức năng đó vẫn giữ nguyên bên cạnh.

Về mục 4.7: biểu đồ "lượt đọc/nghe theo ngày" chỉ có dữ liệu **kể từ khi bảng `view_events` được thêm
vào**, vì trước đó hệ thống không lưu mốc thời gian của từng lượt xem — chỉ có số cộng dồn. Những ngày
trước thời điểm đó hiển thị 0, không phải lỗi mà là do không có dữ liệu để dựng lại.
