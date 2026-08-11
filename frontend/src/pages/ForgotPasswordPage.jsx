import { useState } from "react";
import { Link } from "react-router-dom";
import { authApi } from "../api/endpoints";
import useAuthProviders from "../hooks/useAuthProviders";
import { Alert, Button, Field, TextInput } from "../components/ui";

export default function ForgotPasswordPage() {
  const providers = useAuthProviders();

  const [email, setEmail] = useState("");
  const [sent, setSent] = useState(null);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    try {
      const data = await authApi.forgotPassword(email.trim());
      setSent(data.message);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="stack" onSubmit={handleSubmit} style={{ gap: "var(--space-4)" }}>
      <div className="nb-section-title auth-form-title" style={{ marginBottom: 0 }}>
        <h1>Quên mật khẩu</h1>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {/*
        The reply is the same whether or not the address is registered — saying
        which would turn this page into a way of finding out who has an account
        here — so the confirmation replaces the form rather than sitting above a
        button inviting another guess.
      */}
      {sent ? (
        <Alert tone="success" title="Đã gửi yêu cầu">
          {sent}
        </Alert>
      ) : (
        <>
          <p className="muted">
            Nhập email đã dùng để đăng ký. Chúng tôi sẽ gửi một liên kết đặt lại mật khẩu, dùng được
            một lần.
          </p>

          <Field label="Email" htmlFor="email" error={fieldErrors.email}>
            <TextInput
              id="email"
              type="email"
              autoComplete="email"
              autoFocus
              required
              value={email}
              onChange={(event) => setEmail(event.target.value)}
            />
          </Field>

          <Button type="submit" variant="primary" size="lg" block loading={submitting}>
            Gửi liên kết đặt lại
          </Button>
        </>
      )}

      {/* The server may have no mail account configured, in which case nothing
          above can work and saying so beats letting the visitor try. */}
      {providers && !providers.passwordResetEnabled && (
        <Alert tone="warning">
          Máy chủ chưa cấu hình email nên chưa gửi được liên kết đặt lại. Vui lòng liên hệ quản trị
          viên.
        </Alert>
      )}

      <p className="auth-alt muted">
        Nhớ ra mật khẩu rồi? <Link to="/dang-nhap">Đăng nhập</Link>
      </p>
    </form>
  );
}
