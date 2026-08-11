import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import GoogleAuth from "../components/GoogleAuth";
import useAuthProviders from "../hooks/useAuthProviders";
import { Alert, Button, Field, PasswordInput, TextInput } from "../components/ui";

const EMPTY_FORM = {
  username: "",
  email: "",
  displayName: "",
  password: "",
  confirmPassword: "",
};

export default function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const providers = useAuthProviders();

  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

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
      await register({
        username: form.username,
        email: form.email,
        password: form.password,
        displayName: form.displayName || undefined,
      });
      navigate("/", { replace: true });
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
        <h1>Đăng ký</h1>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      {/* Paired across the pane, in the order the fields are filled in: the
          five-field column this used to be did not fit a laptop screen. */}
      <div className="auth-grid">
        <Field
          label="Tên đăng nhập"
          htmlFor="username"
          error={fieldErrors.username}
          hint="Chữ, số và . _ -"
        >
          <TextInput
            id="username"
            autoComplete="username"
            autoFocus
            required
            value={form.username}
            onChange={(event) => updateField("username", event.target.value)}
          />
        </Field>

        <Field label="Email" htmlFor="email" error={fieldErrors.email}>
          <TextInput
            id="email"
            type="email"
            autoComplete="email"
            required
            value={form.email}
            onChange={(event) => updateField("email", event.target.value)}
          />
        </Field>

        {/* The wrapper is the grid item, so the span goes on it rather than
            on anything inside the field. */}
        <div className="auth-grid-full">
          <Field
            label="Tên hiển thị"
            htmlFor="displayName"
            error={fieldErrors.displayName}
            hint="Không bắt buộc — để trống thì dùng tên đăng nhập"
          >
            <TextInput
              id="displayName"
              value={form.displayName}
              onChange={(event) => updateField("displayName", event.target.value)}
            />
          </Field>
        </div>

        <Field label="Mật khẩu" htmlFor="password" error={fieldErrors.password} hint="Tối thiểu 6 ký tự">
          <PasswordInput
            id="password"
            autoComplete="new-password"
            required
            error={fieldErrors.password}
            value={form.password}
            onChange={(event) => updateField("password", event.target.value)}
          />
        </Field>

        <Field
          label="Nhập lại mật khẩu"
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
      </div>

      <Button type="submit" variant="primary" size="lg" block loading={submitting}>
        Tạo tài khoản
      </Button>

      <GoogleAuth providers={providers} text="signup_with" onError={setError} />

      <p className="auth-alt muted">
        Đã có tài khoản? <Link to="/dang-nhap">Đăng nhập</Link>
      </p>
    </form>
  );
}
