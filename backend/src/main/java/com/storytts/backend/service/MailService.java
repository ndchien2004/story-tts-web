package com.storytts.backend.service;

import com.storytts.backend.config.AppMailProperties;
import com.storytts.backend.domain.User;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.mail.MailProperties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/** Gửi email hệ thống. Hiện chỉ có một loại thư: liên kết đặt lại mật khẩu. */
@Service
@RequiredArgsConstructor
@Slf4j
public class MailService {

    private final JavaMailSender mailSender;
    private final MailProperties smtpProperties;
    private final AppMailProperties properties;

    /** Thiếu tài khoản SMTP thì chức năng quên mật khẩu tự tắt, không lỗi 500. */
    public boolean isConfigured() {
        return notBlank(smtpProperties.getHost()) && notBlank(smtpProperties.getUsername());
    }

    /**
     * Gửi liên kết đặt lại mật khẩu.
     *
     * <p>Chạy nền: gửi SMTP mất vài giây, mà thời gian trả lời của endpoint quên
     * mật khẩu phải như nhau dù email có tồn tại hay không — chờ gửi xong mới
     * trả lời chính là cách để người ngoài dò ra địa chỉ nào đã đăng ký.
     */
    @Async
    public void sendPasswordReset(User user, String resetLink, int ttlMinutes) {
        String subject = "Đặt lại mật khẩu tài khoản Truyện Nghe";
        String body = """
                <div style="font-family:Segoe UI,Arial,sans-serif;font-size:15px;color:#1c1c21;line-height:1.6">
                  <p>Chào %s,</p>
                  <p>Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản <b>%s</b>.
                     Bấm nút bên dưới để chọn mật khẩu mới:</p>
                  <p style="margin:24px 0">
                    <a href="%s" style="background:#1c1c21;color:#ffffff;padding:12px 22px;
                       border-radius:6px;text-decoration:none;font-weight:600">Đặt lại mật khẩu</a>
                  </p>
                  <p>Liên kết chỉ dùng được một lần và sẽ hết hạn sau %d phút.</p>
                  <p style="color:#6e6e78">Nếu bạn không yêu cầu đổi mật khẩu, hãy bỏ qua email này —
                     mật khẩu hiện tại vẫn giữ nguyên.</p>
                  <p style="color:#6e6e78;font-size:13px">Không mở được nút? Dán liên kết sau vào trình duyệt:<br>%s</p>
                </div>
                """.formatted(escape(user.getDisplayName()), escape(user.getUsername()),
                resetLink, ttlMinutes, resetLink);

        send(user.getEmail(), subject, body);
    }

    private void send(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            setFrom(helper);
            mailSender.send(message);
            log.info("Đã gửi email '{}' tới {}", subject, to);
        } catch (Exception ex) {
            // Chạy nền nên không còn ai bắt ngoại lệ này; ghi log để còn lần ra
            // được khi người dùng báo "không nhận được thư".
            log.error("Không gửi được email tới {}: {}", to, ex.getMessage());
        }
    }

    /** Để trống {@code app.mail.from} thì lấy luôn tài khoản SMTP làm người gửi. */
    private void setFrom(MimeMessageHelper helper) throws MessagingException, UnsupportedEncodingException {
        String from = notBlank(properties.from()) ? properties.from() : smtpProperties.getUsername();
        if (notBlank(properties.fromName())) {
            helper.setFrom(from, properties.fromName());
        } else {
            helper.setFrom(from);
        }
    }

    /** Tên hiển thị do người dùng tự đặt, nên phải khử trước khi ghép vào HTML. */
    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
