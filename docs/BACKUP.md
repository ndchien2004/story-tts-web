# Sao lưu và phục hồi

## Hiện trạng: ba lớp, bảo vệ ba thứ khác nhau

| Lớp | Ai lo | Bảo vệ trước |
|---|---|---|
| Aiven backup tự động | Aiven (có trong gói miễn phí) | Hỏng đĩa, xóa nhầm bảng |
| Cloudinary giữ file audio | Cloudinary | Mất máy chủ ứng dụng |
| `ops/backup.sh` chạy theo lịch | Chúng ta | **Mất quyền truy cập chính tài khoản Aiven** |

Lớp thứ ba tồn tại vì lý do ở cột cuối: bản sao lưu nằm trong tài khoản sắp mất
thì không phải là bản sao lưu.

---

## Chạy sao lưu

Tự động: `.github/workflows/backup.yml` chạy 02:00 giờ Việt Nam mỗi ngày, giữ
gói 30 ngày dưới dạng artifact.

Chạy tay:

```bash
export DB_HOST=... DB_NAME=... DB_USER=... DB_PASSWORD=...
export BACKUP_AGE_RECIPIENT=age1...        # khóa CÔNG KHAI
ops/backup.sh

ops/backup.sh --with-audio                 # tải luôn file audio về gói
```

**Không chạy được trên Render** — gói miễn phí không cho truy cập shell.

### Gói có gì

```
database.sql          mysqldump --single-transaction (các bảng khớp nhau về thời điểm)
audio-manifest.tsv    mọi file_path mà cơ sở dữ liệu đang trỏ tới
manifest.json         thời điểm, số lượng, có kèm audio hay không
audio/                chỉ khi chạy --with-audio
```

`audio-manifest.tsv` là mảnh khiến bản sao lưu **phục hồi được thành một hệ
thống nhất quán**, không chỉ thành một cơ sở dữ liệu. Nó ghi lại cơ sở dữ liệu
đang trỏ tới những khóa nào, tại đúng thời điểm chụp — nhờ đó lượt phục hồi
*đối chiếu* được chứ không phải hy vọng.

---

## Mã hóa: khóa nằm ở đâu

Gói sao lưu chứa email thật, mật khẩu đã băm, token đặt lại mật khẩu và toàn bộ
lịch sử giao dịch Xu. Đây là **dữ liệu duy nhất trong hệ thống rời khỏi vành đai
của nhà cung cấp**, nên đây là chỗ mã hóa có tác dụng thật.

```bash
age-keygen -o backup-key.txt        # chạy MỘT LẦN, trên máy cá nhân
grep 'public key' backup-key.txt    # → age1... , đây là thứ đem đi dùng
```

| | Ở đâu |
|---|---|
| Khóa **công khai** (`age1...`) | GitHub Secrets, kịch bản, chỗ nào cũng được |
| Khóa **riêng** (`backup-key.txt`) | Password manager **+ một bản ngoại tuyến**. Không GitHub, không máy chủ, không kho mã nguồn. |

Vì sao tách như vậy: máy chạy sao lưu chỉ cần khóa công khai để *mã hóa*. Nếu
GitHub Actions hay máy chủ bị chiếm, kẻ chiếm được vẫn không đọc được bất kỳ gói
nào đã tạo trước đó. Để khóa riêng vào GitHub Secrets là vứt bỏ đúng tính chất ấy.

> ### ⚠️ Mất khóa riêng = mất khả năng đọc mọi gói đã sao lưu
> Không có đường vòng, không có bên nào khôi phục hộ. Hãy kiểm tra bản ngoại
> tuyến còn đọc được, mỗi khi làm diễn tập phục hồi.

Chưa cài được `age` thì dùng `BACKUP_PASSPHRASE` (OpenSSL AES-256). Yếu hơn ở
chỗ chuỗi ấy vừa mã hóa vừa giải mã, nên nó phải được giữ như khóa riêng.

---

## Phục hồi

```bash
export DB_HOST=... DB_NAME=... DB_USER=... DB_PASSWORD=...
export BACKUP_AGE_IDENTITY=~/backup-key.txt
export CLOUDINARY_CLOUD_NAME=... CLOUDINARY_API_SECRET=...   # để đối chiếu audio

ops/restore.sh backups/story-tts-2026....tar.gz.age
```

Kịch bản cố ý khó chạy nhầm:

1. **Đối chiếu audio trước khi ghi.** Từng khóa trong manifest được kiểm trên
   Cloudinary. Biết trước "12 chương sẽ mất audio" là một quyết định; phát hiện
   ra sau khi đã ghi đè là một sự cố.
2. **Từ chối ghi đè** cơ sở dữ liệu đang có bảng, trừ khi thêm `--force`.
3. **Từ chối chạy tiếp** nếu có file audio thiếu, trừ khi thêm `--force`.

Sau khi phục hồi:

```
Khởi động ứng dụng → Flyway đưa lược đồ lên bản mới nhất
                   → /actuator/health trả UP
                   → mở một chương có audio và bấm play
```

Chương nào mất audio sẽ tự được đánh dấu hỏng ở lần bấm play đầu tiên, và người
đọc dựng lại được bằng nút "Nghe bằng AI". Muốn dọn một lượt:
`POST /api/admin/storage/repair`.

---

## Diễn tập phục hồi

Một bản sao lưu chưa từng được phục hồi thử thì chưa biết là có dùng được không.
Nên làm mỗi quý:

1. Tạo một database trống (Aiven cho một service miễn phí; hoặc MySQL cục bộ
   bằng `docker compose up`).
2. `ops/restore.sh` vào đó.
3. Chạy ứng dụng trỏ vào nó, mở vài chương.
4. **Kiểm tra bản ngoại tuyến của khóa riêng vẫn giải mã được** — đây là phần
   hay bị bỏ qua nhất, và cũng là phần hỏng thì mất tất cả.

---

## Những gì KHÔNG được sao lưu

| Thứ | Vì sao |
|---|---|
| File audio (mặc định) | Cloudinary đã là bản lưu lâu bền. Chạy `--with-audio` khi cần bản độc lập — ví dụ trước khi đổi tài khoản Cloudinary. |
| Biến môi trường / khóa API | Nằm trong Render dashboard và trong `.env` ở máy cá nhân. Giữ một bản trong password manager. |
| Ảnh đại diện người dùng | Cloudinary giữ. Mất thì người dùng tải lại. |
