import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/auth-context";
import GoogleSignInButton from "./GoogleSignInButton";

/**
 * The "or continue with Google" half of the sign-in and sign-up forms.
 *
 * Both pages need the same divider, the same button and the same handling of
 * what comes back, and the only thing that differs is the wording on the button
 * and where the visitor lands afterwards.
 *
 * Renders nothing at all when the server has no Google client id configured,
 * which is why the pages can include it unconditionally.
 *
 * @param providers  the answer from `useAuthProviders`, or null while it loads
 * @param onError    where to put a failure, so it joins the form's own alert
 */
export default function GoogleAuth({ providers, text, redirectTo = "/", onError }) {
  const { loginWithGoogle } = useAuth();
  const navigate = useNavigate();
  const [signingIn, setSigningIn] = useState(false);

  if (!providers?.googleEnabled) return null;

  async function handleCredential(credential) {
    setSigningIn(true);
    onError(null);
    try {
      await loginWithGoogle(credential);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setSigningIn(false);
      onError(err.message);
    }
  }

  return (
    <div className="auth-social-block">
      <div className="auth-divider">
        <span>hoặc</span>
      </div>

      <GoogleSignInButton
        clientId={providers.googleClientId}
        text={text}
        onSuccess={handleCredential}
        onError={onError}
      />

      {signingIn && <p className="auth-social-hint muted">Đang đăng nhập bằng Google…</p>}
    </div>
  );
}
