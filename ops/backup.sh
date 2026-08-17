#!/usr/bin/env bash
#
# Sao lưu cơ sở dữ liệu và danh mục audio thành một gói duy nhất, đã mã hóa.
#
# CHẠY Ở ĐÂU: máy cá nhân, hoặc GitHub Actions theo lịch (xem
# .github/workflows/backup.yml). KHÔNG chạy được trên Render — gói miễn phí
# không cho truy cập shell, và hệ tệp ở đó cũng không giữ được gì.
#
# VÌ SAO MÃ HÓA GÓI NÀY: bản kết xuất chứa email thật, mật khẩu đã băm, token
# đặt lại mật khẩu và toàn bộ lịch sử giao dịch Xu. Đây là dữ liệu duy nhất
# trong hệ thống rời khỏi vành đai của nhà cung cấp — sang máy cá nhân, sang kho
# lưu trữ, sang ổ cứng của ai đó — nên đây mới là chỗ mã hóa có tác dụng thật.
# (Mã hóa chính file audio đang chạy thì không: khóa buộc phải nằm cùng chỗ với
# ứng dụng đọc nó, nên nó không chặn được ai đọc được ứng dụng.)
#
# KHÓA: chỉ khóa CÔNG KHAI được dùng ở đây. Khóa riêng nằm ở password manager
# của bạn và một bản ngoại tuyến — KHÔNG BAO GIỜ trên máy chủ, không trong kho
# mã nguồn, không trong GitHub Secrets. Nhờ vậy máy chạy backup bị chiếm cũng
# không đọc được những gói đã sao lưu trước đó.
#
#   Sinh khóa một lần:   age-keygen -o backup-key.txt
#   Lấy khóa công khai:  grep 'public key' backup-key.txt
#
# MẤT KHÓA RIÊNG = MẤT KHẢ NĂNG ĐỌC MỌI GÓI ĐÃ SAO LƯU. Không có đường vòng.
#
# Dùng:
#   ops/backup.sh                 # kết xuất cơ sở dữ liệu + danh mục audio
#   ops/backup.sh --with-audio    # tải luôn file audio từ Cloudinary về gói
#
set -euo pipefail

OUT_DIR="${BACKUP_DIR:-./backups}"
WITH_AUDIO=0
[ "${1:-}" = "--with-audio" ] && WITH_AUDIO=1

: "${DB_HOST:?Thiếu DB_HOST}"
: "${DB_NAME:?Thiếu DB_NAME}"
: "${DB_USER:?Thiếu DB_USER}"
: "${DB_PASSWORD:?Thiếu DB_PASSWORD}"
DB_PORT="${DB_PORT:-3306}"

STAMP="$(date -u +%Y%m%dT%H%M%SZ)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mkdir -p "$OUT_DIR"
echo "==> Sao lưu $DB_NAME tại $DB_HOST ($STAMP)"

# --- Cơ sở dữ liệu -----------------------------------------------------------
#
# --single-transaction: chụp toàn bộ trong một giao dịch, nên các bảng khớp nhau
#   về mặt thời điểm và không bảng nào bị khóa trong lúc kết xuất. Đây là điều
#   kiện để ví Xu và lịch sử giao dịch không lệch nhau trong bản sao lưu.
# --set-gtid-purged=OFF: bản kết xuất phục hồi được sang một máy chủ khác, không
#   chỉ về đúng máy đã tạo ra nó.
echo "--> Kết xuất cơ sở dữ liệu"
MYSQL_PWD="$DB_PASSWORD" mysqldump \
    --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
    --single-transaction --routines --triggers --events \
    --set-gtid-purged=OFF \
    --default-character-set=utf8mb4 \
    "$DB_NAME" > "$WORK/database.sql"

# --- Danh mục audio ----------------------------------------------------------
#
# Đây là mảnh ghép khiến bản sao lưu phục hồi được thành một hệ thống nhất quán.
# Cơ sở dữ liệu nói "chương này có audio ở khóa X"; nếu lúc phục hồi khóa X
# không còn thì chương tồn tại nhưng không nghe được, và không có gì báo cho ai
# biết. Danh mục này là thứ để đối chiếu.
echo "--> Ghi danh mục audio"
MYSQL_PWD="$DB_PASSWORD" mysql \
    --host="$DB_HOST" --port="$DB_PORT" --user="$DB_USER" \
    --batch --raw --skip-column-names "$DB_NAME" <<'SQL' > "$WORK/audio-manifest.tsv"
SELECT 'AUDIO', id, file_path, file_size, content_type
FROM audio_files WHERE status = 'READY' AND file_path IS NOT NULL
UNION ALL
SELECT 'BGM', id, file_path, file_size, content_type
FROM bgm_tracks WHERE file_path IS NOT NULL;
SQL

AUDIO_COUNT="$(wc -l < "$WORK/audio-manifest.tsv" | tr -d ' ')"
echo "    $AUDIO_COUNT file audio được tham chiếu"

# --- File audio (tùy chọn) ---------------------------------------------------
#
# Mặc định KHÔNG tải: Cloudinary đã là bản lưu lâu bền, và gói sao lưu hằng ngày
# nên nhỏ để còn giữ được nhiều mốc thời gian. Chạy --with-audio khi cần một bản
# độc lập hẳn với Cloudinary — ví dụ trước khi đổi tài khoản.
if [ "$WITH_AUDIO" = "1" ]; then
    : "${CLOUDINARY_CLOUD_NAME:?Thiếu CLOUDINARY_CLOUD_NAME}"
    : "${CLOUDINARY_API_SECRET:?Thiếu CLOUDINARY_API_SECRET}"
    echo "--> Tải file audio từ Cloudinary"
    mkdir -p "$WORK/audio"
    while IFS=$'\t' read -r kind id key size ctype; do
        [ -z "${key:-}" ] && continue
        # Chữ ký đường phát: 8 ký tự đầu của SHA-1(đường dẫn + api_secret),
        # base64 an toàn cho URL. Giống hệt CloudinaryService.signedAudioUrl.
        sig="$(printf '%s%s' "$key" "$CLOUDINARY_API_SECRET" \
               | openssl dgst -sha1 -binary \
               | openssl base64 -A | tr '+/' '-_' | tr -d '=' | cut -c1-8)"
        url="https://res.cloudinary.com/${CLOUDINARY_CLOUD_NAME}/video/authenticated/s--${sig}--/${key}"
        target="$WORK/audio/$(echo "$key" | tr '/' '_')"
        if ! curl -fsS -o "$target" "$url"; then
            echo "    CẢNH BÁO: không tải được $kind #$id ($key)" >&2
        fi
    done < "$WORK/audio-manifest.tsv"
fi

# --- Gói lại và mã hóa -------------------------------------------------------
cat > "$WORK/manifest.json" <<JSON
{
  "created_at": "$STAMP",
  "database": "$DB_NAME",
  "host": "$DB_HOST",
  "audio_referenced": $AUDIO_COUNT,
  "audio_included": $([ "$WITH_AUDIO" = "1" ] && echo true || echo false)
}
JSON

ARCHIVE="$OUT_DIR/story-tts-$STAMP.tar.gz"
tar -czf "$ARCHIVE" -C "$WORK" .
echo "--> Gói: $ARCHIVE ($(du -h "$ARCHIVE" | cut -f1))"

if [ -n "${BACKUP_AGE_RECIPIENT:-}" ] && command -v age >/dev/null 2>&1; then
    age -r "$BACKUP_AGE_RECIPIENT" -o "$ARCHIVE.age" "$ARCHIVE"
    rm -f "$ARCHIVE"
    echo "==> Xong: $ARCHIVE.age (đã mã hóa)"
elif [ -n "${BACKUP_PASSPHRASE:-}" ]; then
    # Đường lùi khi chưa cài age. Yếu hơn ở chỗ khóa giải mã chính là chuỗi này,
    # nên nó phải nằm ngoài máy chạy backup y như khóa riêng của age.
    openssl enc -aes-256-cbc -pbkdf2 -iter 240000 -salt \
        -in "$ARCHIVE" -out "$ARCHIVE.enc" -pass env:BACKUP_PASSPHRASE
    rm -f "$ARCHIVE"
    echo "==> Xong: $ARCHIVE.enc (đã mã hóa)"
else
    echo "==> Xong: $ARCHIVE" >&2
    echo "!!! CHƯA MÃ HÓA. Gói này chứa email thật, mật khẩu đã băm và lịch sử" >&2
    echo "    giao dịch. Đặt BACKUP_AGE_RECIPIENT hoặc BACKUP_PASSPHRASE trước" >&2
    echo "    khi đưa nó ra khỏi máy này." >&2
fi
