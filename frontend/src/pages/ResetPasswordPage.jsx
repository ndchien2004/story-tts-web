import { useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { authApi } from "../api/endpoints";
import { Alert, Button, Field, PasswordInput } from "../components/ui";

export default function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = searchParams.get("token");

  const [form, setForm] = useState({ password: "", confirmPassword: "" });
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();

    if (form.password !== form.confirmPassword) {
      setFieldErrors({ confirmPassword: "Mật khẩu nhập lại không khớp." });
      return;
    }

    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    try {
      await authApi.resetPassword({ token, password: form.password });
      setDone(true);
    } catch (err) {
      setError(err.message);
      setFieldErrors(err.fieldErrors ?? {});
    } finally {
      setSubmitting(false);
    }
  }

  // Reached without a token: someone opened the address by hand, or the link
  // lost its query string on the way through a mail client.
  if (!token) {
    return (
      <div className="stack" style={{ gap: "var(--space-4)" }}>
        <div className="nb-section-title auth-form-title" style={{ marginBottom: 0 }}>
          <h1>Đặt lại mật khẩu</h1>
        </div>
        <Alert tone="error">
          Liên kết không hợp lệ hoặc thiếu mã đặt lại. Hãy yêu cầu một liên kết mới.
        </Alert>
        <p className="auth-alt muted">
          <Link to="/quen-mat-khau">Gửi lại liên kết</Link>
        </p>
      </div>
    );
  }

  return (
    <form className="stack" onSubmit={handleSubmit} style={{ gap: "var(--space-4)" }}>
      <div className="nb-section-title auth-form-title" style={{ marginBottom: 0 }}>
        <h1>Đặt lại mật khẩu</h1>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {done ? (
        <>
          <Alert tone="success" title="Xong">
            Mật khẩu đã được đổi. Hãy đăng nhập bằng mật khẩu mới.
          </Alert>
          <Button
            variant="primary"
            size="lg"
            block
            onClick={() => navigate("/dang-nhap", { replace: true })}
          >
            Tới trang đăng nhập
          </Button>
        </>
      ) : (
        <>
          <p className="muted">Chọn mật khẩu mới cho tài khoản của bạn.</p>

          <Field
            label="Mật khẩu mới"
            htmlFor="password"
            error={fieldErrors.password}
            hint="Tối thiểu 6 ký tự"
          >
            <PasswordInput
              id="password"
              autoComplete="new-password"
              autoFocus
              required
              error={fieldErrors.password}
              value={form.password}
              onChange={(event) => updateField("password", event.target.value)}
            />
          </Field>

          <Field
            label="Nhập lại mật khẩu mới"
            htmlFor="confirmPassword"
            error={fieldErrors.confirmPassword}
          >
            <PasswordInput
              id="confirmPassword"
              autoComplete="new-password"
              required
              error={fieldErrors.confirmPassword}
              value={form.confirmPassword}
              onChange={(event) => updateField("confirmPassword", event.target.value)}
            />
          </Field>

          <Button type="submit" variant="primary" size="lg" block loading={submitting}>
            Đổi mật khẩu
          </Button>
        </>
      )}

      <p className="auth-alt muted">
        Liên kết đã hết hạn? <Link to="/quen-mat-khau">Gửi lại</Link>
      </p>
    </form>
  );
}
