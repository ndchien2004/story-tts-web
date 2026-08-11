import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import GoogleAuth from "../components/GoogleAuth";
import useAuthProviders from "../hooks/useAuthProviders";
import { Alert, Button, Field, PasswordInput, TextInput } from "../components/ui";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const providers = useAuthProviders();

  const [form, setForm] = useState({ username: "", password: "" });
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [submitting, setSubmitting] = useState(false);

  // Send the user back to the page that bounced them here, if any.
  const redirectTo = location.state?.from?.pathname ?? "/";

  function updateField(name, value) {
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setFieldErrors({});

    try {
      await login(form);
      navigate(redirectTo, { replace: true });
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
        <h1>Đăng nhập</h1>
      </div>

      {error && <Alert tone="error">{error}</Alert>}

      <Field label="Tên đăng nhập hoặc email" htmlFor="username" error={fieldErrors.username}>
        <TextInput
          id="username"
          autoComplete="username"
          autoFocus
          required
          value={form.username}
          onChange={(event) => updateField("username", event.target.value)}
        />
      </Field>

      <Field label="Mật khẩu" htmlFor="password" error={fieldErrors.password}>
        <PasswordInput
          id="password"
          autoComplete="current-password"
          required
          error={fieldErrors.password}
          value={form.password}
          onChange={(event) => updateField("password", event.target.value)}
        />
      </Field>

      {/* Only offered when the server can actually send the email. */}
      {providers?.passwordResetEnabled && (
        <p className="auth-aside">
          <Link to="/quen-mat-khau">Quên mật khẩu?</Link>
        </p>
      )}

      <Button type="submit" variant="primary" size="lg" block loading={submitting}>
        Đăng nhập
      </Button>

      <GoogleAuth
        providers={providers}
        text="signin_with"
        redirectTo={redirectTo}
        onError={setError}
      />

      <p className="auth-alt muted">
        Chưa có tài khoản? <Link to="/dang-ky">Đăng ký ngay</Link>
      </p>
    </form>
  );
}
