package com.storytts.backend.service.storage;

/**
 * Nơi file media thực sự nằm.
 *
 * <h3>Vì sao tầng này tồn tại</h3>
 * Bản triển khai đầu tiên ghi thẳng xuống đĩa, và điều đó đúng khi ứng dụng chạy
 * trên một máy chủ có đĩa của riêng nó. Trên Render gói miễn phí thì không: hệ
 * tệp ở đó là tạm thời, bị xóa sạch mỗi lần triển khai lại, mỗi lần khởi động
 * lại, và mỗi lần dịch vụ ngủ đi sau mười lăm phút không ai truy cập. Audio dựng
 * bằng ElevenLabs — mất tiền thật cho từng bản — bốc hơi theo, trong khi hàng
 * trong cơ sở dữ liệu vẫn nói nó đang READY.
 *
 * <p>Nên nơi lưu file phải là một lựa chọn theo môi trường, không phải một giả
 * định nằm rải rác trong mã nghiệp vụ. Bản cục bộ dùng khi lập trình ở máy cá
 * nhân; bản Cloudinary dùng khi chạy thật. Không bên gọi nào biết mình đang nói
 * chuyện với bên nào.
 *
 * <h3>Khóa lưu trữ</h3>
 * Mọi method đều nhận một {@code key} — đúng chuỗi đã được trả về lúc lưu, và
 * đúng chuỗi nằm trong cột {@code file_path}. Ý nghĩa của nó do từng bản triển
 * khai tự định: bản cục bộ hiểu là tên file, bản Cloudinary hiểu là public_id.
 * Ngoài tầng này không chỗ nào được diễn giải nó.
 */
public interface MediaStorage {

    /**
     * Lưu nội dung đã nằm sẵn trong bộ nhớ và trả về khóa lưu trữ.
     *
     * <p>Đường dùng cho audio vừa dựng xong: {@code SynthesisResult} vốn đã là
     * một mảng byte, ghi ra file tạm chỉ để đọc lại là thừa.
     */
    String store(byte[] content, MediaKind kind, String extension);

    /**
     * Lưu nội dung từ một luồng và trả về khóa lưu trữ.
     *
     * <p>Đường dùng cho file admin tải lên. Bên gọi giữ trách nhiệm đóng luồng.
     */
    String store(java.io.InputStream input, MediaKind kind, String extension);

    /**
     * Mở một lát byte để phát.
     *
     * @param range khoảng byte trình phát hỏi tới, hay null để lấy trọn file
     * @throws MediaNotFoundException nếu khóa không còn ứng với dữ liệu nào
     */
    MediaSlice open(String key, MediaKind kind, ByteRange range);

    /**
     * File sau khóa này có thật không.
     *
     * <p>Cố ý <b>không</b> được gọi trên đường phát: với bản Cloudinary mỗi lần
     * hỏi là một vòng mạng, và đường phát đằng nào cũng phát hiện ra ngay ở lượt
     * {@link #open} kế tiếp. Chỗ dùng đúng của nó là các lượt rà soát chủ động.
     */
    boolean exists(String key, MediaKind kind);

    /** Xóa, và file vốn đã không còn thì không tính là lỗi. */
    void delete(String key, MediaKind kind);

    /**
     * Dọn những file dở dang mà một lượt ghi hỏng để lại.
     *
     * <p>Nằm trong giao diện thay vì là chi tiết riêng của bản cục bộ, để bên
     * gọi không phải hỏi "nơi lưu này thuộc loại nào" trước khi dọn. Nơi lưu nào
     * không sinh ra file dở dang thì trả về 0, và đó là câu trả lời đúng chứ
     * không phải một trường hợp chưa làm.
     *
     * @param olderThan chỉ dọn file dở dang cũ hơn quãng này, để không xóa nhầm
     *                  một lượt ghi đang chạy dở
     * @return số file đã dọn
     */
    int sweepTemporary(java.time.Duration olderThan);

    /** Tên nơi lưu, chỉ để ghi log lúc khởi động. */
    String describe();
}
