import { useEffect, useState } from "react";
import { authApi } from "../api/endpoints";
import { useAuth } from "../context/auth-context";
import { Alert, Button, Field, TextInput } from "./ui";

const CODE_LENGTH = 6;

/** Matches the cooldown the server enforces, so the button unlocks when it will. */
const RESEND_COOLDOWN_SECONDS = 60;

/**
 * Second step of signing up: the code that turns a pending registration into
 * an account.
 *
 * Nothing has been written to the database yet at this point — that is the
 * whole reason the step exists — so leaving the page costs the visitor their
 * registration and nothing else.
 *
 * @param email    where the code went; also the key the server looks it up by
 * @param minutes  how long the code lasts, as the server reported it
 * @param onBack   returns to the form with its values intact
 */
export default function RegistrationCodeForm({ email, minutes, onBack, onVerified }) {
  const { verifyRegistration } = useAuth();

  const [code, setCode] = useState("");
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);
  const [cooldown, setCooldown] = useState(RESEND_COOLDOWN_SECONDS);

  useEffect(() => {
    if (cooldown <= 0) return undefined;
    const timer = setTimeout(() => setCooldown((value) => value - 1), 1000);
    return () => clearTimeout(timer);
  }, [cooldown]);

  async function handleSubmit(event) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setNotice(null);

    try {
      await verifyRegistration({ email, code });
      onVerified();
    } catch (err) {
      setError(err.message);
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setResending(true);
    setError(null);
    setNotice(null);

    try {
      const data = await authApi.resendRegistrationCode(email);
      setNotice(data.message);
      setCode("");
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch (err) {
      setError(err.message);
    } finally {
      setResending(false);
    }
  }

  return (
    <form className="stack" onSubmit={handleSubmit} style={{ gap: "var(--space-4)" }}>
      <div className="nb-section-title auth-form-title" style={{ marginBottom: 0 }}>
        <h1>Xác thực email</h1>
      </div>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <p className="muted">
        Chúng tôi vừa gửi mã gồm {CODE_LENGTH} chữ số tới <strong>{email}</strong>. Nhập mã để hoàn
        tất đăng ký — tài khoản chỉ được tạo sau bước này. Mã có hiệu lực trong {minutes} phút.
      </p>

      <Field label="Mã xác thực" htmlFor="code">
        <TextInput
          id="code"
          className="nb-input otp-input"
          inputMode="numeric"
          autoComplete="one-time-code"
          autoFocus
          required
          maxLength={CODE_LENGTH}
          value={code}
          // Dán mã từ hòm thư thường kéo theo khoảng trắng, và bàn phím điện
          // thoại vẫn gõ được chữ; lọc ở đây thì ô luôn đúng dạng máy chủ chờ.
          onChange={(event) => setCode(event.target.value.replace(/\D/g, ""))}
        />
      </Field>

      <Button
        type="submit"
        variant="primary"
        size="lg"
        block
        loading={submitting}
        disabled={code.length < CODE_LENGTH}
      >
        Xác nhận và tạo tài khoản
      </Button>

      <Button block loading={resending} disabled={cooldown > 0} onClick={handleResend}>
        {cooldown > 0 ? `Gửi lại mã sau ${cooldown}s` : "Gửi lại mã"}
      </Button>

      <p className="auth-alt muted">
        Sai địa chỉ email?{" "}
        <button type="button" className="link-button" onClick={onBack}>
          Quay lại sửa
        </button>
      </p>
    </form>
  );
}
