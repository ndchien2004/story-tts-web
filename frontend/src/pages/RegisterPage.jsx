import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import { Alert, Button, Field, TextInput } from "../components/ui";

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
    <div className="container-narrow" style={{ maxWidth: "460px" }}>
      <form className="nb-card stack" onSubmit={handleSubmit}>
        <div className="nb-section-title">
          <h1>Đăng ký</h1>
        </div>

        {error && <Alert tone="error">{error}</Alert>}

        <Field
          label="Tên đăng nhập"
          htmlFor="username"
          error={fieldErrors.username}
          hint="Chỉ gồm chữ, số và các ký tự . _ -"
        >
          <TextInput
            id="username"
            autoComplete="username"
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

        <Field
          label="Tên hiển thị"
          htmlFor="displayName"
          error={fieldErrors.displayName}
          hint="Không bắt buộc"
        >
          <TextInput
            id="displayName"
            value={form.displayName}
            onChange={(event) => updateField("displayName", event.target.value)}
          />
        </Field>

        <Field
          label="Mật khẩu"
          htmlFor="password"
          error={fieldErrors.password}
          hint="Tối thiểu 6 ký tự"
        >
          <TextInput
            id="password"
            type="password"
            autoComplete="new-password"
            required
            value={form.password}
            onChange={(event) => updateField("password", event.target.value)}
          />
        </Field>

        <Field
          label="Nhập lại mật khẩu"
          htmlFor="confirmPassword"
          error={fieldErrors.confirmPassword}
        >
          <TextInput
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            required
            value={form.confirmPassword}
            onChange={(event) => updateField("confirmPassword", event.target.value)}
          />
        </Field>

        <Button type="submit" variant="primary" size="lg" block loading={submitting}>
          Tạo tài khoản
        </Button>

        <p className="text-center muted">
          Đã có tài khoản? <Link to="/dang-nhap">Đăng nhập</Link>
        </p>
      </form>
    </div>
  );
}
