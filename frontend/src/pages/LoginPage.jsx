import { useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import { Alert, Button, Field, TextInput } from "../components/ui";

export default function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

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
    <div className="container-narrow" style={{ maxWidth: "460px" }}>
      <form className="nb-card stack" onSubmit={handleSubmit}>
        <div className="nb-section-title">
          <h1>Đăng nhập</h1>
        </div>

        {error && <Alert tone="error">{error}</Alert>}

        <Field label="Tên đăng nhập hoặc email" htmlFor="username" error={fieldErrors.username}>
          <TextInput
            id="username"
            autoComplete="username"
            required
            value={form.username}
            onChange={(event) => updateField("username", event.target.value)}
          />
        </Field>

        <Field label="Mật khẩu" htmlFor="password" error={fieldErrors.password}>
          <TextInput
            id="password"
            type="password"
            autoComplete="current-password"
            required
            value={form.password}
            onChange={(event) => updateField("password", event.target.value)}
          />
        </Field>

        <Button type="submit" variant="primary" size="lg" block loading={submitting}>
          Đăng nhập
        </Button>

        <p className="text-center muted">
          Chưa có tài khoản? <Link to="/dang-ky">Đăng ký ngay</Link>
        </p>
      </form>
    </div>
  );
}
