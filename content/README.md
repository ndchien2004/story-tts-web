# Gói nội dung

Cách đưa truyện, chương và audio vào hệ thống từ tệp trên đĩa, thay vì gõ tay
trong khu quản trị.

## Hình dạng thư mục

```
content/
└── books/
    └── nguoi-dua-thu/          ← tên thư mục tùy ý, không ảnh hưởng gì
        ├── book.json
        ├── chapters/
        │   ├── 001.json
        │   ├── 002.json
        │   └── 010.json        ← đánh số có số 0 đứng đầu để sắp đúng thứ tự
        └── audio/
            └── 001.mp3
```

### `book.json`

```json
{
  "title": "Người đưa thư",
  "author": "Nguyễn Văn A",
  "genre": "Trinh thám",
  "description": "Một câu chuyện về những lá thư không bao giờ tới nơi.",
  "status": "ONGOING",
  "coverImage": "https://res.cloudinary.com/.../bia.jpg"
}
```

`author` và `genre` chưa có trong hệ thống thì được tạo mới.
`status`: `ONGOING` hoặc `COMPLETED`. `description`, `coverImage` bỏ trống được.

### `chapters/001.json`

```json
{
  "chapterNumber": 1,
  "title": "Lá thư đầu tiên",
  "content": "Toàn văn chương, xuống dòng bằng \\n.",
  "accessLevel": "PUBLIC",
  "coinPrice": 0,
  "audio": "audio/001.mp3",
  "audioSha256": "3b1f...c9"
}
```

| Trường | Bắt buộc | Ghi chú |
|---|---|---|
| `chapterNumber` | ✅ | Số nguyên dương. Cùng với truyện, đây là danh tính của chương. |
| `title` | ✅ | |
| `content` | ✅ | |
| `accessLevel` | | `PUBLIC` / `MEMBER` / `VIP`. Mặc định `PUBLIC`. **Chỉ có tác dụng khi tạo mới.** |
| `coinPrice` | | Mặc định 0. **Chỉ có tác dụng khi tạo mới.** |
| `audio` | | Đường dẫn tương đối trong thư mục truyện. |
| `audioSha256` | ✅ nếu có `audio` | Bắt buộc, để phát hiện file hỏng trước khi nó thành một chương im lặng. |

Tính băm:

```bash
sha256sum audio/001.mp3          # Linux
shasum -a 256 audio/001.mp3      # macOS
Get-FileHash audio\001.mp3 -Algorithm SHA256   # Windows PowerShell
```

---

## Chạy nhập

```bash
cd backend
./mvnw -DskipTests package

java -jar target/backend-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=import \
  --app.import.dir=../content
```

Chạy xong, tiến trình kết thúc. **Mã thoát khác 0 nếu có bất kỳ chương nào hỏng**,
nên đặt vào một kịch bản hay một job CI được.

> Nhập nội dung **không** chạy cùng lúc với web. Đây là một việc riêng, có
> profile riêng, vì một tệp JSON sai dấu phẩy không được phép làm cả trang web
> không khởi động được.

Trên bản chạy thật, đặt luôn các biến trỏ tới Aiven và Cloudinary — audio sẽ đi
thẳng vào nơi lưu trữ thật:

```bash
DB_HOST=... DB_PASSWORD=... DB_SSL_MODE=REQUIRED DB_CREATE_IF_MISSING=false \
STORAGE_DRIVER=cloudinary CLOUDINARY_CLOUD_NAME=... CLOUDINARY_API_SECRET=... \
java -jar target/*.jar --spring.profiles.active=import --app.import.dir=./content
```

---

## Chạy lại nhiều lần là an toàn

Đây là tính chất quan trọng nhất, vì một lượt nhập hiếm khi trót lọt ngay lần
đầu.

| Tình huống | Kết quả |
|---|---|
| Truyện đã có (trùng `title`) | Cập nhật mô tả, **không** tạo truyện thứ hai |
| Chương đã có (trùng `chapterNumber`) | Cập nhật tiêu đề và nội dung |
| Chương đã có audio | **Bỏ qua**, không tải lên lần nữa |
| Tác giả / thể loại đã có | Dùng lại, không tạo trùng |

### Những gì lượt nhập KHÔNG bao giờ đụng tới

- **`accessLevel` và `coinPrice` của chương đã tồn tại.** Đổi giá một chương đã
  có người mua bằng Xu, hay mở khóa một chương VIP, là quyết định nghiệp vụ —
  không phải hệ quả phụ của việc chạy lại một lệnh nhập nội dung. Muốn đổi thì
  đổi trong khu quản trị.
- **Lượt xem, lượt nghe, bình luận, đánh giá.** Là dữ liệu thật.
- **Quyền đã cấp cho người mua.**

### Một chương hỏng không kéo theo chương khác

Mỗi chương là một giao dịch riêng. Tệp thứ 47 sai cú pháp thì 46 chương trước nó
vẫn vào, những chương sau vẫn chạy tiếp, và cuối lượt in ra danh sách chỗ cần sửa:

```
=== Xong: truyện=1  chương: tạo=180 cập nhật=0 bỏ qua=0  audio: tải lên=12 bỏ qua=0  lỗi=1 ===
1 chỗ hỏng:
  - nguoi-dua-thu/chapters/047.json: SHA-256 của 047.mp3 không khớp (gói khai 3b1f…, thực tế a92c…)
```

---

## Thứ tự kiểm tra

```
đọc JSON → kiểm trường bắt buộc → kiểm file audio có tồn tại → đối chiếu SHA-256
                                                                      │
                                     ─────────────────────────────────┘
                                     │
                                     ▼
                        ghi chương → đẩy audio lên nơi lưu trữ → bật cờ READY
```

Audio được kiểm **trước** khi có gì được ghi. Và cờ `READY` chỉ được bật sau khi
byte đã nằm ở nơi lưu trữ — không có trạng thái trung gian nào mà chương nói
rằng nó có audio trong khi audio chưa tới nơi.

---

## Không commit audio vào Git

`.gitignore` đã chặn `*.mp3`, `*.wav`, `*.m4a`, `*.aac`, `*.ogg`, `*.flac`,
`*.opus`, `*.enc`. Gói nội dung để ở máy hoặc ở một kho lưu trữ riêng — file
audio là thứ rất khó gỡ khỏi lịch sử Git sau khi đã trở thành một commit.
