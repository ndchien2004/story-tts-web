# Frontend — Truyện Nghe

Giao diện React (Vite) của website đọc & nghe truyện. Hướng dẫn cài đặt đầy đủ cho cả backend lẫn
frontend nằm ở **[README ở thư mục gốc](../README.md)**.

## Chạy nhanh

```bash
cp .env.example .env     # trỏ tới backend, mặc định http://localhost:8080
npm install
npm run dev              # http://localhost:5173
```

Backend phải chạy trước, nếu không mọi lời gọi API đều lỗi. Xem README gốc phần "Cài đặt và chạy".

## Lệnh

| Lệnh | Tác dụng |
|---|---|
| `npm run dev` | Chế độ phát triển, có hot reload |
| `npm run build` | Build cho production vào `dist/` |
| `npm run preview` | Xem thử bản build |
| `npm run lint` | Kiểm tra mã bằng oxlint |

## Tổ chức thư mục

```
src/
├─ api/          client Axios (tự gắn JWT) + khai báo toàn bộ endpoint
├─ components/   component dùng lại; ui.jsx chứa các thành phần cơ bản
├─ context/      Auth (người dùng + token) và Theme (sáng/tối)
├─ hooks/        useChapterAudio, useDebouncedValue
├─ pages/        một file một route; admin/ là khu quản trị
├─ styles/       CSS thuần, chia theo nhóm; tokens.css là bảng biến màu/khoảng cách
└─ brand.js      đường dẫn logo trên Cloudinary
```

Không dùng thư viện UI và không có state manager ngoài React Context. Mọi màu sắc, khoảng cách, bo góc
đều lấy từ biến CSS trong `styles/tokens.css` — đổi giao diện thì sửa ở đó, không sửa rải rác.
