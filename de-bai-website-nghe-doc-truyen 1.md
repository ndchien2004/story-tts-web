# ĐỀ BÀI: Website Đọc & Nghe Truyện có chức năng Chuyển Văn Bản thành Giọng Nói (Text-to-Speech)

**Người thực hiện:** 1 người
**Thời gian dự kiến:** 5 tuần (đã tăng từ 4 lên 5 tuần do bổ sung frontend React và vài chức năng nâng cao)
**Công nghệ chính:** Java (Spring Boot, REST API) + React (frontend)

---

## 1. Bối cảnh và mục tiêu

Xây dựng một website cho phép người dùng đọc truyện dưới dạng văn bản, nghe truyện dưới dạng audio có sẵn, và đặc biệt là chuyển đổi tự động một chương truyện từ dạng chữ sang audio bằng công nghệ Text-to-Speech (TTS). Admin có quyền khóa từng chương truyện (yêu cầu đăng nhập hoặc VIP) để kiểm soát nội dung nào được đọc/nghe tự do. Đề tài giúp người thực hiện luyện tập toàn diện một dự án web: backend Java (Spring Boot, REST API, bảo mật/phân quyền), frontend React, xử lý file, tích hợp API bên thứ ba.

## 2. Mô tả bài toán

Hệ thống gồm bốn nhóm người dùng theo quyền: Khách, Thành viên, Thành viên VIP, và Quản trị viên (Admin) — xem chi tiết ở mục 3. Admin quản lý nội dung truyện (thêm/sửa/xóa truyện, chương, thể loại, audio) và quyết định mức khóa của từng chương. Người dùng duyệt danh sách truyện, đọc nội dung theo chương (nếu đủ quyền), nghe audio nếu có sẵn, hoặc yêu cầu hệ thống tự chuyển một chương thành audio để nghe (nếu truyện đó chưa có bản audio do admin upload).

## 3. Đối tượng sử dụng

- **Khách (chưa đăng nhập):** xem danh sách truyện, chỉ đọc/nghe được các chương ở mức **Công khai**.
- **Thành viên (đã đăng ký, đăng nhập):** ngoài quyền của Khách, đọc/nghe được thêm các chương ở mức **Yêu cầu đăng nhập**; lưu tiến độ đọc/nghe, đánh dấu yêu thích, bình luận/đánh giá.
- **Thành viên VIP:** là Thành viên được Admin cấp thêm quyền VIP (thao tác thủ công, không tích hợp thanh toán thật trong phạm vi đề tài này); đọc/nghe được cả các chương ở mức **VIP**.
- **Quản trị viên:** toàn quyền quản lý truyện, chương, thể loại, tác giả; đặt mức khóa cho từng chương; cấp/thu hồi quyền VIP cho thành viên.

Mỗi chương truyện có một **mức truy cập** do Admin quy định: `Công khai` (PUBLIC), `Yêu cầu đăng nhập` (MEMBER), hoặc `VIP`. Đây chính là chức năng "khóa chương theo yêu cầu Admin" của đề tài.

## 4. Yêu cầu chức năng

Đánh dấu **[BB]** = Bắt buộc (phải hoàn thành), **[NC]** = Nâng cao (làm nếu còn thời gian).

### 4.1. Quản lý người dùng
- [BB] Đăng ký, đăng nhập, đăng xuất (mã hóa mật khẩu, không lưu plaintext).
- [BB] Phân quyền Member / Admin (Spring Security).
- [BB] Xác thực bằng JWT (JSON Web Token): vì frontend (React) tách rời backend, sau khi đăng nhập backend trả về token, React đính kèm token vào header `Authorization` ở mỗi request tới API.
- [BB] Admin có thể gán/thu hồi trạng thái **VIP** cho một Thành viên (thao tác thủ công trong trang quản trị, không tích hợp cổng thanh toán thật trong phạm vi đề tài này).
- [NC] Đăng nhập bằng Google (OAuth2).
- [NC] Quên mật khẩu qua email.

### 4.2. Quản lý truyện (phía Admin)
- [BB] CRUD truyện: tên, tác giả, thể loại, ảnh bìa, mô tả, trạng thái (đang ra / hoàn thành).
- [BB] CRUD chương: tiêu đề chương, nội dung văn bản, số thứ tự, ngày đăng.
- [BB] **Khóa chương:** khi tạo/sửa một chương, Admin chọn mức truy cập cho chương đó — `Công khai` / `Yêu cầu đăng nhập` / `VIP`. Đây là cơ chế khóa chương chính của đề tài, do Admin toàn quyền quyết định trên từng chương.
- [BB] Upload file audio có sẵn cho một chương (nếu có bản audio thu âm sẵn, không cần TTS).
- [NC] Quản lý thể loại, tác giả riêng (CRUD).

### 4.3. Đọc truyện (phía Reader)
- [BB] Danh sách truyện có phân trang, lọc theo thể loại, sắp xếp (mới nhất, phổ biến nhất).
- [BB] Trang chi tiết truyện: thông tin + danh sách chương (chương bị khóa hiển thị icon khóa kèm mức yêu cầu, ví dụ "🔒 Yêu cầu VIP").
- [BB] Trang đọc nội dung chương (giao diện đọc rõ ràng, chuyển chương trước/sau).
- [BB] Kiểm tra quyền truy cập trước khi trả nội dung chương: nếu người dùng hiện tại (Khách/Member/VIP) không đủ quyền, API trả lỗi 403 và frontend hiển thị thông báo yêu cầu đăng nhập hoặc nâng cấp VIP, **không** trả nội dung chương về client.
- [BB] Tìm kiếm truyện theo tên/tác giả.
- [NC] Lưu lại chương đang đọc để vào lại đọc tiếp (reading progress).
- [NC] Chế độ đọc tối (dark mode), tùy chỉnh cỡ chữ.
- [NC] Gợi ý truyện tương tự dựa trên thể loại người dùng đã đọc/nghe: lấy thể loại của các truyện có trong lịch sử đọc/nghe gần nhất của người dùng, gợi ý thêm các truyện khác cùng thể loại mà họ chưa đọc (hiển thị ở trang chủ hoặc cuối trang chi tiết truyện).

### 4.4. Nghe audio truyện
- [BB] Nếu chương đã có file audio (do admin upload), hiển thị trình phát audio (HTML5 `<audio>`), hỗ trợ tua, phát/dừng.
- [BB] Áp dụng đúng quy tắc khóa chương ở mục 4.3 cho cả audio: chương bị khóa thì API audio cũng phải trả lỗi 403, không cho tải/stream file.
- [NC] Lưu vị trí đang nghe (giây thứ bao nhiêu) để nghe tiếp lần sau.
- [NC] **Chế độ "nghe liên tục":** khi audio của chương hiện tại phát xong, tự động chuyển sang chương kế tiếp — nếu chương đó người dùng có quyền nghe và đã có audio (upload hoặc TTS) thì phát ngay; nếu chưa có audio, tự gọi TTS để tạo trước rồi mới phát tiếp.

### 4.5. Chuyển văn bản sang audio (Text-to-Speech) — chức năng trọng tâm
- [BB] Nếu chương chưa có audio sẵn, cho phép người dùng bấm "Nghe bằng AI" → hệ thống gọi API TTS (đề xuất FPT.AI Text-to-Speech, có giọng đọc tiếng Việt) để chuyển nội dung chương thành file audio.
- [BB] Chỉ gọi TTS cho chương mà người dùng hiện tại có quyền truy cập (kiểm tra `access_level` trước khi cho phép tạo/nghe audio bằng TTS — tránh việc người dùng dùng TTS để lách qua chương bị khóa).
- [BB] Lưu (cache) file audio đã tạo vào server, để những lần nghe sau không cần gọi lại API (tránh tốn phí và thời gian chờ).
- [NC] Cho chọn giọng đọc (nam/nữ), tốc độ đọc.
- [NC] Xử lý bất đồng bộ (async) cho chương dài: hiển thị trạng thái "Đang tạo audio…" rồi tự cập nhật khi xong, thay vì bắt người dùng chờ đồng bộ.

### 4.6. Tương tác người dùng
- [NC] Đánh giá (sao) và bình luận theo truyện.
- [NC] Đánh dấu truyện yêu thích, lịch sử đọc/nghe gần đây.

### 4.7. Quản trị / thống kê
- [NC] Trang thống kê cho Admin, hiển thị bằng biểu đồ dùng **Chart.js** (hoặc `react-chartjs-2` nếu vẽ trực tiếp trong React): số lượt đọc/nghe theo ngày, top truyện được xem/nghe nhiều nhất, tỷ lệ chương Công khai/Member/VIP.

## 5. Yêu cầu phi chức năng

- Giao diện responsive, dùng tốt trên cả máy tính và điện thoại.
- Thời gian tải trang danh sách/chi tiết truyện dưới 2 giây với dữ liệu mẫu (~50-100 truyện).
- Audio phải hỗ trợ streaming (tua được ngay, không cần tải hết file).
- Mật khẩu và các khóa API (API key của dịch vụ TTS) không được lưu cứng (hardcode) trong code — dùng file cấu hình `application.properties`/`.env` và loại trừ khỏi Git.
- Có xử lý lỗi hợp lý (ví dụ: API TTS lỗi/hết quota → thông báo rõ cho người dùng, không crash server).

## 6. Công nghệ đề xuất

| Thành phần | Công nghệ đề xuất | Ghi chú |
|---|---|---|
| Ngôn ngữ / Backend | Java 17+, Spring Boot 3 (Spring Web/REST, Spring Data JPA, Spring Security + JWT) | Backend đóng vai trò REST API thuần túy (`@RestController`, DTO), không render HTML |
| Cơ sở dữ liệu | MySQL hoặc PostgreSQL | MySQL phổ biến, dễ tìm tài liệu tiếng Việt |
| Frontend | React (khởi tạo bằng Vite) + React Router + Axios | Gọi REST API của Spring Boot; cần cấu hình CORS ở backend để React (chạy port khác) gọi được API |
| Biểu đồ thống kê | Chart.js hoặc `react-chartjs-2` | Dùng cho trang thống kê Admin (mục 4.7) |
| Chuyển văn bản → giọng nói (TTS) | FPT.AI Text-to-Speech API (giọng Việt, gọi qua REST/HTTP) | Phù hợp nội dung tiếng Việt. Phương án thay thế: Google Cloud Text-to-Speech (có thư viện Java client chính thức), Amazon Polly. |
| Lưu trữ file audio | Thư mục local trên server (ví dụ `/uploads/audio`) cho giai đoạn học tập | Có thể nâng cấp lên AWS S3/MinIO nếu muốn mô phỏng production |
| Build tool | Maven hoặc Gradle (backend), npm/pnpm (frontend React) | Maven dễ tiếp cận hơn cho người mới |
| Kiểm thử | JUnit 5, Mockito | Viết test cho các Service quan trọng (đặc biệt luồng gọi TTS và kiểm tra quyền truy cập chương) |
| Quản lý mã nguồn | Git + GitHub/GitLab | Commit theo từng chức năng nhỏ, dễ theo dõi tiến độ; có thể tách 2 repo (backend/frontend) hoặc 1 repo dạng monorepo |
| Triển khai (tuỳ chọn) | Backend: Railway/Render; Frontend: Vercel/Netlify | Không bắt buộc nếu mục tiêu chỉ là học tập |

## 7. Kiến trúc hệ thống (tổng quan)

```
[React SPA (trình duyệt)]
        |  gọi REST API bằng Axios, kèm JWT trong header Authorization
        v
[Spring Boot REST Controller] --> [Access Control (kiểm tra access_level chương + role/VIP user)]
        |
        v
[Service Layer] --> [Repository (Spring Data JPA)] --> [MySQL/PostgreSQL]
        |
        v
[TTS Service] --> gọi API FPT.AI TTS qua HTTP --> nhận file audio --> lưu vào /uploads/audio --> lưu đường dẫn vào DB (bảng audio_files)
```

Luồng kiểm tra khóa chương: mọi request lấy nội dung chương, lấy audio, hoặc gọi TTS đều đi qua một lớp kiểm tra quyền chung (ví dụ viết dưới dạng Spring Security method-level `@PreAuthorize` hoặc một filter/aspect riêng) — so sánh `access_level` của chương (PUBLIC/MEMBER/VIP) với trạng thái người dùng hiện tại (khách/đã đăng nhập/VIP, lấy từ JWT). Nếu không đủ quyền, trả HTTP 403 kèm thông báo, React hiển thị màn hình yêu cầu đăng nhập/nâng cấp VIP thay vì nội dung chương.

Luồng TTS chi tiết: Người dùng bấm "Nghe bằng AI" trên một chương → Controller kiểm tra quyền truy cập chương → nếu đủ quyền, kiểm tra DB xem chương này đã có audio (do TTS tạo trước đó) chưa → nếu chưa, gọi TTS Service → TTS Service gửi nội dung chương tới API FPT.AI → nhận về file âm thanh → lưu file vào thư mục uploads và ghi bản ghi mới vào bảng `audio_files` (đánh dấu `source = 'TTS'`) → trả đường dẫn file cho Controller → React phát audio.

## 8. Thiết kế cơ sở dữ liệu sơ bộ (các bảng chính)

- `users` (id, username, email, password_hash, role [`ADMIN`/`MEMBER`], is_vip [boolean, mặc định false], created_at)
- `genres` (id, name)
- `authors` (id, name, bio)
- `stories` (id, title, author_id, genre_id, cover_image, description, status, created_at)
- `chapters` (id, story_id, title, content, chapter_number, **access_level** [`PUBLIC`/`MEMBER`/`VIP`], created_at)
- `audio_files` (id, chapter_id, file_path, source [`UPLOAD`/`TTS`], duration, created_at)
- `reading_progress` (id, user_id, chapter_id, last_position, updated_at) — dùng cả cho tiến độ đọc/nghe và làm dữ liệu đầu vào để gợi ý truyện tương tự (join `chapters.story_id` → `stories.genre_id`)
- `favorites` (id, user_id, story_id)
- `ratings_comments` (id, user_id, story_id, rating, comment, created_at) — module nâng cao

Quy tắc kiểm tra quyền dựa trên hai cột: `chapters.access_level` (Admin đặt) và `users.role` + `users.is_vip` (xác định qua JWT của người đang request) — chương `PUBLIC` ai cũng đọc được, `MEMBER` cần `role != GUEST` (tức đã đăng nhập), `VIP` cần `is_vip = true`.

## 9. Lộ trình thực hiện gợi ý (5 tuần, 1 người)

**Tuần 1 — Nền tảng backend:** Thiết kế CSDL (bao gồm `access_level` ở `chapters` và `is_vip` ở `users`), khởi tạo project Spring Boot, cấu hình Spring Security + JWT (đăng ký/đăng nhập/phân quyền Member/Admin), CRUD truyện + chương phía Admin (REST endpoint), gồm chức năng đặt mức khóa cho chương.

**Tuần 2 — Frontend cơ bản + luồng đọc:** Khởi tạo React (Vite), cấu hình gọi API + lưu JWT, trang đăng nhập/đăng ký, danh sách truyện (tìm kiếm/lọc/phân trang), trang chi tiết truyện, trang đọc chương có xử lý hiển thị chương bị khóa (403 → màn hình yêu cầu đăng nhập/VIP).

**Tuần 3 — Audio & TTS:** Chức năng upload audio có sẵn cho chương, trình phát audio HTML5 trong React, tích hợp API FPT.AI TTS (thử nghiệm bằng Postman trước, sau đó code TTS Service trong Spring Boot), lưu cache file đã tạo, áp dụng kiểm tra quyền truy cập trước khi phát/tạo audio, làm chế độ "nghe liên tục".

**Tuần 4 — Tính năng nâng cao:** Gợi ý truyện tương tự theo thể loại đã đọc/nghe, trang thống kê Admin bằng Chart.js, và 1 chức năng tương tác nếu còn thời gian (yêu thích hoặc đánh giá/bình luận).

**Tuần 5 — Hoàn thiện:** Viết test cho các Service chính (đặc biệt luồng kiểm tra quyền và luồng TTS), sửa lỗi, tối ưu giao diện React, viết báo cáo/README mô tả kiến trúc và hướng dẫn cài đặt, quay video demo hoặc chuẩn bị thuyết trình.

> Ghi chú: nếu sau 2-3 tuần thấy tiến độ chậm so với kế hoạch, nên cắt trước các mục [NC] (gợi ý truyện, chế độ nghe liên tục, thống kê Chart.js) để đảm bảo hoàn thành đầy đủ các mục [BB], trong đó khóa chương và TTS là hai chức năng trọng tâm không nên cắt.

## 10. Tiêu chí tự đánh giá khi hoàn thành

- Toàn bộ chức năng đánh dấu [BB] hoạt động ổn định, không lỗi khi thao tác thông thường.
- Chức năng khóa chương hoạt động đúng cho cả 3 mức (Công khai/Yêu cầu đăng nhập/VIP) — nên kiểm thử bằng 3 tài khoản khác nhau (khách, member thường, member VIP) để xác nhận mỗi loại chỉ thấy đúng nội dung được phép.
- Chức năng TTS hoạt động thật với API bên ngoài (không phải giả lập/hardcode), có cơ chế cache hợp lý, và tôn trọng quy tắc khóa chương (không tạo/nghe audio TTS cho chương người dùng không có quyền).
- Code có phân lớp rõ ràng (Controller / Service / Repository / Entity), không viết logic nghiệp vụ trong Controller; logic kiểm tra quyền truy cập chương nên tách thành một lớp/aspect dùng chung, không lặp code kiểm tra ở nhiều nơi.
- Có tài liệu README: mô tả chức năng, hướng dẫn cài đặt backend + frontend, cấu hình API key TTS.
- Đã đẩy code lên GitHub với lịch sử commit hợp lý (không phải 1 commit duy nhất "done").

## 11. Hướng mở rộng nếu còn thời gian sau 5 tuần

- Thanh toán thật để mua VIP (tích hợp cổng thanh toán như VNPay/Momo/Stripe) thay cho việc Admin cấp VIP thủ công.
- Đăng nhập bằng Google (OAuth2), quên mật khẩu qua email.
- Tìm kiếm nâng cao (Elasticsearch) khi số lượng truyện lớn.
- Đóng gói bằng Docker (backend + frontend + DB) để dễ triển khai và demo.
- Phát triển thêm ứng dụng mobile (React Native) dùng lại REST API đã có.
