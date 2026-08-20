# Truyện Nghe — Website đọc truyện & nghe audio

Website đọc truyện chữ và nghe audio. Mỗi chương có thể có bản thu do quản trị viên tải lên, hoặc bản
giọng đọc tiếng Việt do máy chủ dựng từ nội dung chương. Chương chưa có bản nào thì **người đọc tự bấm
"Nghe bằng AI"** — bản dựng ra là của riêng họ và sống trong phiên đăng nhập ấy, còn kho audio dùng
chung của cả trang là phần do quản trị viên dựng sẵn hoặc tải lên. Vì mỗi bản dựng mới là một lần gọi
API tính tiền, đường của người đọc đi kèm **hạn mức theo ngày ghi trong một sổ riêng** — xem
[Nghe bằng AI](#luồng-tạo-audio--hai-cửa-vào-một-cơ-chế). Quản trị viên khóa từng chương ở ba mức
(Công khai / Yêu cầu đăng nhập / VIP), đặt giá lẻ bằng Xu, và giữ chương ở dạng nháp hay hẹn giờ đăng.

Backend Java (Spring Boot, REST API thuần) + frontend React (Vite), hai phần tách rời, nói chuyện với
nhau bằng JWT.

---

## Mục lục

- [Tính năng](#tính-năng)
- [Công nghệ](#công-nghệ)
- [Kiến trúc](#kiến-trúc)
- [Bốn vấn đề lớn và cách giải](#bốn-vấn-đề-lớn-và-cách-giải)
- [Cấu trúc thư mục](#cấu-trúc-thư-mục)
- [Cài đặt và chạy](#cài-đặt-và-chạy)
- [Cấu hình API key](#cấu-hình-api-key)
- [Triển khai và lưu trữ](#triển-khai-và-lưu-trữ)
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
| Xác thực email | Đăng ký phải nhập mã OTP 6 chữ số gửi về hòm thư; **tài khoản chỉ được tạo sau khi mã đúng** |
| Đăng nhập Google | Một nút, lần đầu thì tạo luôn tài khoản; đã có tài khoản cùng email thì ghép vào tài khoản đó |
| Quên mật khẩu | Nhận liên kết đặt lại qua email, dùng một lần và có hạn |
| Duyệt truyện | Danh sách có phân trang, lọc theo thể loại, sắp xếp (mới nhất / phổ biến / A-Z), tìm theo tên truyện hoặc tác giả |
| Đọc | Trang đọc riêng, chuyển chương trước/sau, chỉnh cỡ chữ và giãn dòng, giao diện sáng/tối |
| Khóa chương | Chương không đủ quyền hiện icon 🔒 kèm mức yêu cầu; nội dung **không bao giờ** được gửi về trình duyệt |
| Nghe | Trình phát có tua (HTTP Range), nhớ vị trí đang nghe, chế độ nghe liên tục tự chuyển chương |
| Nhạc nền | Chọn một bản trong **kho nhạc quản trị viên tải lên** — hoặc mở bản của chính mình từ máy — chạy song song với giọng đọc, chỉnh âm lượng riêng, lặp lại, và **tự nhỏ lại khi có tiếng người**; hai đường tiếng trộn qua một `AudioContext` chung nên không lệch nhau trên điện thoại |
| Bám chữ theo giọng đọc | Chữ sáng lên đúng lúc được đọc tới, dòng đang đọc tự giữ ở giữa màn hình, bấm vào một chữ là nghe lại từ chỗ đó; mốc thời gian lấy theo từng chữ ngay trong lần tổng hợp giọng nói |
| Chương chưa có audio | Nút **"Nghe bằng AI"** ngay trên trang đọc: dựng audio rồi tự phát, có hạn mức mỗi ngày để một lần bấm không thành một khoản chi không kiểm soát; nghe lại bản đã có thì không tính lượt, và lần dựng hỏng thì được hoàn lượt |
| Trợ lý AI | Hộp chat nhỏ ở góc trang đọc: tóm tắt chương đang mở, trả lời câu hỏi về nó. Nội dung chương do máy chủ tự tra sau khi xét quyền, nên trợ lý không mở được cửa sau vào chương trả phí. Cũng có hạn mức mỗi ngày |
| Chương được sửa | Sửa nội dung là chương lên một phiên bản mới; bản audio đọc chữ cũ thành lỗi thời và không còn được phát như bản hiện tại. Trang đọc nhận thông báo ngay (SSE) kèm nút "Đọc nội dung mới", chứ không bị cắt ngang giữa lúc đang nghe |
| Mua lẻ chương | Ví Xu: nạp qua PayOS, mở khóa từng chương theo giá quản trị viên đặt. VIP đọc mọi chương miễn phí; mỗi giao dịch để lại một dòng sổ cái kèm số dư trước/sau |
| Cá nhân | Tủ truyện (đang đọc dở / đã đọc xong / yêu thích), tiến độ đọc, hồ sơ + ảnh đại diện |
| Gợi ý | Truyện cùng thể loại với những truyện đã đọc, hiện ở trang chủ |
| Tương tác | Chấm sao, bình luận, xóa bình luận của chính mình |
| Nâng cấp VIP | Mua gói VIP theo tháng, **thanh toán thật qua PayOS** (chuyển khoản / QR); hạn được cộng dồn khi gia hạn sớm |
| Gift code | Nhập mã để nhận Xu, ngay cạnh số dư ở trang tài khoản và dưới bảng giá ở trang nạp Xu. Mã không phân biệt hoa thường; **mỗi tài khoản đổi một mã đúng một lần**, và số Xu luôn lấy từ mã trong cơ sở dữ liệu chứ không từ thứ trình duyệt gửi lên |

### Quản trị viên

| Màn hình | Chức năng |
|---|---|
| Tổng quan | Số liệu toàn hệ thống, **ba biểu đồ Chart.js**, nêu trước những việc cần làm (chương thiếu audio, bản audio hỏng) |
| Truyện, chương & audio | Vào tab thấy danh sách truyện (sửa thông tin, xóa); bấm vào một truyện thì mở danh sách chương của nó — có phân trang cho truyện dài — nơi sửa nội dung, đặt mức khóa, đặt giá Xu, **đổi mức khóa và giá hàng loạt**, lọc theo tình trạng audio, upload bản thu và **tạo audio hàng loạt** (tối đa 20 chương/lượt) |
| Nháp & hẹn giờ đăng | Mỗi truyện và mỗi chương có ba trạng thái: **Nháp** (chỉ quản trị viên thấy), **Hẹn giờ** (tới giờ tự hiện, không cần ai bấm gì), **Đã đăng**. Gỡ một truyện xuống thì mọi chương của nó cũng biến mất khỏi trang người đọc |
| Audio của một chương | Trong màn hình sửa chương: xem mọi bản audio kể cả bản hỏng, tải lên bản mới, chọn giọng, tạo lại, xóa từng bản |
| Thể loại & tác giả | CRUD, kèm số truyện của từng mục; chặn xóa mục còn truyện đang dùng |
| Nhạc nền | Tải nhạc nền lên cho người nghe chọn, đặt tên và dòng ghi công; **tạm ẩn** một bản khỏi danh sách mà vẫn giữ file, hoặc xóa hẳn |
| Bình luận | Kiểm duyệt toàn bộ bình luận của mọi truyện, tìm kiếm và xóa |
| Thành viên | Cấp/thu hồi VIP vĩnh viễn, khóa/mở tài khoản, nâng/hạ quyền quản trị |
| Gói VIP & thanh toán | Tự đặt gói bán ra (số tháng và giá tùy ý), bật/tắt bán; xem mọi đơn và đối chiếu lại đơn còn treo với cổng thanh toán |
| Gói Xu & ví | Đặt bảng giá gói nạp Xu, cộng/trừ Xu tay cho một tài khoản, xem sổ cái từng giao dịch |
| Gift code | Tab thứ hai của mục Gói nạp Xu. Tạo mã (gõ tay hoặc **sinh ngẫu nhiên**) với số Xu, giờ bắt đầu, giờ hết hạn và số lượt tối đa — bỏ trống ba thứ sau nghĩa là hiệu lực ngay / không hết hạn / không giới hạn. Bảng có tìm kiếm, lọc theo tình trạng và theo ngày tạo, sắp theo cột, phân trang; mở một mã ra xem danh sách tài khoản đã đổi và tổng Xu đã phát. Mã đã có người đổi thì **tắt được nhưng không xóa được** — lịch sử Xu của họ trỏ về nó |

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
| Giọng đọc | ElevenLabs Text-to-Speech, model đa ngôn ngữ |
| Trợ lý đọc truyện | Google Gemini (REST, không SDK) |
| Báo chương vừa đổi | Server-Sent Events (`SseEmitter` của Spring MVC) |
| Thanh toán | PayOS (tạo link thanh toán + webhook, ký HMAC-SHA256) |
| Lưu ảnh đại diện, ảnh thương hiệu | Cloudinary |
| Lưu audio, nhạc nền | Thư mục local `backend/uploads/audio` và `backend/uploads/bgm` |
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

Mọi đường chạm tới nội dung chương đều đi qua `ChapterAccessService.requireAccess`, lớp bọc ngoài
`AccessControlService` để thêm phần "đã mua bằng Xu chưa":

| Đường vào | Endpoint |
|---|---|
| Đọc chữ | `GET /api/chapters/{id}` |
| Nghe audio | `GET /api/chapters/{id}/audio/{audioId}` |
| Mốc thời gian từng chữ | `GET /api/chapters/{id}/audio/{audioId}/transcript` |
| Người đọc tạo audio | `POST /api/chapters/{id}/tts` |
| Hỏi trợ lý AI | `POST /api/ai/story-assistant` |
| Admin tạo audio hàng loạt | `POST /api/admin/audio/batch-tts` |

Cùng cửa ấy còn trả lời một câu đứng trước câu hỏi quyền: **chương này đã đăng chưa.** Bản nháp và
chương đang chờ tới giờ trả về **404** với người đọc thường — không phải 403, vì 403 là một lời xác
nhận rằng chương ấy có tồn tại, mà với một bản nháp thì chính sự tồn tại của nó mới là thứ chưa được
công bố. Xem `PublicationService`.

| access_level | Khách | Thành viên | VIP | Admin |
|---|:---:|:---:|:---:|:---:|
| PUBLIC | ✔ | ✔ | ✔ | ✔ |
| MEMBER | ✘ | ✔ | ✔ | ✔ |
| VIP | ✘ | ✘ | ✔ | ✔ |

Không đủ quyền thì trả **403** kèm thông báo, React hiện màn hình yêu cầu đăng nhập hoặc nâng cấp VIP.
Hai đường tạo audio bắt buộc phải qua cùng cửa này, nếu không người dùng có thể dùng nó để lách qua
chương bị khóa: `requireAccess` chạy **trước** cả bước tra cache, nên một chương bị khóa không lộ ra
cả việc nó đã có bản audio hay chưa.

Hàm `AccessControlService.canAccess(accessLevel, principal)` được viết dạng thuần, không phụ thuộc
`SecurityContext`, để có thể kiểm thử mà không cần dựng Spring.

### Luồng tạo audio — hai cửa vào, một cơ chế

`TtsService` chỉ biết dựng audio và dùng lại bản cũ; nó không biết ai được phép tiêu tiền. Phần đó
nằm ở hai lớp riêng: khu quản trị dựng hàng loạt và không bị hạn mức, còn người đọc đi qua
`ReaderTtsService` với một ngân sách.

```
Admin: chọn chương trong "Truyện, chương & audio" → POST /api/admin/audio/batch-tts
   │
   ▼
hasRole('ADMIN') → AdminConsoleController → AudioAdminService.generateBatch
   │  từng chương một, chương này hỏng không kéo chương kia chết theo
   ▼
TtsService.requestForChapter
   ├─ chapterAccessService.requireAccess(chapter)   ← đã đăng chưa, rồi mới tới quyền
   ├─ audioFileRepository.findTtsCache(chương, phiên bản, giọng, tốc độ)
   │     └─ đã có bản READY → trả về ngay, KHÔNG gọi API
   ├─ chưa có → hỏi ngân sách (chỉ đường người đọc) → ghi ai_usage
   ├─ ghi audio_files với status = PROCESSING
   └─ publishEvent(...) rồi return luôn  ────────────────┐
                                                          │ @Async
                                                          ▼
                                            TtsGenerationWorker
                                              ├─ TtsEngine chọn nhà cung cấp
                                              │    elevenlabs (bỏ qua cái chưa có key)
                                              ├─ cắt nội dung thành khúc ≤ 4500 ký tự, ghép lại
                                              ├─ StorageService lưu uploads/audio/<uuid>.mp3
                                              └─ cập nhật status = READY (hoặc FAILED kèm lý do)
```

Phía người đọc (mục 4.5 của đề bài — nút "Nghe bằng AI"):

```
React: AudioPlayer → GET /api/chapters/{id}/audio
   │     có bản READY → phát luôn
   │     rỗng → hiện nút "Nghe bằng AI" kèm "hôm nay bạn còn N lượt"
   ▼
POST /api/chapters/{id}/tts  (phải đăng nhập — xem bên dưới)
   ▼
ReaderTtsService → TtsService.requestForChapter(…, budget)
   │     READY  → phát ngay, KHÔNG tốn lượt nào
   │     PROCESSING → "Đang tạo audio…", chờ bằng GET /api/chapters/{id}/tts/{audioId}
   ▼
<audio src="…/audio/{audioId}?access_token=…">
   AudioController đọc header Range → 206 Partial Content → tua được ngay
```

Sáu điểm đáng chú ý:

1. **Bản của người đọc là của riêng họ, và sống trong một phiên.** Đây là điểm dễ hiểu nhầm nhất của
   cả tính năng, nên nói thẳng: bản do người đọc bấm dựng **không** vào kho chung. `requireOwnership`
   chỉ cho chính người ấy nghe, khóa cache gồm cả người yêu cầu, và `ReaderNarrationCleanup` dọn nó ở
   cả hai đầu phiên đăng nhập. Nó là thứ dựng tạm cho một buổi đọc, không phải kho của trang web —
   đĩa không phình lên theo số lần bấm nút, và bảng quản trị không mang những dòng mà admin không đặt
   ở đó.

   Cái giá của lựa chọn ấy, nói rõ luôn: **chi phí không phải là hằng số theo chương**. Mười người
   cùng bấm một chương là mười lần gọi ElevenLabs. Thứ giữ cho con số ấy không chạy là hạn mức ở điểm
   2, chứ không phải việc dùng chung file. Muốn chi phí thành hằng số theo chương thì đường đúng là
   **quản trị viên dựng sẵn** — bản của khu quản trị nằm trong kho chung, ai cũng nghe được, và
   `TtsService` trả nó về ngay cả khi người đọc bấm nút, không tốn lượt nào.

2. **Hạn mức nằm ở một sổ riêng, không đếm trên chính thứ nó bảo vệ.** Bảng `ai_usage` chỉ có đường
   ghi thêm: một dòng cho một lượt, không nghiệp vụ nào xóa. Trước đây hạn mức là một phép đếm ngay
   trên `audio_files` — mà những hàng ấy bị dọn mỗi lần mở phiên mới, nên **đăng xuất rồi đăng nhập
   lại là nạp đầy hạn mức**, lặp bao nhiêu lần cũng được. Hai vòng đời khác hẳn nhau (bản audio là
   *tài sản*, dọn được; một lượt đã dùng là *sự kiện*, không xóa được) đã bị buộc vào một hàng, và
   `AiUsageJpaTest.dongSoSongSotQuaViecDonBanAudio` ghim việc tách chúng ra.

   Cùng bảng ấy phục vụ luôn trợ lý AI, thứ trước đây đếm bằng một bảng băm trong bộ nhớ — mất sạch
   sau mỗi lần tiến trình khởi động lại, mà Render gói miễn phí thì ngủ sau 15 phút vắng khách.

3. **Chỗ hỏi ngân sách nằm ở đúng một dòng**, ngay trước lệnh ghi bản ghi mới trong `TtsService`. Nhờ
   vậy "nghe lại không tốn lượt" là hệ quả của cấu trúc chứ không phải một quy tắc phải nhớ: các đường
   trả về từ cache không đi qua đó. `TtsServiceTest` ghim đúng thứ tự này.

   Chiều ngược lại cũng được giữ: **dựng hỏng thì hoàn lượt.** Hoàn là ghi thêm một mốc `refunded_at`
   chứ không xóa dòng — "đã xảy ra rồi được trả lại" và "chưa từng xảy ra" là hai câu khác nhau, và
   chỉ câu đầu đúng. Nhờ vậy bảng còn đọc được như một sổ chi phí.

4. **Tạo audio bắt buộc đăng nhập**, chặn ngay ở tầng URL của Spring Security. Không phải vì sợ khách,
   mà vì không có danh tính thì không có gì để đếm hạn mức lên: một đường vô danh sẽ chỉ còn trần chung
   chặn, tức một người bền bỉ là đủ tiêu hết phần của cả ngày.

5. **Cache khóa theo bộ bốn (chương, phiên bản nội dung, giọng, tốc độ)** — đổi giọng là một bản audio
   khác, và sửa nội dung cũng vậy. Người đọc không chọn được giọng lẫn tốc độ (máy chủ quyết định),
   nên đường người đọc sinh tối đa một file cho mỗi chương mỗi phiên bản; console quản trị chọn giọng
   tự do.

6. **Token qua query param cho audio** — thẻ `<audio>` của HTML không gửi được header, nên
   `JwtAuthenticationFilter` chấp nhận thêm tham số `access_token`. Ngoại lệ ấy được thu về đúng phạm
   vi nó phục vụ: **chỉ request GET, và chỉ những đường phát media** (`/api/chapters/*/audio/*`,
   `/api/bgm/*/stream`). Trước đây nó có hiệu lực ở mọi đường, nên bất kỳ ai cũng dựng được một URL
   mang theo phiên đăng nhập của mình rồi dán đi — mà URL thì đi vào access log, vào `Referer`, vào
   lịch sử trình duyệt.

Việc chờ dựng xong dùng `GET /api/chapters/{id}/tts/{audioId}` chứ không phải danh sách audio: đường
danh sách cố ý ghi một lượt nghe mỗi lần gọi, mà thăm dò vài giây một lần sẽ thổi phồng biểu đồ thống
kê của trang quản trị lên hàng chục lượt nghe không có thật.

### Trộn âm thanh và bám chữ theo giọng đọc

Hai tính năng, một đường tiếng. Cả hai đều nằm ở `frontend/src/audio/` (TypeScript), phần duy nhất
cần tới máy chủ là mốc thời gian từng chữ.

#### Mốc thời gian đến từ đâu

Không đo lại, không đoán: ElevenLabs trả về mốc của **từng ký tự** ngay trong lần tổng hợp, qua
đường `/with-timestamps` — cùng một lần gọi, cùng một khoản tiền.

```
TtsGenerationWorker
   └─ ElevenLabsTtsClient.synthesize
        ├─ cắt chương thành khúc ≤ 4500 ký tự (TextChunker)
        ├─ mỗi khúc: POST …/with-timestamps → { audio_base64, alignment }
        │     và nhớ khúc ấy nằm ở đâu trong nội dung chương
        ├─ WordAligner: gộp ký tự thành chữ (ranh giới = khoảng trắng),
        │     cộng dồn thời gian giữa các khúc, quy vị trí ký tự về chương gốc
        └─ ProviderSpeech { audio, words[] }
   └─ TranscriptCodec → bảng audio_transcripts (JSON), audio_files.transcript_words = N
```

Bốn quyết định đáng nêu:

1. **Bảng riêng, không phải một cột.** Một chương ra chừng ba trăm KB JSON, mà Hibernate nạp mọi cột
   thường của một hàng ngay khi nạp hàng ấy — để chung thì mỗi lần liệt kê bản audio của một chương
   là một lần kéo cả mảng số đó về rồi vứt đi. Bên `audio_files` chỉ giữ lại một số nguyên đếm chữ,
   đủ để trả lời "bản này bám chữ được không". Khóa ngoại có `ON DELETE CASCADE`, nên mốc thời gian
   không bao giờ sống sót qua bản audio sinh ra nó.
2. **Hoặc đủ cả, hoặc bỏ hết.** Thiếu mốc của một khúc giữa chương thì mọi khúc sau nó lệch đi đúng
   bằng độ dài khúc thiếu. Một bản tô sáng lệch nửa phút tệ hơn hẳn một bản không tô sáng, nên thiếu
   một khúc là bỏ cả chương.
3. **Tài khoản không có đường `/with-timestamps` vẫn dùng được.** Gặp 404/405 thì client tự lùi về
   đường cũ và nhớ luôn cho các lần sau. Bản audio ra bình thường, chỉ là không bám chữ được.
4. **Endpoint mốc thời gian đi qua đúng hai lớp cửa của đường phát** (quyền chương + chủ sở hữu bản
   audio). Mốc thời gian gần như là nội dung chương chép lại thành mảng — để hở chỗ này là mở một cửa
   sau vào chương trả phí, và `AudioOwnershipTest` ghim điều đó.

#### Bộ trộn: vì sao Web Audio chứ không phải hai thẻ `<audio>`

```
                 ┌── MediaElementAudioSourceNode ──┐
<audio> giọng đọc┤  (phát dần + tua bằng Range)    ├── narrationGain ──┐
                 └─────────────────────────────────┘                   │
                                                                        ├── destination
        nhạc nền ── fetch → decodeAudioData → AudioBufferSourceNode ── bgmGain ──┘
                          (lặp liền mạch, không khe hở)
```

- **Một `AudioContext`, một đồng hồ.** Hai thẻ `<audio>` có hai đồng hồ và bị hệ điều hành đánh thức
  độc lập; trên điện thoại chúng trôi xa nhau, và cái người nghe nhận ra là nhạc nền tự to lên đúng
  lúc giọng đọc đang nói.
- **Giọng đọc vẫn đi qua thẻ `<audio>`** vì chương dài hàng chục phút: `decodeAudioData` sẽ bắt người
  nghe chờ trọn file trước chữ đầu tiên và làm mất khả năng tua bằng HTTP Range. Nhạc nền thì ngược
  lại — ngắn, và cần lặp không có khe hở, thứ mà `<audio loop>` không làm được vì phần đệm của MP3.
- **Nhường lời (ducking)** bằng `linearRampToValueAtTime` trên `bgmGain`: nhạc lùi xuống 35% khi có
  tiếng người, trở lại ở khoảng nghỉ.
- **Lối lùi bắt buộc phải có.** `createMediaElementSource` trên một file khác nguồn gốc mà không có
  CORS thì **không báo lỗi — nó im lặng cho ra toàn số 0**. Máy chủ audio ở đây nằm khác origin với
  trình duyệt, nên trước lần tải đầu tiên có một phép thử CORS hai byte; hỏng thì cả bộ trộn chuyển
  sang chế độ `element` (âm lượng chỉnh thẳng trên thẻ audio, nhạc nền chạy bằng thẻ thứ hai). Mất
  phần trộn mượt, giữ lại phần nghe được.
- **Bộ trộn sống lâu hơn giao diện.** Nó được dựng ở `ChapterPage` và giữ nguyên khi người đọc sang
  chương khác — đổi chương là đổi nguồn tiếng, không phải dựng lại đường tiếng, nên nhạc nền không
  đứt ở ranh giới hai chương.

#### Bám chữ: vì sao `requestAnimationFrame` chứ không phải `timeupdate`

`timeupdate` bắn khoảng bốn lần mỗi giây, vào lúc trình duyệt chọn chứ không phải lúc màn hình sắp
vẽ — tô sáng theo nó là trễ tới một phần tư giây, và trễ không đều. Vòng lặp ở đây chạy đúng trước
mỗi lần vẽ, và giữa hai lần `currentTime` nhích lên thì nó **nội suy bằng đồng hồ tường nhân tốc độ
đọc** (có trần 0.25s để không chạy trước giọng đọc lúc đang chờ dữ liệu).

Ba điều giữ cho nó không tốn kém:

1. **Vòng lặp không đụng tới React.** Một chương có năm nghìn ô chữ; `setState` mỗi khung hình sẽ kéo
   cả cây React đi so sánh lại sáu mươi lần một giây. Vòng lặp ghi thẳng lớp CSS vào DOM, và bảng tra
   nút DOM được dựng sẵn một lần cho mỗi chương. Thứ duy nhất đi qua React là "người đọc có tự cuộn
   tay hay không".
2. **Tra theo gợi ý, không duyệt lại.** Giọng đọc chạy tiến nên chữ tiếp theo gần như luôn nằm ngay
   sau chữ vừa rồi; chỉ khi người nghe tua thì mới cần tìm chia đôi (`WordTimeline`).
3. **Chỉ chạy khi có tiếng.** Lúc dừng, mỗi lần trạng thái đổi chỉ cần đúng một lần cập nhật.

Phần dóng chữ (`karaoke/align.ts`) đáng chú ý riêng: chỉ số ký tự máy chủ gửi về chỉ được dùng làm
**gợi ý**, luôn kiểm lại bằng chính nội dung chữ và dò tiếp vài chữ nếu sai. Chuỗi đến được màn hình
không phải lúc nào cũng là chuỗi đã gửi đi đọc — tầng gọi API gấp mọi phản hồi về Unicode NFC, chương
có thể đã được sửa một chữ sau khi bản audio dựng xong. Tin tuyệt đối vào chỉ số thì lệch một ký tự
là cả chương tô sáng sai một nhịp, mà lệch một nhịp còn tệ hơn không tô, vì nó khiến người đọc nghi
ngờ chính mắt mình. Dưới 60% số chữ khớp được thì coi như bản mốc ấy không còn thuộc về chương này,
và trang đọc nói thẳng điều đó thay vì tô bừa.

Nhạc nền có ba nguồn, hỏi song song và hỏng độc lập nhau:

1. **Kho trên máy chủ** — quản trị viên tải lên ở `/admin/nhac-nen`, người nghe thấy ngay ở lần mở
   trang sau. Đây là nguồn chính, và là nguồn duy nhất thêm được nhạc mà không phải dịch lại
   frontend. File nằm ở `BGM_DIR`, bảng `bgm_tracks` chỉ giữ tên file.
2. **`frontend/public/bgm/manifest.json`** — cách cũ, vẫn chạy: ai đã bỏ sẵn nhạc vào bản build thì
   chúng hiện sau nhạc trên máy chủ. Xem `frontend/public/bgm/README.md`.
3. **Máy người nghe** — vì bản quyền: không phải bản nhạc nào cũng phát công khai được. Tệp mở từ máy
   thành một object URL sống trong tab đang mở, không rời khỏi máy họ, và mất khi tải lại trang.

Một bản nhạc rút khỏi kho thì **tắt** chứ không xóa: người nghe có thể đang nghe dở, và một câu hỏi
bản quyền thường được gỡ ra rồi đưa lại. Đường phát vẫn phục vụ bản đã tắt; chỉ danh sách chọn là
không còn nó.

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

### Luồng đăng ký có xác thực email

```
POST /api/auth/register  { username, email, password }
      │
      ├─ kiểm tên đăng nhập và email còn trống
      ├─ ghi pending_registrations: thông tin khai + mật khẩu ĐÃ băm + SHA-256 của mã 6 số
      └─ @Async gửi mã về email          ← bảng users vẫn chưa có gì
      ▼
POST /api/auth/register/verify  { email, code }
      ├─ sai mã → tăng số lần thử, hết 5 lượt thì hủy luôn lượt đăng ký
      └─ đúng mã → kiểm lại tên/email còn trống → GHI VÀO users → trả JWT
```

Ba điểm đáng chú ý:

1. **Bảng `users` chỉ được ghi ở bước hai.** Không có bảng chờ thì hoặc phải tạo sẵn một tài khoản
   "chưa xác thực" — nó sẽ chiếm mất địa chỉ email của người chưa từng đồng ý — hoặc phải tin vào dữ
   liệu client gửi lại ở bước hai. Cả hai đều tệ hơn.
2. **Giới hạn số lần thử mới là thứ bảo vệ mã.** Mã chỉ có sáu chữ số nên việc băm nó không cản được
   ai; cái cản là bộ đếm 5 lượt. Bộ đếm đó dùng `noRollbackFor` để không bị giao dịch cuộn ngược theo
   ngoại lệ — nếu không thì nó không bao giờ tăng và mã dò được thoải mái.
3. **Bấm đăng ký lại không gửi thêm thư.** Trong 60 giây kể từ lần gửi trước, thông tin khai được cập
   nhật nhưng mã giữ nguyên: đổi mã mà không gửi lại sẽ làm hỏng cái người ta vừa nhận, còn gửi thêm
   một lá nữa là biến form đăng ký thành công cụ dội thư vào hòm thư người khác.

Máy chủ chưa cấu hình SMTP thì không có đường nào gửi mã đi, nên luồng lùi về cách cũ — tạo tài khoản
ngay ở bước một. Nếu không, một bản clone chưa điền `.env` sẽ không đăng ký nổi một tài khoản nào.

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

### Giới hạn tần suất — hai hàng rào, hai cách đếm

Trước đây backend không có một giới hạn nào. Ba hậu quả cụ thể, không phải giả thuyết: cửa đăng nhập
cho dò mật khẩu không giới hạn (và vì mỗi lần thử là một phép BCrypt cố ý chậm, nó đồng thời là cách
rẻ nhất để bào hết hai mươi luồng Tomcat); form đăng ký và form quên mật khẩu gửi thư thật, mà trần
60 giây sẵn có chỉ tính theo địa chỉ email nên đổi email là gửi tiếp; ô bình luận không có gì cản.

```
RateLimitFilter  (theo địa chỉ mạng, gáo token)      ← đứng TRƯỚC JwtAuthenticationFilter
   ├─ đăng nhập      10 lượt / 5 phút
   ├─ gửi thư         5 lượt / giờ
   ├─ nhập mã        20 lượt / giờ
   ├─ gọi AI         10 lượt / phút
   ├─ ghi dữ liệu    60 lượt / phút
   └─ mọi lời gọi   600 lượt / phút     ← cầu dao cuối
        ▼
AuthService  (theo TÀI KHOẢN)
   └─ 10 lần sai liên tiếp → nghỉ 15 phút
```

Ba điểm đáng chú ý:

1. **Đứng trước lớp xác thực.** Mỗi request mang token đều tốn một câu SELECT để nạp lại người dùng;
   chặn sau lớp ấy nghĩa là kẻ tấn công vẫn bắt được cơ sở dữ liệu làm việc cho mình.
2. **Hai cách đếm vì mỗi cách có một chỗ hở.** Đếm theo IP là thứ duy nhất biết được khi chưa ai đăng
   nhập, nhưng kẻ có sẵn một dải địa chỉ chỉ cần đổi nguồn sau mỗi mười lần thử. Bộ đếm theo tài khoản
   gắn với thứ họ không đổi được. Cái giá của nó cũng được nói ra: người ngoài khóa được tài khoản của
   người khác trong mười lăm phút — chấp nhận được vì quãng nghỉ tự hết, và người bị khóa vẫn đặt lại
   được mật khẩu qua email ngay lập tức, đường ấy không đi qua bộ đếm này.
3. **Gáo token chứ không phải cửa sổ cố định.** Đếm theo cửa sổ cho qua gấp đôi ở ranh giới: mười lượt
   lúc 12:00:59 và mười lượt nữa lúc 12:01:00. Với một hàng rào dựng để chặn việc dò mật khẩu thì đó
   đúng là chỗ nó cần chặt nhất.

Bộ đếm nằm trong bộ nhớ của một tiến trình — đánh đổi có ý thức, và khác hẳn với hạn mức AI: ở đó con
số quyết định một hóa đơn nên nó phải bền, còn ở đây nó chỉ quyết định nhịp, và mất nhịp trong một
khoảnh khắc khởi động lại không để lại hậu quả nào tích lũy. Toàn bộ mức nằm ở `app.ratelimit.*`.

### Cơ sở dữ liệu

| Nhóm | Bảng |
|---|---|
| Nội dung | `stories` · `chapters` · `audio_files` · `audio_transcripts` · `genres` · `authors` · `bgm_tracks` |
| Người dùng | `users` · `password_reset_tokens` · `pending_registrations` |
| Đọc & tương tác | `reading_progress` · `favorites` · `ratings_comments` · `view_events` |
| Tiền | `wallets` · `wallet_transactions` · `coin_packages` · `chapter_entitlements` · `vip_plans` · `payment_orders` · `gift_codes` · `gift_code_redemptions` |
| Chi phí AI | `ai_usage` |

`view_events` ghi mỗi lượt mở chương để đọc hoặc nghe. Cần bảng riêng vì `view_count` chỉ là số cộng
dồn — biết tổng nhưng không tách được ra từng ngày, mà biểu đồ theo ngày lại hỏi đúng câu đó.

`ai_usage` là sổ đếm lượt dùng AI, dùng chung cho cả nút "Nghe bằng AI" lẫn trợ lý. Chỉ có đường ghi
thêm; hoàn lượt là đặt `refunded_at` chứ không xóa dòng. Khóa ngoại tới `audio_files` có
`ON DELETE SET NULL`, nên dòng sổ sống sót qua chính bản audio đã sinh ra nó — đó là toàn bộ lý do
bảng này tồn tại.

`wallets` giữ số dư còn `wallet_transactions` giải thích nó, và cả hai luôn được ghi trong cùng một
giao dịch: số dư một mình không trả lời được "vì sao lại là con số này", còn sổ cái một mình thì mỗi
lần mở chương là một lần cộng dồn cả lịch sử.

`gift_codes` không có cột trạng thái. SCHEDULED / ACTIVE / EXPIRED / DISABLED / EXHAUSTED đều suy ra
được từ `enabled`, `start_at`, `end_at`, `max_uses` và `used_count` — cùng lập luận với `published_at`
ở đoạn dưới. Ba lời hứa của tính năng này đều do cơ sở dữ liệu giữ chứ không do mã nguồn kiểm tra:
`UNIQUE(code)` trên giá trị đã viết hoa khiến `summer2026` và `SUMMER2026` là một mã;
`UNIQUE(gift_code_id, user_id)` khiến bấm "Đổi mã" mười lần cùng lúc chỉ cộng Xu một lần; và
`UPDATE ... WHERE used_count < max_uses` — một câu lệnh vừa kiểm vừa ghi, giống hệt phép trừ Xu ở
`wallets` — khiến bốn mươi người tranh một mã còn mười lượt thì đúng mười người nhận được. Cả ba nằm
trong một giao dịch cùng với việc cộng Xu, nên số dư ví, sổ cái Xu, sổ đổi mã và cột đếm lượt không
lệch nhau được.

`stories.published_at` và `chapters.published_at` là một cột mang ba trạng thái — null là nháp, mốc ở
tương lai là hẹn giờ, mốc đã qua là đã đăng. Không có tác vụ định kỳ nào đi đổi trạng thái: "tới giờ
tự đăng" là một mệnh đề `WHERE` chạy lúc có người đọc, nên không có cửa sổ sai và không có lần chạy
nào bị bỏ lỡ trên một máy chủ hay ngủ.

Quyền VIP đến từ hai nguồn tách bạch: `users.is_vip` là quyền Admin cấp tay, không hạn; `users.vip_until`
là hạn của gói đã mua. `User.isVip()` xét cả hai, nên phần còn lại của hệ thống không cần biết sự khác
biệt đó.

`pending_registrations` giữ những lượt đăng ký chưa nhập mã. Bản ghi ở đó không phải là tài khoản: hết
hạn hoặc hết lượt thử là bị xóa, và chỉ khi mã đúng thì nội dung mới được chép sang `users`.

`users.google_id` giữ claim `sub` của tài khoản Google, null nghĩa là chưa liên kết. Tài khoản tạo bằng
Google vẫn có `password_hash` — cột đó NOT NULL — nhưng là một chuỗi ngẫu nhiên không ai đoán được;
muốn có mật khẩu thật thì đi đường "quên mật khẩu".

Lược đồ do **Flyway** dựng, từ các tệp trong `backend/src/main/resources/db/migration`. Hibernate để
ở `ddl-auto=validate`: nó chỉ đối chiếu và báo lỗi ngay lúc khởi động nếu entity lệch với lược đồ, chứ
không tự sửa bảng sau lưng nữa. Migration chạy tự động khi ứng dụng khởi động — không có bước thủ công
nào, và cũng không có file SQL nào phải chạy tay.

---

## Bốn vấn đề lớn và cách giải

Bốn đợt thay đổi gần nhất, mỗi đợt giải một vấn đề khác hẳn nhau. Ghi lại ở đây vì **cách chọn** trong
cả bốn đều đáng nói hơn thứ cuối cùng được viết ra.

### 1. Kết nối cơ sở dữ liệu bị giữ suốt lúc chờ mạng

**Triệu chứng.** `Connection is not available, request timed out` hiện lên ở những request chẳng liên
quan gì tới audio.

**Nguyên nhân.** Việc dựng audio nằm gọn trong một giao dịch: mở giao dịch, đọc hàng chờ, rồi gọi
ElevenLabs và đứng chờ. Một chương bị cắt thành nhiều lần gọi 4500 ký tự, mỗi lần chờ tối đa hai phút —
nên một chương 20.000 ký tự giữ một kết nối suốt **mười phút mà không chạy câu lệnh SQL nào**. Một lô
hai mươi chương do Admin dựng giữ bốn kết nối, trên tổng số mười của cả ứng dụng.

**Cách giải.** Cắt một lượt dựng thành ba đoạn, và chỉ hai đoạn đầu cuối cần cơ sở dữ liệu:

```
đọc bản ghi   ──►   gọi ElevenLabs   ──►   ghi kết quả
(giao dịch 1)      (KHÔNG giao dịch)      (giao dịch 2)
   vài ms            hàng phút              vài ms
```

Việc này buộc phải tách thành một bean riêng (`TtsGenerationRecords`): `@Transactional` chạy bằng
proxy, nên hai method trong cùng một lớp không mở được hai giao dịch. Tải file lên cũng vậy — file
được ghi **trước câu lệnh SQL đầu tiên**, dựa vào việc Hibernate lấy kết nối một cách trì hoãn, nên
`hibernate.connection.handling_mode` được viết thẳng vào `application.properties` thay vì để nó là một
mặc định mà mã nguồn lặng lẽ phụ thuộc.

**Điều cố ý không làm:** nâng kích thước pool. Mười kết nối vốn thừa cho hai mươi luồng Tomcat khi giao
dịch chỉ sống vài mili giây; nâng lên chỉ dời thời điểm cạn và vứt mất manh mối. Thay vào đó bật
`leak-detection-threshold=20s` — sau đợt sửa, giao dịch dài nhất còn lại chỉ gồm vài câu lệnh, nên bất
cứ thứ gì chạm ngưỡng ấy đều đáng in ra ngăn xếp.

### 2. Mua lẻ từng chương, bên cạnh gói VIP

**Vấn đề.** Người đọc muốn đúng một chương chỉ có hai lựa chọn: mua cả tháng VIP, hoặc không đọc.

**Cách giải.** Ví Xu + giá theo từng chương. Hai quyết định về cấu trúc đáng chú ý:

**Mức khóa và giá là hai cột riêng, không gộp thành một enum.** Chúng trả lời hai câu hỏi khác nhau —
*ai được thấy chương* và *mở nó tốn bao nhiêu*. Gộp lại thì mỗi cách bán mới lại nhân đôi số giá trị:
`VIP`, `COIN`, `VIP_OR_COIN`, `MEMBER_AND_COIN`. Tách ra thì chúng tự kết hợp: VIP kèm giá 50 nghĩa là
VIP đọc miễn phí, còn lại trả 50 Xu. Mọi chương cũ nhận giá 0, và nhánh giá-0 gọi thẳng vào lớp kiểm
quyền cũ không sửa gì — nên không có gì đã xuất bản đổi hành vi.

**Vừa có số dư vừa có sổ cái, không chọn một.** Số dư đứng một mình không nói được vì sao nó là con số
ấy, và câu hỏi đầu tiên về "mất Xu" sẽ không có câu trả lời. Sổ cái đứng một mình thì mỗi lần mở chương
là một lần cộng dồn cả lịch sử. Cả hai được ghi trong cùng một giao dịch, và mỗi dòng sổ mang số dư
trước/sau, nên lệch thì tìm ra bằng một câu truy vấn chứ không phải bằng một cuộc điều tra.

**Không có chỗ nào tin vào "kiểm rồi ghi".** Tiêu Xu là `UPDATE ... WHERE balance >= price`, nên hai
lượt mua đồng thời mà chỉ đủ tiền cho một thì đúng một lượt thành công. Mua trùng bị chặn bởi
`UNIQUE(user_id, chapter_id)`, nên nhấp đúp hay request thử lại không bị tính tiền hai lần. Cả hai đều
do cơ sở dữ liệu bảo đảm, vì hai request đua nhau đều đọc thấy "chưa mua" trước khi bên nào kịp ghi.

### 3. Dữ liệu và audio sống sót qua mỗi lần triển khai

**Đây là vấn đề mà tiền đề ban đầu sai, và điều đó thay đổi toàn bộ lời giải.**

Ý định lúc đầu là: để audio tạm trong một thư mục trên máy chủ, deploy xong thì mã hóa cả thư mục dữ
liệu, tự tạo database rồi nhập dữ liệu vào. Khi đi kiểm tra hạ tầng thật thì lộ ra hai điều:

**Không có thư mục nào để mà mã hóa.** Backend chạy trên Render gói miễn phí, mà hệ tệp ở đó là *tạm
thời*: file bị xóa sạch mỗi lần triển khai lại, mỗi lần khởi động lại, **và sau 15 phút không ai truy
cập** (dịch vụ ngủ đi, tỉnh dậy là một máy sạch). Gói miễn phí cũng không gắn được Persistent Disk.

**Và hậu quả nặng hơn là một bug đang chạy:** mỗi bản audio dựng bằng ElevenLabs — mất tiền thật cho
từng bản — biến mất trong vòng một giờ, trong khi hàng trong cơ sở dữ liệu (nằm ở Aiven, bền vững) vẫn
ghi `status = READY`. Cờ ấy khiến bộ nhớ đệm từ chối dựng lại, nên chương đó **kẹt vĩnh viễn** ở trạng
thái có audio mà bấm play không ra gì.

**Cách giải — lưu trữ.** Nơi lưu file trở thành một lựa chọn theo môi trường, không còn là giả định nằm
rải rác trong mã nghiệp vụ:

```
                        MediaStorage  (giao diện)
                              │
              ┌───────────────┴───────────────┐
      LocalMediaStorage              CloudinaryMediaStorage
   máy cá nhân / máy có đĩa riêng      bắt buộc trên Render
```

Bản cục bộ ghi vào file `.tmp` cùng thư mục rồi đổi tên nguyên tử, nên tên thật chỉ xuất hiện khi byte
cuối cùng đã nằm trên đĩa — tiến trình bị giết giữa chừng để lại một file tạm vô hại chứ không để lại
một file MP3 cụt mang tên hợp lệ. Bản Cloudinary lưu ở dạng `authenticated` và **chuyển tiếp HTTP Range**
lên nơi lưu, nên máy chủ không bao giờ giữ trọn một chương trong bộ nhớ để trả về một khúc giữa của nó.

Quyền nghe vẫn do máy chủ giữ, không đưa URL cho trình duyệt: trên gói Cloudinary miễn phí chữ ký
**không hết hạn được**, mà quyền nghe ở đây thì thay đổi và phải thu hồi được (VIP có hạn, chương bán
bằng Xu, bản audio người đọc tự dựng là của riêng họ).

**Cách giải — mã hóa.** Không mã hóa cả thư mục, và cũng không mã hóa file audio đang chạy. Lý do:
khóa buộc phải nằm cùng máy với ứng dụng đọc nó, nên nó không chặn được ai đã đọc được ứng dụng — đổi
lại thì mất HTTP Range, phải giải mã trong heap 224MB, và mất khóa là mất sạch audio. Mã hóa được đặt
vào đúng chỗ nó có tác dụng thật: **gói sao lưu**, thứ duy nhất rời khỏi vành đai nhà cung cấp (chứa
email thật, mật khẩu đã băm, lịch sử giao dịch Xu). Dùng `age`, và **khóa riêng không bao giờ nằm trên
máy chủ hay trong GitHub Secrets** — máy chạy sao lưu chỉ cần khóa công khai, nên bị chiếm cũng không
đọc được gói cũ.

**Cách giải — tự tạo database và nhập dữ liệu.** Tách làm hai vòng đời riêng:

```
Ứng dụng khởi động  ──►  Flyway migrate  ──►  ddl-auto=validate đối chiếu  ──►  /actuator/health
                          (tự động)              (báo lỗi nếu lệch)

Nhập nội dung       ──►  --spring.profiles.active=import   (việc riêng, chạy khi cần)
```

Lược đồ **tự dựng** khi ứng dụng khởi động; không có bước thủ công nào. Nhưng nhập nội dung thì **cố ý
không** nằm trong đường khởi động — một tệp JSON sai dấu phẩy không được phép làm cả trang web không lên
được. Importer chạy lại nhiều lần an toàn (truyện nhận diện theo tên, chương theo cặp truyện + số
chương), mỗi chương là một giao dịch riêng nên chương thứ 47 hỏng không kéo theo 46 chương trước nó, và
audio được đối chiếu SHA-256 **trước** khi có gì được ghi. Cờ `READY` chỉ bật sau khi byte đã tới nơi.

### 4. Ba hàng rào chi phí, và chỗ cả ba cùng thủng

**Đây là đợt sửa lỗi, không phải đợt thêm tính năng** — nên phần đáng đọc là *vì sao không ai thấy ba
lỗi này sớm hơn*. Cả ba đều nằm ở chỗ hai đoạn mã đúng đứng cạnh nhau và cộng lại thành sai.

**Hạn mức "Nghe bằng AI" nạp lại được bằng cách đăng xuất.** `ReaderNarrationCleanup` dọn bản audio
người đọc tự dựng ở mỗi đầu phiên — đúng, vì nó là thứ dựng tạm cho một buổi đọc. `ReaderTtsService`
đếm hạn mức bằng `COUNT(*)` trên chính những hàng ấy — cũng đúng, và còn có một tính chất đẹp: dùng
lại cache không sinh hàng nào nên không tốn lượt nào. Nhưng đứng cạnh nhau thì bấm hết ba lượt, đăng
xuất, đăng nhập, lại có ba lượt, lặp vô hạn.

Gốc rễ là hai vòng đời khác hẳn nhau bị buộc vào một hàng: bản audio là **tài sản** (dọn được), một
lượt đã dùng là **sự kiện** (không xóa được nữa). Lời giải là tách chúng ra — bảng `ai_usage` chỉ có
đường ghi thêm. Tính chất "cache miễn phí" vẫn giữ nguyên, vì sổ chỉ được ghi bên trong
`beforeNewGeneration`, mà đường trả về từ cache không đi qua đó.

**Hạn mức trợ lý AI mất sau mỗi lần máy chủ ngủ dậy.** Bộ đếm nằm trong một bảng băm trong bộ nhớ.
Ghi chú lúc ấy có nêu một rủi ro — chạy nhiều bản ứng dụng song song — nhưng bỏ sót rủi ro đang thật
sự xảy ra: Render gói miễn phí ngủ sau 15 phút vắng khách, và tỉnh dậy là một tiến trình mới với bộ
đếm về 0. Trần 500 lượt/ngày chỉ có hiệu lực giữa hai lần ngủ, nên **trang càng ít khách thì hàng rào
càng lỏng** — đúng chiều ngược với trực giác. Lời giải là dùng chung bảng `ai_usage`: một migration,
hai hàng rào chi phí, một nguồn sự thật nằm trên đĩa.

**Không có một giới hạn tần suất nào trong toàn bộ backend.** Xem
[mục kiến trúc](#giới-hạn-tần-suất--hai-hàng-rào-hai-cách-đếm). Điều đáng nói ở đây là *hai* hàng rào
chứ không phải một: đếm theo IP là thứ duy nhất biết được khi chưa ai đăng nhập, nhưng kẻ có sẵn một
dải địa chỉ lách qua được; bộ đếm theo tài khoản bịt đúng chỗ ấy, và mang một cái giá phải nói ra
(người ngoài khóa được tài khoản người khác trong mười lăm phút).

Cùng đợt còn hai chỗ hẹp hơn: `access_token` trên URL từng có hiệu lực ở **mọi** đường chứ không riêng
đường phát media như ghi chú của nó nói; và trang chi tiết truyện từng trả về **toàn bộ** chương trong
một lần gọi — không ai thấy vấn đề với sáu truyện mẫu bốn chương, nhưng là vài trăm KB JSON mỗi lượt
truy cập với một truyện dịch nghìn chương.

Chi tiết: [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) · [docs/BACKUP.md](docs/BACKUP.md) ·
[content/README.md](content/README.md)

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
│     ├─ audio/          TypeScript: bộ trộn Web Audio (giọng đọc + nhạc nền) và
│     │                  karaoke/ cho phần bám chữ theo giọng đọc
│     ├─ components/     Component dùng lại
│     ├─ context/        Auth, Theme
│     ├─ hooks/          useChapterAudio, useDebouncedValue, useAuthProviders
│     ├─ pages/          Trang theo route (thư mục con admin/)
│     ├─ styles/         CSS thuần, chia theo nhóm
│     ├─ utils/          Định dạng tiền/ngày, chuẩn hóa Unicode, đích đến sau đăng nhập
│     └─ brand.js        URL logo và ảnh banner trên Cloudinary
├─ content/              Gói nội dung để nhập truyện/chương/audio (xem README trong đó)
├─ docs/                 DEPLOYMENT.md, BACKUP.md
├─ ops/                  backup.sh, restore.sh
├─ render.yaml           Cấu hình dịch vụ trên Render
├─ docker-compose.yml    MySQL 8 cho máy cá nhân
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
| `npm run typecheck` | Kiểm kiểu phần TypeScript (`src/audio/`, các component `.tsx`) — mã `.jsx` cũ vẫn để nguyên, xem `tsconfig.json` |

---

## Cấu hình API key

Tất cả đều nằm trong `.env` ở thư mục gốc. Không có key nào được viết cứng trong mã nguồn.

### Giọng đọc — ElevenLabs (bắt buộc nếu Admin muốn tạo audio thay vì tự upload)

```properties
# https://elevenlabs.io → Profile → API Keys
ELEVENLABS_API_KEY=
# voice_id lấy trong mục Voices; đây là giọng dùng cho mọi chương.
ELEVENLABS_VOICE_ID=
# Bắt buộc dùng model đa ngôn ngữ thì tiếng Việt mới đọc đúng
ELEVENLABS_MODEL_ID=eleven_multilingual_v2

# Thứ tự thử các nhà cung cấp. Hiện chỉ có một, để đây để thêm nhà cung cấp
# khác mà không phải sửa mã nguồn.
TTS_PROVIDERS=elevenlabs
```

Không điền key thì phần còn lại của web vẫn chạy bình thường: Admin vẫn upload được bản thu, chỉ nút
"Tạo audio" trong trang quản trị báo rõ là máy chủ chưa cấu hình. Trang đọc cũng không hiện nút "Nghe
bằng AI" — `GET /api/tts/status` gộp luôn điều kiện "có nhà cung cấp nào chưa" vào cờ `enabled`, nên
người đọc không gặp một cái nút chắc chắn sẽ lỗi.

`ELEVENLABS_VOICE_ID` là giọng dùng khi lần tạo đó không chỉ định giọng nào. Muốn đổi giọng cho một
chương hoặc một lô chương thì chọn ngay trong trang quản trị — danh sách giọng của tài khoản
ElevenLabs được tải về để chọn.

#### Ngân sách cho nút "Nghe bằng AI" của người đọc

```properties
# Tắt hẳn đường người đọc; console quản trị vẫn dựng audio bình thường.
TTS_READER_ENABLED=true
# Số bản audio MỚI mỗi người được tạo trong ngày. -1 = không giới hạn, 0 = chặn hẳn.
# Nghe lại chương đã có audio không tính vào đây.
TTS_READER_DAILY_QUOTA=3
TTS_READER_DAILY_QUOTA_VIP=10
# Trần chung cho toàn bộ người đọc trong ngày — cầu dao cuối về chi phí.
TTS_READER_DAILY_QUOTA_GLOBAL=100
# Một chương bị cắt thành nhiều lần gọi nhà cung cấp (4500 ký tự mỗi lần), nên
# đây mới là cần chi phí thật: 20000 ký tự ≈ 5 lần gọi cho một bản audio.
TTS_READER_MAX_CHARS=20000
```

Hạn mức đếm theo ngày ở giờ Việt Nam (`Asia/Ho_Chi_Minh`), và **lần dựng hỏng thì hoàn lượt** — người
đọc không mất lượt vì lỗi của nhà cung cấp. Hết lượt thì API trả **429** kèm header `Retry-After` tính
tới nửa đêm.

Cách đếm: mỗi bản audio người đọc tự tạo được đóng dấu `audio_files.requested_by`, và hạn mức là một
phép đếm trên đúng những dòng đó. Nhờ vậy hai điều sau là hệ quả của cấu trúc chứ không phải một quy
tắc phải nhớ — dùng lại cache không tốn lượt (vì không sinh dòng nào), và một lô hàng trăm chương do
Admin dựng không ăn vào ngân sách của người đọc (vì không có tên người).

#### Về việc "tốc độ đọc" và "chọn giọng nam/nữ" (mục 4.5 [NC])

Người đọc **không** chọn giọng và tốc độ khi tạo audio, vì hai thứ đó là thành phần khóa cache: để
chọn thì mỗi lựa chọn lại sinh thêm một file cho cùng một chương. Phần "tốc độ đọc" được đáp ứng ở
chỗ đúng hơn — menu tốc độ phát 0.75–2× ngay trên trình phát, đổi được sau khi audio đã có và không
tốn thêm đồng nào. ElevenLabs cũng không nhận tham số tốc độ, nên một ô chọn tốc độ lúc tạo sẽ là một
ô không có tác dụng gì. Việc chọn giọng cho từng chương nằm ở trang quản trị.

### Trợ lý đọc truyện — Google Gemini

```properties
# https://aistudio.google.com → Get API key
GEMINI_API_KEY=
GEMINI_MODEL=gemini-3.1-flash-lite

# Tắt hẳn tính năng mà phần còn lại của web chạy bình thường.
AI_ASSISTANT_ENABLED=true

# Số câu hỏi mỗi người trong ngày. -1 = không giới hạn, 0 = chặn hẳn.
AI_DAILY_QUOTA=20
AI_DAILY_QUOTA_VIP=50
# Trần chung cho toàn bộ người đọc trong ngày — cầu dao cuối về chi phí.
AI_DAILY_QUOTA_GLOBAL=500

# Mỗi câu hỏi gửi kèm cả nội dung chương, nên đây là hàng rào CHI PHÍ chứ không
# phải hàng rào kỹ thuật: cửa sổ ngữ cảnh của model rộng hơn hẳn con số này.
AI_MAX_CHAPTER_CHARS=24000
AI_MAX_QUESTION_CHARS=500
# Số lượt hỏi đáp cũ gửi kèm để trợ lý hiểu câu hỏi nối tiếp. 0 = không nhớ gì.
AI_MAX_HISTORY_TURNS=6
```

Để trống `GEMINI_API_KEY` thì trang đọc **không vẽ gì cả** — không có nút, không có lời mời. Cùng một
nếp với "Nghe bằng AI": `GET /api/ai/status` gộp luôn điều kiện "có key chưa" vào cờ `enabled`.

Hạn mức đếm trên bảng `ai_usage`, chung với đường tạo audio — xem
[Cơ sở dữ liệu](#cơ-sở-dữ-liệu). Trình duyệt chỉ gửi lên một con số `chapterId`; nội dung chương do
chính máy chủ tra ra **sau** khi đi qua cùng cửa xét quyền mà đường đọc chữ dùng, nên trợ lý không thể
thành cửa sau vào chương trả phí. Hội thoại sống trong trình duyệt và chết cùng chương — không bảng,
không lịch sử lưu lại.

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

Để trống `MAIL_USERNAME` thì liên kết "Quên mật khẩu?" **tự ẩn** khỏi trang đăng nhập, trang
`/quen-mat-khau` nói rõ là máy chủ chưa cấu hình email thay vì để người dùng gửi rồi chờ vô ích, và
đăng ký **bỏ luôn bước nhập mã** — tài khoản được tạo ngay như trước khi có chức năng này.

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
| `STORAGE_DRIVER` | local | `local` hoặc `cloudinary` — xem mục dưới |
| `AUDIO_DIR` | ./uploads/audio | Nơi lưu file audio (chỉ khi `STORAGE_DRIVER=local`) |
| `BGM_DIR` | ./uploads/bgm | Nơi lưu nhạc nền (chỉ khi `STORAGE_DRIVER=local`) |
| `SERVER_PORT` | 8080 | Cổng backend |

### Giới hạn tần suất

Những con số này được chọn để **không một người dùng thật nào chạm tới**, còn kẻ dò thì chạm ngay ở
giây thứ hai. Hạ xuống là siết lại được ngay, không phải sửa code.

```properties
# Tắt hẳn hàng rào (chỉ dùng khi chạy test tải).
RATE_LIMIT_ENABLED=true
# Số lượt mỗi địa chỉ mạng.
RATE_LIMIT_LOGIN=10      # / 5 phút — cửa đăng nhập
RATE_LIMIT_MAIL=5        # / giờ — đăng ký và quên mật khẩu, hai đường gửi thư thật
RATE_LIMIT_CODE=20       # / giờ — nhập mã OTP và đặt lại mật khẩu
RATE_LIMIT_AI=10         # / phút — tạo audio và hỏi trợ lý
RATE_LIMIT_WRITE=60      # / phút — bình luận, chấm sao, yêu thích
RATE_LIMIT_ANY=600       # / phút — cầu dao cuối cho mọi lời gọi

# Hàng rào thứ hai: đếm theo TÀI KHOẢN, nên kẻ đổi địa chỉ mạng cũng không lách được.
LOGIN_MAX_FAILURES=10
LOGIN_LOCK_FOR=15m
```

Chạy sau một proxy (Render, Cloudflare, Nginx) thì địa chỉ người gọi đọc từ nhịp đầu của
`X-Forwarded-For`. Header ấy giả được, và điều đó chỉ chấp nhận được vì mọi lưu lượng đều buộc phải đi
qua proxy của nhà cung cấp, thứ ghi đè lại header trước khi chuyển tiếp — đứng sau một proxy không làm
việc ấy thì `RateLimitFilter.clientAddress` là dòng phải sửa.

---

## Triển khai và lưu trữ

Bản chạy thật nằm trên ba dịch vụ, đều là gói miễn phí:

```
   Vercel                Render                  Aiven
  React SPA  ──HTTPS──►  Spring Boot  ──TLS──►  MySQL 8 (1GB)
                              │
                              ▼
                         Cloudinary
                      audio + ảnh đại diện
```

### Audio nằm ở đâu, và vì sao

Ứng dụng không tự quyết định nơi lưu file — nó nói chuyện với `MediaStorage`, và
`STORAGE_DRIVER` chọn bản triển khai:

| | Dùng khi | Ghi chú |
|---|---|---|
| `local` | Lập trình ở máy cá nhân; máy chủ có đĩa riêng | Ghi qua file tạm rồi đổi tên nguyên tử, nên không có file MP3 cụt nào mang tên hợp lệ |
| `cloudinary` | **Bắt buộc trên Render** | Lưu dạng `authenticated`; máy chủ ký đường dẫn và chuyển tiếp byte |

> **Vì sao bắt buộc trên Render:** hệ tệp của gói miễn phí là *tạm thời* — file
> biến mất mỗi lần triển khai lại, khởi động lại, **và sau 15 phút không ai truy
> cập**. Để `local` ở đó nghĩa là mỗi bản audio dựng bằng ElevenLabs (mất tiền
> thật) bốc hơi trong vòng một giờ, trong khi hàng trong cơ sở dữ liệu vẫn ghi
> `READY` — và cờ ấy chặn luôn đường dựng lại.

### Quyền nghe vẫn do máy chủ giữ

Audio **không** được trả về dưới dạng URL cho trình duyệt, kể cả URL đã ký. Trên
gói Cloudinary miễn phí, chữ ký không hết hạn được (thời hạn là tính năng gói
Advanced), mà quyền nghe ở đây thì thay đổi và phải thu hồi được: VIP có hạn,
chương bán bằng Xu, bản audio người đọc tự dựng là của riêng họ.

Nên mọi byte đi qua `GET /api/chapters/{id}/audio/{audioId}`, sau hai lớp cửa
đã có sẵn — `ChapterAccessService` rồi `requireOwnership`. Khoảng byte trình phát
hỏi được chuyển tiếp lên Cloudinary, nên máy chủ không bao giờ giữ trọn một
chương trong bộ nhớ để trả về một khúc giữa của nó. Tua vẫn chạy như trước.

### Tài liệu chi tiết

| | |
|---|---|
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Biến môi trường, luồng triển khai, hạn mức từng dịch vụ |
| [docs/BACKUP.md](docs/BACKUP.md) | Sao lưu, phục hồi, và chỗ cất khóa mã hóa |
| [content/README.md](content/README.md) | Nhập truyện/chương/audio từ tệp trên đĩa |
| `render.yaml` | Cấu hình Render, để trong Git thay vì chỉ ở dashboard |

### Đối chiếu cơ sở dữ liệu với nơi lưu file

```
GET  /api/admin/storage/audit     # liệt kê bản ghi trỏ tới file không còn tồn tại
POST /api/admin/storage/repair    # đánh dấu chúng hỏng, để chương dựng lại được
```

Không cần chạy định kỳ: gặp một bản audio mất file trên đường phát thì nó tự
được đánh dấu ngay tại chỗ, và nút "Nghe bằng AI" dựng lại được bản mới.

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

Đăng nhập bằng tài khoản quản trị thì vào thẳng `/admin` — bằng mật khẩu hay bằng Google đều vậy, và
mở lại trang đăng nhập khi đã có phiên cũng đưa về đó. Không phải để chặn: quản trị viên vẫn mở được
mọi trang của người đọc, và thanh bên có sẵn lối "Xem trang người đọc". Chỉ là họ không mở trang để
hạ cánh xuống trang chủ rồi tự tìm đường qua menu tài khoản. Chỗ họ *đang định* tới vẫn được tôn
trọng: bị bật khỏi một trang quản trị vì hết phiên thì đăng nhập lại là quay đúng về trang ấy.

---

## Danh sách API

Tài liệu đầy đủ có tương tác: **`http://localhost:8080/swagger-ui.html`**

### Công khai

| Method | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/auth/providers` | Máy chủ đang bật cách đăng nhập nào (Google, quên mật khẩu) |
| POST | `/api/auth/register` | Bắt đầu đăng ký — gửi mã OTP, **chưa tạo tài khoản** |
| POST | `/api/auth/register/verify` | Nhập mã; đúng mã thì tài khoản mới được ghi vào `users` |
| POST | `/api/auth/register/resend` | Gửi lại mã, chặn bấm liên tục trong 60 giây |
| POST | `/api/auth/login` | Đăng nhập, trả JWT |
| POST | `/api/auth/google` | Đăng nhập bằng ID token của Google; lần đầu thì tạo luôn tài khoản |
| POST | `/api/auth/forgot-password` | Gửi liên kết đặt lại mật khẩu — **trả lời như nhau dù email có tồn tại hay không** |
| POST | `/api/auth/reset-password` | Đặt mật khẩu mới bằng token trong email |
| GET | `/api/stories` | Danh sách truyện (keyword, genreId, status, sort, page, size) |
| GET | `/api/stories/{id}` | Chi tiết truyện + **trang đầu** danh sách chương |
| GET | `/api/stories/{id}/chapters` | Một trang chương (`q`, `order`, `page`, `size`) — `q` nhận tên chương hoặc một con số để nhảy tới đúng chương ấy |
| GET | `/api/stories/top-listened` | Xếp hạng truyện nghe nhiều nhất theo ngày/tuần/tháng |
| GET | `/api/chapters/{id}` | Nội dung chương — **403 nếu không đủ quyền, 404 nếu chưa đăng** |
| GET | `/api/chapters/{id}/access` | Quyền đọc của người đang gọi, kèm giá Xu và số dư — trang đọc hỏi trước khi thử tải nội dung |
| GET | `/api/chapters/{id}/events` | Luồng SSE báo chương vừa đổi nội dung; không mang gì riêng tư nên không cần token |
| GET | `/api/chapters/{id}/audio` | Các bản audio của chương (mỗi lần gọi tính một lượt nghe) |
| GET | `/api/chapters/{id}/audio/{audioId}` | Stream audio, hỗ trợ Range |
| GET | `/api/chapters/{id}/audio/{audioId}/transcript` | Mốc thời gian từng chữ, cho phần bám chữ theo giọng đọc — cùng lớp chặn quyền với đường stream |
| GET | `/api/tts/status` | Có tạo được audio không, và còn bao nhiêu lượt hôm nay |
| GET | `/api/ai/status` | Trợ lý AI có bật không, và còn bao nhiêu lượt hỏi hôm nay |
| GET | `/api/genres`, `/api/authors` | Danh mục |
| GET | `/api/bgm` | Kho nhạc nền đang mở cho người nghe chọn |
| GET | `/api/bgm/{id}/stream` | Phát một bản nhạc nền — trả trọn file, cache 1 ngày |
| GET | `/api/stories/{id}/comments` | Bình luận của một truyện |
| GET | `/api/vip/plans` | Bảng giá các gói VIP đang bán |
| GET | `/api/wallet/packages` | Bảng giá các gói nạp Xu đang bán |
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
| POST | `/api/chapters/{id}/tts` | Nút "Nghe bằng AI" — dùng lại bản đã có thì không tốn lượt; hết lượt trả **429** |
| GET | `/api/chapters/{id}/tts/{audioId}` | Chờ dựng xong, **không tính lượt nghe** |
| POST | `/api/ai/story-assistant` | Hỏi trợ lý về chương đang đọc; hết lượt trả **429** |
| POST | `/api/stories/{id}/comments` | Gửi đánh giá và bình luận |
| DELETE | `/api/comments/{id}` | Xóa bình luận của mình (Admin xóa được của mọi người) |
| POST | `/api/chapters/{id}/purchase` | Mở khóa một chương bằng Xu — gọi lại không tính tiền hai lần |
| GET | `/api/wallet` · `/api/wallet/transactions` | Số dư Xu, và sổ cái từng giao dịch |
| POST | `/api/wallet/orders` | Tạo đơn nạp Xu, trả về link thanh toán PayOS |
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
| PATCH | `/api/admin/chapters/{id}/pricing` · `/pricing` | Đặt giá Xu cho một chương hoặc nhiều chương |
| PATCH | `/api/admin/chapters/{id}/publication` | Đăng / gỡ xuống / hẹn giờ đăng một chương |
| PATCH | `/api/admin/stories/{id}/publication` | Đăng / gỡ xuống / hẹn giờ đăng cả một truyện |
| GET | `/api/admin/chapters/{id}/audio` | Mọi bản audio của một chương, **kể cả bản hỏng** |
| POST | `/api/admin/chapters/{id}/audio` | Upload audio thu sẵn |
| DELETE | `/api/admin/audio/{audioId}` | Xóa một bản audio |
| GET | `/api/admin/audio/chapters` | Chương kèm tình trạng audio |
| POST | `/api/admin/audio/batch-tts` | Tạo audio hàng loạt — chọn giọng tự do, **không bị hạn mức** |
| GET | `/api/admin/comments` | Toàn bộ bình luận để kiểm duyệt |
| POST/PUT/DELETE | `/api/admin/genres`, `/api/admin/authors` | CRUD danh mục |
| GET | `/api/admin/bgm` | Mọi bản nhạc nền, **kể cả bản đã tắt** |
| POST | `/api/admin/bgm` | Tải lên một bản nhạc nền (tên và ghi công đi kèm trong cùng multipart) |
| PUT | `/api/admin/bgm/{id}` | Sửa tên, dòng ghi công, thứ tự |
| PATCH | `/api/admin/bgm/{id}/active` | Bật/tắt một bản trong danh sách người nghe chọn |
| DELETE | `/api/admin/bgm/{id}` | Xóa hẳn, file trên đĩa cũng bị xóa |
| GET | `/api/admin/users` | Danh sách thành viên |
| PATCH | `/api/admin/users/{id}/vip` · `/enabled` · `/role` | Cấp VIP vĩnh viễn, khóa tài khoản, đổi quyền |
| GET/POST/PUT | `/api/admin/vip/plans` | Xem và cấu hình các gói VIP |
| PATCH | `/api/admin/vip/plans/{id}/active` | Bật/tắt bán một gói |
| GET | `/api/admin/vip/orders` | Mọi đơn nâng cấp (lọc theo trạng thái) |
| POST | `/api/admin/vip/orders/{orderCode}/refresh` | Đối chiếu một đơn còn treo với PayOS |
| GET/POST/PUT | `/api/admin/wallet/packages` | Xem và cấu hình các gói nạp Xu |
| POST | `/api/admin/wallet/users/{id}/adjust` | Cộng/trừ Xu tay, kèm lý do vào sổ cái |
| GET | `/api/admin/storage/audit` · `POST /repair` | Đối chiếu cơ sở dữ liệu với nơi lưu file |

---

## Kiểm thử

```bash
cd backend
./mvnw test
```

Test dùng H2 in-memory nên **không cần MySQL đang chạy**, và cũng không gọi API TTS thật (mọi phụ thuộc
bên ngoài đều được thay bằng mock).

**283 bài, 27 lớp.** Những bài quan trọng nhất không kiểm "hàm này trả về đúng chưa" mà ghim một
*bất biến* — thứ mà một lần refactor sau này có thể phá vỡ mà không ai nhận ra.

| Lớp test | Kiểm điều gì |
|---|---|
| `AccessControlServiceTest` · `ChapterAccessServiceTest` | Toàn bộ bảng phân quyền 3 mức khóa × 4 nhóm người dùng, chương có giá Xu, và `requireAccess` phải ném 403/402 **không kèm nội dung chương** |
| `PublicationJpaTest` | Bản nháp và chương hẹn giờ trả **404** với người đọc, hiện đủ với quản trị viên; **tới giờ thì chương tự hiện, không tác vụ nền nào chạy**; truyện chưa đăng giấu luôn chương đã đăng của nó; nút "chương sau" bỏ qua chương chưa đăng |
| `TtsServiceTest` | Chặn TTS với chương bị khóa (kể cả khi đã có sẵn cache), dùng lại bản READY, dọn bản FAILED rồi tạo lại, xếp hàng bất đồng bộ, và **ngân sách được hỏi ở đúng một chỗ**: sau khi kiểm quyền và tra cache, trước khi ghi |
| `ReaderTtsServiceTest` | Ngân sách của nút "Nghe bằng AI": bắt đăng nhập, hạn mức theo bậc Thành viên/VIP/Admin, trần chung xét trước hạn mức cá nhân, trần độ dài chương, đếm theo ngày giờ Việt Nam, và **trúng bản đã có thì không ghi dòng sổ nào** |
| `AiUsageJpaTest` | Sổ đếm lượt AI trên cơ sở dữ liệu thật: **dọn bản audio của một phiên không làm hạn mức mọc lại**, lượt bị từ chối được hoàn ngay nên không giữ chỗ của ai, hoàn hai lần không cộng thêm, và hai loại lượt không lẫn vào nhau |
| `ContentAudioVersioningJpaTest` | Sửa nội dung là chương lên phiên bản mới và audio cũ thành lỗi thời — trong cùng một giao dịch; một lượt dựng về đích muộn không thắng được phiên bản hiện tại |
| `RateLimitFilterTest` · `AuthServiceLoginThrottleTest` | Mỗi địa chỉ một gáo, cửa đăng nhập có gáo riêng, preflight của CORS không bao giờ bị chặn, địa chỉ đọc qua `X-Forwarded-For`; và **đang trong quãng nghỉ thì không băm mật khẩu nữa** |
| `JwtAuthenticationFilterTest` | Tài khoản bị khóa bị chặn ở mọi đường, kể cả đường công khai và đường phát audio; **token trên URL chỉ có tác dụng ở đường phát media** |
| `WordAlignerTest` | Gộp mốc thời gian từng ký tự thành từng chữ: ranh giới trùng với cách trình duyệt cắt chữ, nhiều khúc thì thời gian cộng dồn và vị trí ký tự quy về nội dung chương, ký tự bị nhà cung cấp bỏ qua không kéo phần sau lệch theo |
| `AudioOwnershipTest` | Bản audio người đọc tự dựng là của riêng họ — kể cả đường lấy mốc thời gian, vốn gần như là nội dung chương chép lại thành mảng |
| `StoryAssistantServiceTest` · `AssistantPromptTest` | Trợ lý gọi đúng cửa quyền trước khi đọc chương, hạn mức trừ trước khi gọi nhà cung cấp, lịch sử hội thoại bị cắt cả về số lượt lẫn độ dài, chương dài bị cắt theo đúng cách đã hứa |
| `WalletJpaTest` · `ChapterPurchaseJpaTest` · `PaymentOrderLedgerJpaTest` | Không bao giờ âm Xu dù hai lượt mua chạy song song, mua trùng không tính tiền hai lần, cộng hạn VIP đúng một lần cho mỗi đơn |
| `RegistrationServiceTest` | Bước một không ghi gì vào `users`, chỉ băm của mã được lưu, mật khẩu không bị băm chồng ở bước hai, hết lượt thử thì hủy lượt đăng ký, và bấm gửi lại quá sớm bị chặn |
| `PasswordResetServiceTest` | Email lạ và tài khoản bị khóa không nhận được liên kết, chỉ băm của token được lưu, liên kết cũ bị vô hiệu, và một liên kết chỉ dùng được một lần |
| `AuthServiceGoogleTest` · `AuthServiceLockedAccountTest` | Tạo tài khoản ở lần đăng nhập Google đầu tiên, ghép vào tài khoản cùng email, ưu tiên `google_id`, và tài khoản bị khóa không đăng nhập lại được bằng đường nào |
| `ContentImporterJpaTest` · `LocalMediaStorageTest` · `ByteRangeTest` | Nhập nội dung chạy lại nhiều lần an toàn, ghi file qua tên tạm rồi đổi tên nguyên tử, và phân tích header `Range` đúng cả với những dạng hiếm |
| `BackendApplicationTests` | Toàn bộ context Spring khởi động được |

Ba bài đáng đọc nhất, vì mỗi bài ghim một lời hứa mà mất nó thì không ai nhận ra ngay:

- **`kiemQuyenTruocKhiTraCache`** — cache **không** trở thành cửa sau: chương đã bị khóa thì ngay cả
  bản audio tạo sẵn từ trước cũng không được trả về.
- **`cacheReadyThiKhongTonLuot`** và **`banDaCoThiKhongTonLuot`** — nghe lại một chương đã có audio
  không tốn lượt nào, vì nó không tốn thêm đồng nào. Không có hai bài này thì một lần dời chỗ gọi ngân
  sách trong `TtsService` sẽ âm thầm biến mỗi lần mở lại chương thành một lượt bị trừ.
- **`dongSoSongSotQuaViecDonBanAudio`** — đây là bài cho chính cái lỗi đã có: hạn mức từng được đếm
  ngay trên bảng `audio_files`, mà những hàng ấy bị dọn mỗi lần mở phiên mới, nên đăng xuất rồi đăng
  nhập lại là nạp đầy hạn mức. Bài này chạy đúng kịch bản đó và đòi con số phải đứng yên.

Ở phía xác thực, `chiLuuBamCuaToken` đối chiếu chuỗi đi trong email với chuỗi nằm trong cơ sở dữ liệu
và khẳng định hai thứ đó khác nhau — cái sau là SHA-256 của cái trước. Còn `buocMotKhongTaoTaiKhoan`
giữ đúng lời hứa của cả luồng đăng ký: bấm đăng ký xong mà bảng `users` vẫn phải trống.

**Frontend chưa có bài test nào.** Nói ra vì đó là chỗ hở đáng kể nhất còn lại: phần khó nhất của sản
phẩm — dóng chữ karaoke, `WordTimeline`, bộ trộn Web Audio — nằm ở đó và hiện không có gì bảo vệ.

---

## Phạm vi đề bài

Toàn bộ mục **[BB]** (bắt buộc) và toàn bộ mục **[NC]** (nâng cao) đã hoàn thành, gồm cả hai mục 4.1
từng bỏ ngỏ: **đăng nhập bằng Google** và **quên mật khẩu qua email**.

Ngoài phạm vi đề bài, mục 11 (hướng mở rộng) đã làm thêm:

| | |
|---|---|
| **Thanh toán thật** qua PayOS | Đề bài chỉ yêu cầu Admin cấp VIP thủ công, và chức năng đó vẫn giữ nguyên bên cạnh |
| **Ví Xu và mua lẻ từng chương** | Người đọc muốn đúng một chương không còn phải chọn giữa "mua cả tháng VIP" và "không đọc" |
| **Trợ lý AI đọc truyện** | Hộp chat tóm tắt chương đang mở, chạy trên Google Gemini |
| **Phiên bản nội dung & audio** | Sửa chương thì bản audio đọc chữ cũ tự thành lỗi thời, và trang đọc được báo ngay |
| **Nháp và hẹn giờ đăng** | Ba trạng thái cho cả truyện lẫn chương, không cần tác vụ định kỳ nào |
| **Giới hạn tần suất** | Hai hàng rào: theo địa chỉ mạng và theo tài khoản |

Về mục 4.7: biểu đồ "lượt đọc/nghe theo ngày" chỉ có dữ liệu **kể từ khi bảng `view_events` được thêm
vào**, vì trước đó hệ thống không lưu mốc thời gian của từng lượt xem — chỉ có số cộng dồn. Những ngày
trước thời điểm đó hiển thị 0, không phải lỗi mà là do không có dữ liệu để dựng lại.
