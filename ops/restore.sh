#!/usr/bin/env bash
#
# Phục hồi từ một gói do ops/backup.sh tạo ra.
#
# Kịch bản này ghi đè một cơ sở dữ liệu. Nó cố ý khó chạy nhầm: phải chỉ đích
# danh gói, và nếu cơ sở dữ liệu đích đang có dữ liệu thì phải thêm --force.
#
# THỨ TỰ CÓ CHỦ Ý: đối chiếu danh mục audio TRƯỚC khi ghi cơ sở dữ liệu. Biết
# trước "sẽ có 12 chương mất audio" là một quyết định; phát hiện ra sau khi đã
# ghi đè là một sự cố.
#
# Dùng:
#   ops/restore.sh backups/story-tts-2026....tar.gz.age
#   ops/restore.sh --force backups/....tar.gz     # ghi đè DB đang có dữ liệu
#
set -euo pipefail

FORCE=0
if [ "${1:-}" = "--force" ]; then
    FORCE=1
    shift
fi

ARCHIVE="${1:?Dùng: ops/restore.sh [--force] <gói sao lưu>}"
[ -f "$ARCHIVE" ] || { echo "Không thấy gói: $ARCHIVE" >&2; exit 1; }

: "${DB_HOST:?Thiếu DB_HOST}"
: "${DB_NAME:?Thiếu DB_NAME}"
: "${DB_USER:?Thiếu DB_USER}"
: "${DB_PASSWORD:?Thiếu DB_PASSWORD}"
DB_PORT="${DB_PORT:-3306}"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

mysql_do() {
    MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" \
        --user="$DB_USER" --default-character-set=utf8mb4 "$@"
}

# --- Giải mã và mở gói -------------------------------------------------------
echo "==> Mở gói $ARCHIVE"
case "$ARCHIVE" in
    *.age)
        : "${BACKUP_AGE_IDENTITY:?Thiếu BACKUP_AGE_IDENTITY (đường dẫn tới khóa riêng)}"
        age -d -i "$BACKUP_AGE_IDENTITY" "$ARCHIVE" > "$WORK/archive.tar.gz"
        ;;
    *.enc)
        : "${BACKUP_PASSPHRASE:?Thiếu BACKUP_PASSPHRASE}"
        openssl enc -d -aes-256-cbc -pbkdf2 -iter 240000 \
            -in "$ARCHIVE" -out "$WORK/archive.tar.gz" -pass env:BACKUP_PASSPHRASE
        ;;
    *)
        cp "$ARCHIVE" "$WORK/archive.tar.gz"
        ;;
esac

mkdir -p "$WORK/unpacked"
tar -xzf "$WORK/archive.tar.gz" -C "$WORK/unpacked"
[ -f "$WORK/unpacked/database.sql" ] || { echo "Gói thiếu database.sql" >&2; exit 1; }

echo "--> Nội dung gói:"
sed 's/^/    /' "$WORK/unpacked/manifest.json" 2>/dev/null || true

# --- Chốt an toàn ------------------------------------------------------------
EXISTING="$(mysql_do --batch --skip-column-names -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME';" || echo 0)"

if [ "$EXISTING" -gt 0 ] && [ "$FORCE" != "1" ]; then
    cat >&2 <<EOF

DỪNG LẠI. Cơ sở dữ liệu '$DB_NAME' tại $DB_HOST đang có $EXISTING bảng.

Chạy tiếp sẽ ghi đè chúng. Nếu đây đúng là điều bạn muốn:

  1. Sao lưu hiện trạng trước:  ops/backup.sh
  2. Rồi chạy lại với --force

EOF
    exit 1
fi

# --- Đối chiếu audio TRƯỚC khi ghi -------------------------------------------
#
# Phần quan trọng nhất của kịch bản này. Cơ sở dữ liệu sắp được ghi vào nói rằng
# một số chương có audio ở những khóa nhất định. Nếu nơi lưu trữ không còn giữ
# chúng thì sau khi phục hồi, chương ấy tồn tại nhưng bấm play không ra gì.
if [ -f "$WORK/unpacked/audio-manifest.tsv" ]; then
    TOTAL="$(wc -l < "$WORK/unpacked/audio-manifest.tsv" | tr -d ' ')"
    echo "--> Gói tham chiếu $TOTAL file audio"

    if [ -d "$WORK/unpacked/audio" ]; then
        echo "    Gói có kèm file audio — phục hồi sẽ độc lập với Cloudinary."
        echo "    Tải chúng lên lại bằng khu quản trị, hoặc giữ làm bản lưu."
    elif [ -n "${CLOUDINARY_CLOUD_NAME:-}" ] && [ -n "${CLOUDINARY_API_SECRET:-}" ]; then
        echo "    Đang kiểm tra từng khóa trên Cloudinary…"
        MISSING=0
        while IFS=$'\t' read -r kind id key size ctype; do
            [ -z "${key:-}" ] && continue
            sig="$(printf '%s%s' "$key" "$CLOUDINARY_API_SECRET" \
                   | openssl dgst -sha1 -binary \
                   | openssl base64 -A | tr '+/' '-_' | tr -d '=' | cut -c1-8)"
            url="https://res.cloudinary.com/${CLOUDINARY_CLOUD_NAME}/video/authenticated/s--${sig}--/${key}"
            if ! curl -fsS -r 0-0 -o /dev/null "$url" 2>/dev/null; then
                echo "    THIẾU: $kind #$id ($key)" >&2
                MISSING=$((MISSING + 1))
            fi
        done < "$WORK/unpacked/audio-manifest.tsv"

        if [ "$MISSING" -gt 0 ]; then
            echo "" >&2
            echo "    $MISSING/$TOTAL file audio không còn trên Cloudinary." >&2
            echo "    Những chương ấy sẽ tồn tại nhưng không nghe được. Ứng dụng" >&2
            echo "    tự đánh dấu chúng là hỏng ở lần bấm play đầu tiên, nên" >&2
            echo "    người đọc dựng lại được bằng nút 'Nghe bằng AI'." >&2
            if [ "$FORCE" != "1" ]; then
                echo "" >&2
                echo "    Thêm --force nếu vẫn muốn phục hồi." >&2
                exit 1
            fi
        else
            echo "    Tất cả $TOTAL file audio đều còn. Phục hồi sẽ nhất quán."
        fi
    else
        echo "    Bỏ qua bước đối chiếu (thiếu CLOUDINARY_CLOUD_NAME/API_SECRET)." >&2
    fi
fi

# --- Ghi cơ sở dữ liệu -------------------------------------------------------
echo "==> Phục hồi cơ sở dữ liệu vào $DB_NAME"
MYSQL_PWD="$DB_PASSWORD" mysql --host="$DB_HOST" --port="$DB_PORT" \
    --user="$DB_USER" --default-character-set=utf8mb4 \
    "$DB_NAME" < "$WORK/unpacked/database.sql"

echo ""
echo "==> Xong. Việc còn lại:"
echo "    1. Khởi động ứng dụng — Flyway tự đưa lược đồ lên bản mới nhất."
echo "    2. Kiểm tra /actuator/health."
echo "    3. Mở một chương có audio và bấm play."
