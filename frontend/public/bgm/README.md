# Nhạc nền

Thư mục này là kho nhạc nền của trang đọc. Trang web **không đi kèm bản nhạc
nào**, và đó là một lựa chọn chứ không phải một việc còn dở: nhạc có bản quyền,
mà một kho nhạc chép sẵn trong mã nguồn thì đi theo mọi bản sao của dự án này.

Trong lúc thư mục còn trống, người nghe vẫn mở được bản nhạc của chính họ từ
trang đọc ("Mở nhạc từ máy bạn"). Tệp ấy không rời khỏi máy họ và mất khi tải
lại trang.

## Thêm một bản nhạc

1. Chép tệp âm thanh vào chính thư mục này, ví dụ `mua-dem.mp3`.
2. Thêm một mục vào `manifest.json`:

```json
{
  "tracks": [
    {
      "id": "mua-dem",
      "title": "Mưa đêm",
      "file": "mua-dem.mp3",
      "credit": "Nhạc: Tên tác giả (CC BY 4.0)"
    }
  ]
}
```

Không cần dịch lại trang: manifest được đọc lúc chạy, nên trên máy chủ đã triển
khai chỉ cần thay tệp là danh sách đổi theo.

| Trường   | Bắt buộc | Ý nghĩa |
| -------- | -------- | ------- |
| `id`     | có       | Định danh, cũng là thứ được ghi nhớ khi người nghe chọn bản này |
| `title`  | có       | Tên hiển thị trong ô chọn |
| `file`   | có       | Tên tệp trong thư mục này, hoặc một URL đầy đủ |
| `credit` | không    | Ghi công tác giả, hiện dưới ô chọn khi bản này được chọn |

## Chọn tệp thế nào cho vừa

- **Định dạng**: MP3 hoặc M4A cho tương thích rộng nhất; OGG cũng chạy trên mọi
  trình duyệt hiện đại trừ Safari cũ.
- **Độ dài**: từ hai phút trở lên. Bản nhạc được lặp lại liền mạch trong lúc
  nghe, nên bản quá ngắn sẽ lộ ra chỗ nối.
- **Dung lượng**: nên dưới 5 MB mỗi bản. Nhạc nền được tải trọn và giải mã vào
  bộ nhớ để lặp không có khe hở, nên tệp càng nhỏ thì lần bấm phát đầu tiên càng
  nhanh.
- **Bản thân bản nhạc**: chọn loại không lời và không có cao trào — thứ đang
  được nghe là giọng đọc, còn đây là nền cho nó.

## Nguồn nhạc dùng được

Tìm nhạc theo giấy phép Creative Commons hoặc miền công cộng, ví dụ Free Music
Archive, ccMixter, hoặc Incompetech. Đọc kỹ điều kiện: phần lớn giấy phép CC
buộc ghi công, và đó là chỗ trường `credit` dùng tới.
